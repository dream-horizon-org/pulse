package org.dreamhorizon.pulseserver.service.notification.queue;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.dao.notification.NotificationLogDao;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.notification.models.QueuedNotification;

@Slf4j
@Singleton
public class DlqHandler {

  private final SqsNotificationQueue queue;
  private final NotificationLogDao logDao;

  @Inject
  public DlqHandler(SqsNotificationQueue queue, NotificationLogDao logDao) {
    this.queue = queue;
    this.logDao = logDao;
  }

  public Single<DlqProcessResult> processDlqMessages(int maxMessages) {
    return queue
        .receiveDlqMessages(maxMessages)
        .flatMap(
            messages -> {
              if (messages.isEmpty()) {
                return Single.just(new DlqProcessResult(0, 0, 0));
              }

              log.info("Processing {} messages from DLQ", messages.size());

              int[] processed = {0};
              int[] requeued = {0};
              int[] discarded = {0};

              return Observable.fromIterable(messages)
                  .flatMapSingle(
                      msg ->
                          processMessage(msg)
                              .doOnSuccess(
                                  action -> {
                                    processed[0]++;
                                    if (action == DlqAction.REQUEUE) requeued[0]++;
                                    else if (action == DlqAction.DISCARD) discarded[0]++;
                                  }))
                  .toList()
                  .map(results -> new DlqProcessResult(processed[0], requeued[0], discarded[0]));
            });
  }

  private Single<DlqAction> processMessage(QueuedNotification queuedNotification) {
    NotificationMessage message = queuedNotification.getMessage();

    return analyzeFailure(message)
        .flatMap(
            action -> {
              switch (action) {
                case REQUEUE:
                  return requeueMessage(queuedNotification).toSingleDefault(DlqAction.REQUEUE);
                case DISCARD:
                  return discardMessage(queuedNotification).toSingleDefault(DlqAction.DISCARD);
                default:
                  return Single.just(DlqAction.SKIP);
              }
            })
        .onErrorReturnItem(DlqAction.SKIP);
  }

  private Single<DlqAction> analyzeFailure(NotificationMessage message) {
    return Single.fromCallable(
        () -> {
          if (message.getLogId() == null) {
            log.warn("DLQ message has no log ID, discarding");
            return DlqAction.DISCARD;
          }
          return DlqAction.DISCARD;
        });
  }

  private Completable requeueMessage(QueuedNotification queuedNotification) {
    NotificationMessage message = queuedNotification.getMessage();

    return queue
        .enqueue(message)
        .flatMapCompletable(
            messageId -> {
              log.info("Requeued message {} for recipient {}", messageId, message.getRecipient());

              return updateLogStatus(
                      message.getLogId(), NotificationStatus.QUEUED, "Requeued from DLQ")
                  .andThen(queue.deleteDlqMessage(queuedNotification.getReceiptHandle()));
            });
  }

  private Completable discardMessage(QueuedNotification queuedNotification) {
    NotificationMessage message = queuedNotification.getMessage();

    log.info("Discarding DLQ message for recipient {}", message.getRecipient());

    return updateLogStatus(
            message.getLogId(),
            NotificationStatus.PERMANENT_FAILURE,
            "Discarded from DLQ after max retries")
        .andThen(queue.deleteDlqMessage(queuedNotification.getReceiptHandle()));
  }

  private Completable updateLogStatus(Long logId, NotificationStatus status, String message) {
    if (logId == null) {
      return Completable.complete();
    }

    return logDao
        .updateLogStatus(logId, status, 0, message, null, null, null, null)
        .ignoreElement()
        .onErrorComplete();
  }

  public enum DlqAction {
    REQUEUE,
    DISCARD,
    SKIP
  }

  public record DlqProcessResult(int processed, int requeued, int discarded) {}
}
