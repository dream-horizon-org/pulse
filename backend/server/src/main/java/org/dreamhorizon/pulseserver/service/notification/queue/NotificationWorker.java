package org.dreamhorizon.pulseserver.service.notification.queue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.dao.notification.NotificationLogDao;
import org.dreamhorizon.pulseserver.dao.notification.NotificationTemplateDao;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationResult;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.notification.models.QueuedNotification;
import org.dreamhorizon.pulseserver.service.notification.provider.NotificationProviderFactory;

@Slf4j
@Singleton
public class NotificationWorker {

  private final SqsNotificationQueue queue;
  private final NotificationProviderFactory providerFactory;
  private final NotificationTemplateDao templateDao;
  private final NotificationLogDao logDao;
  private final NotificationRetryPolicy retryPolicy;
  private final NotificationConfig.WorkerConfig workerConfig;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private Disposable workerDisposable;

  @Inject
  public NotificationWorker(
      SqsNotificationQueue queue,
      NotificationProviderFactory providerFactory,
      NotificationTemplateDao templateDao,
      NotificationLogDao logDao,
      NotificationRetryPolicy retryPolicy,
      NotificationConfig config) {
    this.queue = queue;
    this.providerFactory = providerFactory;
    this.templateDao = templateDao;
    this.logDao = logDao;
    this.retryPolicy = retryPolicy;
    this.workerConfig = config.getWorkerConfig();
  }

  public void start() {
    if (!queue.isEnabled()) {
      log.info("Notification queue not configured, worker disabled");
      return;
    }

    if (!workerConfig.isEnabled()) {
      log.info("Notification worker disabled by configuration");
      return;
    }

    if (running.compareAndSet(false, true)) {
      log.info(
          "Starting notification worker with batchSize={}, visibilityTimeout={}s, pollInterval={}s",
          workerConfig.getBatchSize(),
          workerConfig.getVisibilityTimeoutSeconds(),
          workerConfig.getPollIntervalSeconds());

      workerDisposable =
          Observable.interval(0, workerConfig.getPollIntervalSeconds(), TimeUnit.SECONDS)
              .subscribeOn(Schedulers.io())
              .flatMapCompletable(tick -> pollAndProcess())
              .retry()
              .subscribe(
                  () -> log.info("Notification worker completed"),
                  error -> log.error("Notification worker error", error));
    }
  }

  public void stop() {
    if (running.compareAndSet(true, false)) {
      log.info("Stopping notification worker");
      if (workerDisposable != null && !workerDisposable.isDisposed()) {
        workerDisposable.dispose();
      }
    }
  }

  public boolean isRunning() {
    return running.get();
  }

  private Completable pollAndProcess() {
    return queue
        .receiveMessages(workerConfig.getBatchSize(), workerConfig.getVisibilityTimeoutSeconds())
        .flatMapCompletable(
            messages -> {
              if (messages.isEmpty()) {
                return Completable.complete();
              }

              log.debug("Received {} messages from queue", messages.size());

              return Observable.fromIterable(messages)
                  .flatMapCompletable(this::processMessage)
                  .onErrorComplete();
            });
  }

  private Completable processMessage(QueuedNotification queuedNotification) {
    NotificationMessage message = queuedNotification.getMessage();

    return templateDao
        .getTemplateById(message.getTemplateId())
        .toSingle()
        .flatMap(
            template -> {
              var providerOpt = providerFactory.getProvider(message.getChannelType());
              if (providerOpt.isEmpty()) {
                return handleNoProvider(queuedNotification);
              }

              return providerOpt
                  .get()
                  .send(message, template)
                  .flatMap(result -> handleResult(queuedNotification, result));
            })
        .onErrorResumeNext(
            error -> {
              log.error(
                  "Error processing message for recipient {}: {}",
                  message.getRecipient(),
                  error.getMessage());
              return handleError(queuedNotification, error);
            })
        .ignoreElement();
  }

