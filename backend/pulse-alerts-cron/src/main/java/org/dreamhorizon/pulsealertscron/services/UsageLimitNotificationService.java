package org.dreamhorizon.pulsealertscron.services;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulsealertscron.client.PulseServerApiClient;
import org.dreamhorizon.pulsealertscron.dto.response.UsageNotificationDto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for processing usage limit notifications.
 * Orchestrates fetching notifications due and sending them.
 */
@Slf4j
@RequiredArgsConstructor
public class UsageLimitNotificationService {
  private final PulseServerApiClient apiClient;

  /**
   * Main method to check for notifications and send them.
   * 1. Calls API to get list of notifications due
   * 2. Sends each notification
   * 3. Marks each as notified
   */
  public Completable processNotifications() {
    log.info("=== Starting usage limit notification processing ===");
    long startTime = System.currentTimeMillis();

    return apiClient.getUsageNotifications()
        .flatMapCompletable(response -> {
          if (response.getNotifications() == null || response.getNotifications().isEmpty()) {
            log.info("✅ No usage notifications due (checked {} projects)",
                response.getTotalProjectsChecked());
            return Completable.complete();
          }

          log.info("📧 Processing {} usage notifications across {} projects",
              response.getNotificationsDue(), response.getTotalProjectsChecked());

          AtomicInteger successCount = new AtomicInteger(0);
          AtomicInteger failureCount = new AtomicInteger(0);

          List<Completable> tasks = new ArrayList<>();
          for (UsageNotificationDto notification : response.getNotifications()) {
            tasks.add(
                sendAndMarkNotification(notification)
                    .doOnComplete(() -> successCount.incrementAndGet())
                    .onErrorResumeNext(error -> {
                      failureCount.incrementAndGet();
                      log.error("❌ Failed to process notification for project {} - {}%: {}",
                          notification.getProjectId(),
                          notification.getThreshold(),
                          error.getMessage());
                      return Completable.complete();
                    })
            );
          }

          return Completable.merge(tasks)
              .doOnComplete(() -> {
                long duration = System.currentTimeMillis() - startTime;
                log.info("✅ Notification processing completed in {}ms: {} sent, {} failed",
                    duration, successCount.get(), failureCount.get());
              });
        })
        .doOnError(error -> {
          long duration = System.currentTimeMillis() - startTime;
          log.error("❌ Notification processing failed after {}ms", duration, error);
        });
  }

  /**
   * Send a single notification and mark it as notified.
   */
  private Completable sendAndMarkNotification(UsageNotificationDto notification) {
    log.info("📧 Processing notification: {} - {} at {}%",
        notification.getProjectId(),
        notification.getMetricType(),
        notification.getThreshold());

    return apiClient.sendUsageLimitNotification(notification)
        .andThen(
            apiClient.markThresholdsNotified(
                notification.getProjectId(),
                List.of(notification.getThreshold())
            )
        )
        .doOnComplete(() ->
            log.info("✅ Notification sent and marked: {} - {} at {}%",
                notification.getProjectId(),
                notification.getMetricType(),
                notification.getThreshold())
        )
        .doOnError(error ->
            log.error("❌ Failed to process notification: {} - {} at {}%",
                notification.getProjectId(),
                notification.getMetricType(),
                notification.getThreshold(),
                error)
        );
  }
}
