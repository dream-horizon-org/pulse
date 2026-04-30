package org.dreamhorizon.pulseserver.service.cron;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationBatchResponseDto;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationResultDto;
import org.dreamhorizon.pulseserver.resources.notification.models.RecipientsDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SendNotificationRequestDto;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService.NotificationStatusResponse;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotification;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotificationResult;

/**
 * Runs usage-limit notification batch on pulse-server (same behavior as legacy
 * pulse-alerts-cron {@code UsageLimitNotificationService}, but in-process).
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UsageLimitNotificationProcessService {

  /** Used when {@link ApplicationConfig#dashboardBaseUrl} is unset or blank after trim. */
  private static final String DEFAULT_DASHBOARD_URL = "https://app.pulse-ux.com";
  /** Cap combined error text (DB column allows ~8k; dao truncates at 8k). */
  private static final int MAX_ERROR_MESSAGE_TOTAL = 7500;
  /** Per failure line to avoid one huge message eating the budget. */
  private static final int MAX_MESSAGE_PER_FAILURE = 2000;
  /**
   * In-run attempts per project (initial try plus retries) before the batch fails. Successful sends
   * are marked in MySQL immediately; only failed projects are retried in subsequent rounds.
   */
  private static final int MAX_IN_RUN_ATTEMPTS = 3;
  /** Pause before retrying failed projects (same invocation) to avoid hammering e.g. SES. */
  private static final int RETRY_DELAY_MS = 300;

  private final UsageLimitService usageLimitService;
  private final NotificationService notificationService;
  private final ApplicationConfig applicationConfig;

  public Completable processUsageLimitNotifications() {
    long startTime = System.currentTimeMillis();
    return usageLimitService
        .getUsageNotifications()
        .flatMapCompletable(this::processResult)
        .doOnError(
            error ->
                log.error(
                    "Usage limit notification batch failed after {}ms",
                    System.currentTimeMillis() - startTime,
                    error));
  }

  private Completable processResult(UsageNotificationResult result) {
    if (result.getNotifications() == null || result.getNotifications().isEmpty()) {
      return Completable.complete();
    }
    int total = result.getNotifications().size();
    List<String> finalFailureLines = new ArrayList<>();
    return processRounds(
        new ArrayList<>(result.getNotifications()), 1, total, finalFailureLines);
  }

  /**
   * Runs up to {@link #MAX_IN_RUN_ATTEMPTS} rounds. Each round only includes projects that failed
   * the previous round; projects that succeed are marked and are not reprocessed.
   */
  private Completable processRounds(
      List<UsageNotification> pending,
      int attempt,
      int totalProjectCount,
      List<String> finalFailureLines) {
    if (pending.isEmpty()) {
      return Completable.complete();
    }
    List<UsageNotification> roundFailed = Collections.synchronizedList(new ArrayList<>());
    List<Completable> tasks = new ArrayList<>();
    for (UsageNotification notification : pending) {
      tasks.add(
          sendAndMark(notification)
              .onErrorResumeNext(
                  error -> {
                    roundFailed.add(notification);
                    if (attempt < MAX_IN_RUN_ATTEMPTS) {
                      log.warn(
                          "Usage limit notification attempt {}/{} failed for project {} (name={}):"
                              + " {} — retrying",
                          attempt,
                          MAX_IN_RUN_ATTEMPTS,
                          notification.getProjectId(),
                          notification.getProjectName(),
                          error.getMessage());
                    } else {
                      finalFailureLines.add(
                          failedProjectLabel(notification)
                              + ": "
                              + truncateForStorage(error, MAX_MESSAGE_PER_FAILURE));
                      log.error(
                          "Failed to process notification for project {} (name={}) - {}% after {} attempts: {}",
                          notification.getProjectId(),
                          notification.getProjectName(),
                          notification.getThreshold(),
                          MAX_IN_RUN_ATTEMPTS,
                          error.getMessage());
                    }
                    return Completable.complete();
                  }));
    }
    // Use concat: run one project at a time per round to avoid overloading e-mail and to keep
    // per-project retry semantics easy to follow (order is not guaranteed to matter here).
    return Completable.concat(tasks)
        .andThen(
            Completable.defer(
                () -> {
                  if (roundFailed.isEmpty()) {
                    return Completable.complete();
                  }
                  if (attempt < MAX_IN_RUN_ATTEMPTS) {
                    return Completable.timer(RETRY_DELAY_MS, TimeUnit.MILLISECONDS, Schedulers.io())
                        .andThen(
                            processRounds(
                                new ArrayList<>(roundFailed), attempt + 1, totalProjectCount, finalFailureLines));
                  }
                  String detail = String.join(" | ", finalFailureLines);
                  if (detail.length() > MAX_ERROR_MESSAGE_TOTAL) {
                    detail = detail.substring(0, MAX_ERROR_MESSAGE_TOTAL) + "…";
                  }
                  return Completable.error(
                      new IllegalStateException(
                          "Usage limit notifications: "
                              + roundFailed.size()
                              + " of "
                              + totalProjectCount
                              + " project(s) failed after "
                              + MAX_IN_RUN_ATTEMPTS
                              + " in-run attempt(s) each. "
                              + detail));
                }));
  }

  private Completable sendAndMark(UsageNotification notification) {
    List<Integer> thresholdsToMark = notification.getThresholdsToMark();
    if (thresholdsToMark == null || thresholdsToMark.isEmpty()) {
      thresholdsToMark = List.of(notification.getThreshold());
    }
    final List<Integer> thresholds = thresholdsToMark;
    return sendUsageLimitEmail(notification)
        .flatMapCompletable(this::requireSuccessfulNotificationBatch)
        .andThen(
            Completable.defer(
                () -> {
                  Single<NotificationStatusResponse> markSingle =
                      usageLimitService.markThresholdsNotified(
                          notification.getProjectId(), thresholds);
                  if (markSingle == null) {
                    return Completable.error(
                        new IllegalStateException("markThresholdsNotified returned null"));
                  }
                  return markSingle.ignoreElement();
                }));
  }

  private Single<NotificationBatchResponseDto> sendUsageLimitEmail(
      UsageNotification notification) {
    String eventsDisplay =
        notification.getEventsPercentageDisplay() != null
            ? notification.getEventsPercentageDisplay()
            : String.valueOf(
                notification.getEventsPercentage() != null ? notification.getEventsPercentage() : 0);
    String sessionsDisplay =
        notification.getSessionsPercentageDisplay() != null
            ? notification.getSessionsPercentageDisplay()
            : String.valueOf(
                notification.getSessionsPercentage() != null
                    ? notification.getSessionsPercentage()
                    : 0);
    Map<String, Object> params = new HashMap<>();
    params.put("projectId", notification.getProjectId());
    params.put(
        "projectName",
        notification.getProjectName() != null ? notification.getProjectName() : notification.getProjectId());
    params.put("threshold", notification.getThreshold());
    params.put("notifyFor", notification.getNotifyFor());
    params.put("eventsPercentageDisplay", eventsDisplay);
    params.put("sessionsPercentageDisplay", sessionsDisplay);
    params.put("dashboardUrl", resolveDashboardUrlForEmail());
    if (notification.getTenantId() != null && !notification.getTenantId().isBlank()) {
      params.put("tenantId", notification.getTenantId());
    }
    RecipientsDto recipients = null;
    if (notification.getRecipientEmails() != null && !notification.getRecipientEmails().isEmpty()) {
      List<String> emails =
          notification.getRecipientEmails().stream()
              .filter(e -> e != null && !e.isBlank() && e.contains("@"))
              .collect(Collectors.toList());
      if (!emails.isEmpty()) {
        recipients = RecipientsDto.builder().emails(emails).build();
      }
    }
    SendNotificationRequestDto request =
        SendNotificationRequestDto.builder()
            .eventName(notification.getTemplateName())
            .channelTypes(List.of(ChannelType.EMAIL))
            .params(params)
            .recipients(recipients)
            .build();
    return notificationService.sendNotification(notification.getProjectId(), request);
  }

  /**
   * Trims configured {@link ApplicationConfig#getDashboardBaseUrl()}, strips trailing slashes, and
   * falls back to {@link #DEFAULT_DASHBOARD_URL} when unset or blank.
   */
  private String resolveDashboardUrlForEmail() {
    String configured = applicationConfig.getDashboardBaseUrl();
    if (configured == null) {
      return DEFAULT_DASHBOARD_URL;
    }
    String trimmed = configured.trim();
    if (trimmed.isEmpty()) {
      return DEFAULT_DASHBOARD_URL;
    }
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    return trimmed;
  }

  /**
   * {@link NotificationServiceImpl} maps SES failures to {@link NotificationResultDto} rows and
   * still completes the {@code Single}; we validate counts/status here so the cron job does not
   * mark success when all rows failed.
   */
  private Completable requireSuccessfulNotificationBatch(NotificationBatchResponseDto batch) {
    if (batch.getFailed() > 0) {
      return Completable.error(
          new IllegalStateException(
              "Email notification: "
                  + batch.getFailed()
                  + "/"
                  + batch.getTotalRecipients()
                  + " failed. "
                  + firstFailureDetail(batch.getResults())));
    }
    if (batch.getTotalRecipients() == 0) {
      return Completable.error(
          new IllegalStateException("No notification delivery attempts (empty batch)"));
    }
    if (batch.getQueued() == 0) {
      return Completable.error(
          new IllegalStateException(
              "No notifications sent or queued (e.g. suppressed or skipped). recipients="
                  + batch.getTotalRecipients()));
    }
    return Completable.complete();
  }

  private static String firstFailureDetail(List<NotificationResultDto> results) {
    if (results == null || results.isEmpty()) {
      return "See pulse-server notification logs";
    }
    return results.stream()
        .filter(
            r ->
                r != null
                    && (NotificationStatus.FAILED.equals(r.getStatus())
                        || NotificationStatus.PERMANENT_FAILURE.equals(r.getStatus())))
        .map(r -> r.getErrorMessage() != null ? r.getErrorMessage() : String.valueOf(r.getStatus()))
        .findFirst()
        .orElse("See pulse-server notification logs");
  }

  /**
   * Human-readable id for error_message: {@code projectId}, or {@code projectId (display name)} when
   * name differs from id.
   */
  private static String failedProjectLabel(UsageNotification n) {
    String id = n.getProjectId() != null ? n.getProjectId() : "(unknown-project-id)";
    String name = n.getProjectName();
    if (name != null
        && !name.isBlank()
        && !name.equals(id)) {
      return id + " (" + name + ")";
    }
    return id;
  }

  private static String truncateForStorage(Throwable t, int maxLen) {
    if (t == null) {
      return "(no message)";
    }
    String s = t.getMessage();
    if (s == null || s.isBlank()) {
      s = t.getClass().getSimpleName();
    }
    s = s.replace('\n', ' ').replace('\r', ' ').trim();
    if (s.length() > maxLen) {
      s = s.substring(0, maxLen) + "…";
    }
    return s;
  }
}