  private Single<Boolean> handleNoProvider(
      QueuedNotification queuedNotification) {
    NotificationMessage message = queuedNotification.getMessage();
    log.error("No provider for channel type: {}", message.getChannelType());

    return updateLogStatus(
            message.getLogId(),
            NotificationStatus.PERMANENT_FAILURE,
            queuedNotification.getReceiveCount(),
            "No provider available",
            null)
        .andThen(queue.deleteMessage(queuedNotification.getReceiptHandle()))
        .toSingleDefault(false);
  }

  private Single<Boolean> handleResult(
      QueuedNotification queuedNotification, NotificationResult result) {

    NotificationMessage message = queuedNotification.getMessage();

    if (result.isSuccess()) {
      log.debug("Successfully sent notification to {}", message.getRecipient());
      return updateLogStatus(
              message.getLogId(),
              NotificationStatus.SENT,
              queuedNotification.getReceiveCount(),
              null,
              result.getExternalId())
          .andThen(queue.deleteMessage(queuedNotification.getReceiptHandle()))
          .toSingleDefault(true);
    }

    boolean shouldRetry =
        retryPolicy.shouldRetry(queuedNotification.getReceiveCount(), result.isPermanentFailure());

    if (shouldRetry) {
      log.warn(
          "Notification failed, will retry. Recipient: {}, Error: {}",
          message.getRecipient(),
          result.getErrorMessage());

      int visibilityTimeout =
          retryPolicy.getVisibilityTimeoutSeconds(queuedNotification.getReceiveCount());

      return updateLogStatus(
              message.getLogId(),
              NotificationStatus.RETRYING,
              queuedNotification.getReceiveCount(),
              result.getErrorMessage(),
              null)
          .andThen(
              queue.changeMessageVisibility(
                  queuedNotification.getReceiptHandle(), visibilityTimeout))
          .toSingleDefault(false);
    } else {
      NotificationStatus finalStatus =
          result.isPermanentFailure()
              ? NotificationStatus.PERMANENT_FAILURE
              : NotificationStatus.FAILED;

      log.error(
          "Notification permanently failed. Recipient: {}, Error: {}",
          message.getRecipient(),
          result.getErrorMessage());

      return updateLogStatus(
              message.getLogId(),
              finalStatus,
              queuedNotification.getReceiveCount(),
              result.getErrorMessage(),
              null)
          .andThen(queue.deleteMessage(queuedNotification.getReceiptHandle()))
          .toSingleDefault(false);
    }
  }

  private Single<Boolean> handleError(
      QueuedNotification queuedNotification, Throwable error) {

    NotificationMessage message = queuedNotification.getMessage();
    boolean shouldRetry = retryPolicy.shouldRetry(queuedNotification.getReceiveCount(), false);

    if (shouldRetry) {
      int visibilityTimeout =
          retryPolicy.getVisibilityTimeoutSeconds(queuedNotification.getReceiveCount());

      return updateLogStatus(
              message.getLogId(),
              NotificationStatus.RETRYING,
              queuedNotification.getReceiveCount(),
              error.getMessage(),
              null)
          .andThen(
              queue.changeMessageVisibility(
                  queuedNotification.getReceiptHandle(), visibilityTimeout))
          .toSingleDefault(false);
    } else {
      return updateLogStatus(
              message.getLogId(),
              NotificationStatus.FAILED,
              queuedNotification.getReceiveCount(),
              error.getMessage(),
              null)
          .andThen(queue.deleteMessage(queuedNotification.getReceiptHandle()))
          .toSingleDefault(false);
    }
  }

  private Completable updateLogStatus(
      Long logId,
      NotificationStatus status,
      int attemptCount,
      String errorMessage,
      String externalId) {
    if (logId == null) {
      return Completable.complete();
    }

    return logDao
        .updateLogStatus(logId, status, attemptCount, errorMessage, null, externalId, null, null)
        .ignoreElement()
        .onErrorComplete();
  }
}
