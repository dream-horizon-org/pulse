package org.dreamhorizon.pulseserver.service.cron;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.dreamhorizon.pulseserver.resources.notification.models.NotificationBatchResponseDto;
import org.dreamhorizon.pulseserver.service.notification.NotificationService;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService.NotificationStatusResponse;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotification;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsageLimitNotificationProcessServiceTest {

  @Mock private UsageLimitService usageLimitService;
  @Mock private NotificationService notificationService;

  private UsageLimitNotificationProcessService service;

  @BeforeEach
  void setUp() {
    reset(usageLimitService, notificationService);
    service = new UsageLimitNotificationProcessService(usageLimitService, notificationService);
  }

  @Test
  void shouldRetryFailedProjectAndSucceedOnSecondInRunAttempt() {
    UsageNotification n = sampleNotification("p-retry");
    stubGetNotifications(List.of(n));
    AtomicInteger sendCalls = new AtomicInteger();
    doAnswer(
            invocation -> {
              // First in-run attempt fails; second succeeds (same project, next round)
              if (sendCalls.getAndIncrement() == 0) {
                return Single.error(new RuntimeException("SES timeout"));
              }
              return Single.just(okBatch());
            })
        .when(notificationService)
        .sendNotification(eq("p-retry"), any());
    when(usageLimitService.markThresholdsNotified(anyString(), anyList()))
        .thenReturn(
            Single.just(NotificationStatusResponse.builder().projectId("p-retry").build()));

    service.processUsageLimitNotifications().blockingAwait();

    verify(notificationService, times(2)).sendNotification(eq("p-retry"), any());
    verify(usageLimitService, times(1))
        .markThresholdsNotified(eq("p-retry"), eq(List.of(50)));
  }

  @Test
  void shouldFailAfterMaxInRunAttemptsWithoutMarking() {
    UsageNotification n = sampleNotification("p-fail");
    stubGetNotifications(List.of(n));
    doAnswer(invocation -> Single.error(new RuntimeException("permanent")))
        .when(notificationService)
        .sendNotification(eq("p-fail"), any());

    assertThatThrownBy(() -> service.processUsageLimitNotifications().blockingAwait())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Usage limit notifications: 1 of 1");

    verify(notificationService, times(3)).sendNotification(eq("p-fail"), any());
    verify(usageLimitService, never()).markThresholdsNotified(anyString(), anyList());
  }

  @Test
  void shouldOnlyRetryProjectsThatFailedPreviousRound() {
    UsageNotification nBad = sampleNotification("fail");
    UsageNotification nOk = sampleNotification("ok");
    stubGetNotifications(List.of(nBad, nOk));

    doAnswer(
            inv -> {
              String projectId = inv.getArgument(0);
              if ("fail".equals(projectId)) {
                return Single.error(new RuntimeException("no"));
              }
              return Single.just(okBatch());
            })
        .when(notificationService)
        .sendNotification(anyString(), any());
    when(usageLimitService.markThresholdsNotified(anyString(), anyList()))
        .thenAnswer(
            inv -> {
              String projectId = inv.getArgument(0);
              return Single.just(
                  NotificationStatusResponse.builder().projectId(projectId).build());
            });

    assertThatThrownBy(() -> service.processUsageLimitNotifications().blockingAwait())
        .isInstanceOf(IllegalStateException.class);

    verify(notificationService, times(3)).sendNotification(eq("fail"), any());
    verify(notificationService, times(1)).sendNotification(eq("ok"), any());
    verify(usageLimitService, times(1)).markThresholdsNotified(eq("ok"), eq(List.of(50)));
    verify(usageLimitService, never()).markThresholdsNotified(eq("fail"), anyList());
  }

  private void stubGetNotifications(List<UsageNotification> notifications) {
    when(usageLimitService.getUsageNotifications())
        .thenReturn(
            Single.just(
                UsageNotificationResult.builder()
                    .notifications(notifications)
                    .totalProjectsChecked(notifications.size())
                    .notificationsDue(notifications.size())
                    .build()));
  }

  private static UsageNotification sampleNotification(String projectId) {
    return UsageNotification.builder()
        .projectId(projectId)
        .projectName("N")
        .threshold(50)
        .thresholdsToMark(List.of(50))
        .notifyFor("events")
        .templateName("usage_limit_threshold")
        .recipientEmails(List.of("a@example.com"))
        .eventsPercentage(65)
        .sessionsPercentage(55)
        .eventsPercentageDisplay("65")
        .sessionsPercentageDisplay("55")
        .build();
  }

  private static NotificationBatchResponseDto okBatch() {
    return NotificationBatchResponseDto.builder().totalRecipients(1).queued(1).failed(0).build();
  }
}
