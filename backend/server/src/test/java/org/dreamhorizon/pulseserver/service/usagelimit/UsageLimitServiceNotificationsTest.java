package org.dreamhorizon.pulseserver.service.usagelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitDao.NotificationRecord;
import org.dreamhorizon.pulseserver.dao.usagelimit.models.ProjectUsageLimit;
import org.dreamhorizon.pulseserver.model.User;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService.NotificationStatusResponse;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageLimitValue;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotification;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageNotificationResult;
import org.dreamhorizon.pulseserver.service.usagelimit.models.UsageStats;
import org.junit.jupiter.api.Test;

/**
 * {@link UsageLimitService} — mark thresholds notified and analyze usage notifications.
 */
class UsageLimitServiceNotificationsTest extends UsageLimitServiceTestBase {

  @Test
  void shouldMapDaoRecordToResponseWithMonth() {
    Instant created = Instant.parse("2025-06-15T12:00:00Z");
    JsonNode node = objectMapper.createObjectNode().put("50", created.toString());
    NotificationRecord record = NotificationRecord.builder()
        .id(1L)
        .projectId("proj-a")
        .thresholdsNotified(node)
        .createdAt(created)
        .updatedAt(created)
        .build();

    ProjectUsageLimit active = ProjectUsageLimit.builder()
        .projectUsageLimitId(99L)
        .projectId("proj-a")
        .isActive(true)
        .build();
    when(usageLimitDao.getActiveLimitByProjectId("proj-a")).thenReturn(Maybe.just(active));
    when(usageLimitDao.markThresholdsNotified(eq("proj-a"), eq(List.of(50)), eq(99L)))
        .thenReturn(Single.just(record));

    NotificationStatusResponse response =
        usageLimitService.markThresholdsNotified("proj-a", List.of(50)).blockingGet();

    assertEquals("proj-a", response.getProjectId());
    assertEquals("2025-06", response.getMonth());
    assertEquals(node, response.getThresholdsNotified());
    assertEquals(created, response.getCreatedAt());
    verify(usageLimitDao).markThresholdsNotified("proj-a", List.of(50), 99L);
  }

  @Test
  void shouldMapNullCreatedAtToNullMonth() throws Exception {
    JsonNode node = objectMapper.readTree("{\"75\":\"2025-01-01T00:00:00Z\"}");
    NotificationRecord record = NotificationRecord.builder()
        .projectId("proj-b")
        .thresholdsNotified(node)
        .createdAt(null)
        .updatedAt(Instant.now())
        .build();

    ProjectUsageLimit active = ProjectUsageLimit.builder()
        .projectUsageLimitId(7L)
        .projectId("proj-b")
        .isActive(true)
        .build();
    when(usageLimitDao.getActiveLimitByProjectId("proj-b")).thenReturn(Maybe.just(active));
    when(usageLimitDao.markThresholdsNotified(eq("proj-b"), eq(List.of(75)), eq(7L)))
        .thenReturn(Single.just(record));

    NotificationStatusResponse response =
        usageLimitService.markThresholdsNotified("proj-b", List.of(75)).blockingGet();

    assertNull(response.getMonth());
  }

  @Test
  void shouldPropagateErrorWhenMarkDaoFails() {
    ProjectUsageLimit active = ProjectUsageLimit.builder()
        .projectUsageLimitId(1L)
        .projectId("p")
        .isActive(true)
        .build();
    when(usageLimitDao.getActiveLimitByProjectId("p")).thenReturn(Maybe.just(active));
    when(usageLimitDao.markThresholdsNotified(anyString(), anyList(), anyLong()))
        .thenReturn(Single.error(new RuntimeException("db down")));

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> usageLimitService.markThresholdsNotified("p", List.of(50)).blockingGet());
    assertEquals("db down", ex.getMessage());
  }

  @Test
  void shouldReturnNotificationsWithAdminEmailsAndTenantId() throws Exception {
    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put("max_events_per_project", UsageLimitValue.builder()
        .displayName("Max Events")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());
    limits.put("max_user_sessions_per_project", UsageLimitValue.builder()
        .displayName("Max Sessions")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());

    ProjectUsageLimit limit = ProjectUsageLimit.builder()
        .projectUsageLimitId(1L)
        .projectId("proj-notify")
        .projectName("Test Project")
        .tenantId("tenant-1")
        .usageLimits(objectMapper.writeValueAsString(limits))
        .isActive(true)
        .createdBy("creator@test.com")
        .createdAt(Instant.now())
        .build();

    when(usageLimitDao.getAllActiveLimits())
        .thenReturn(Flowable.just(limit));
    when(clickhouseQueryService.getCurrentMonthUsage())
        .thenReturn(Single.just(Map.of("proj-notify",
            UsageStats.builder().projectId("proj-notify").eventsUsed(60L).sessionsUsed(60L).build())));
    when(openFgaService.getProjectAdmins("proj-notify"))
        .thenReturn(Single.just(Set.of("user-admin-1")));
    when(userService.getUsersByIds(any()))
        .thenReturn(Single.just(List.of(User.builder().userId("user-admin-1").email("admin@test.com").build())));

    UsageNotificationResult result = usageLimitService.getUsageNotifications().blockingGet();

    assertNotNull(result);
    assertNotNull(result.getNotifications());
    assertEquals(1, result.getNotifications().size());
    UsageNotification notification = result.getNotifications().get(0);
    assertEquals("proj-notify", notification.getProjectId());
    assertEquals("tenant-1", notification.getTenantId());
    assertEquals(50, notification.getThreshold());
    assertNotNull(notification.getRecipientEmails());
    assertTrue(notification.getRecipientEmails().contains("admin@test.com"));
  }

  @Test
  void shouldFallbackToCreatedByWhenNoAdmins() throws Exception {
    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put("max_events_per_project", UsageLimitValue.builder()
        .displayName("Max Events")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());
    limits.put("max_user_sessions_per_project", UsageLimitValue.builder()
        .displayName("Max Sessions")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());

    ProjectUsageLimit limit = ProjectUsageLimit.builder()
        .projectUsageLimitId(1L)
        .projectId("proj-fallback")
        .projectName("Fallback Project")
        .tenantId("tenant-2")
        .usageLimits(objectMapper.writeValueAsString(limits))
        .isActive(true)
        .createdBy("creator@fallback.com")
        .createdAt(Instant.now())
        .build();

    when(usageLimitDao.getAllActiveLimits())
        .thenReturn(Flowable.just(limit));
    when(clickhouseQueryService.getCurrentMonthUsage())
        .thenReturn(Single.just(Map.of("proj-fallback",
            UsageStats.builder().projectId("proj-fallback").eventsUsed(50L).sessionsUsed(50L).build())));
    when(openFgaService.getProjectAdmins("proj-fallback"))
        .thenReturn(Single.just(new HashSet<>()));

    UsageNotificationResult result = usageLimitService.getUsageNotifications().blockingGet();

    assertNotNull(result);
    assertEquals(1, result.getNotifications().size());
    UsageNotification notification = result.getNotifications().get(0);
    assertEquals("proj-fallback", notification.getProjectId());
    assertEquals("tenant-2", notification.getTenantId());
    assertNotNull(notification.getRecipientEmails());
    assertTrue(notification.getRecipientEmails().contains("creator@fallback.com"));
    verify(userService, never()).getUsersByIds(any());
  }

  @Test
  void shouldReturnEmptyWhenNoActiveLimits() {
    when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.empty());
    when(clickhouseQueryService.getCurrentMonthUsage())
        .thenReturn(Single.just(Collections.emptyMap()));

    UsageNotificationResult result = usageLimitService.getUsageNotifications().blockingGet();

    assertNotNull(result);
    assertTrue(result.getNotifications().isEmpty());
    assertEquals(0, result.getTotalProjectsChecked());
    assertEquals(0, result.getNotificationsDue());
  }

  @Test
  void shouldReturnNoNotificationsWhenUsageBelowThresholds() throws Exception {
    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put("max_events_per_project", UsageLimitValue.builder()
        .displayName("Max Events")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());
    limits.put("max_user_sessions_per_project", UsageLimitValue.builder()
        .displayName("Max Sessions")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());

    ProjectUsageLimit limit = ProjectUsageLimit.builder()
        .projectUsageLimitId(1L)
        .projectId("proj-low")
        .usageLimits(objectMapper.writeValueAsString(limits))
        .isActive(true)
        .build();

    when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.just(limit));
    when(clickhouseQueryService.getCurrentMonthUsage())
        .thenReturn(Single.just(Map.of("proj-low",
            UsageStats.builder().projectId("proj-low").eventsUsed(10L).sessionsUsed(10L).build())));

    UsageNotificationResult result = usageLimitService.getUsageNotifications().blockingGet();

    assertTrue(result.getNotifications().isEmpty());
    assertEquals(1, result.getTotalProjectsChecked());
    assertEquals(0, result.getNotificationsDue());
  }

  @Test
  void shouldSkipProjectWhenUsageMissingInClickHouse() throws Exception {
    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put("max_events_per_project", UsageLimitValue.builder()
        .displayName("Max Events")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());
    limits.put("max_user_sessions_per_project", UsageLimitValue.builder()
        .displayName("Max Sessions")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());

    ProjectUsageLimit limit = ProjectUsageLimit.builder()
        .projectUsageLimitId(1L)
        .projectId("proj-no-usage")
        .usageLimits(objectMapper.writeValueAsString(limits))
        .isActive(true)
        .build();

    when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.just(limit));
    when(clickhouseQueryService.getCurrentMonthUsage())
        .thenReturn(Single.just(Collections.emptyMap()));

    UsageNotificationResult result = usageLimitService.getUsageNotifications().blockingGet();

    assertTrue(result.getNotifications().isEmpty());
    assertEquals(1, result.getTotalProjectsChecked());
  }

  @Test
  void shouldReturnNoNotificationWhenThresholdAlreadyMarked() throws Exception {
    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put("max_events_per_project", UsageLimitValue.builder()
        .displayName("Max Events")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());
    limits.put("max_user_sessions_per_project", UsageLimitValue.builder()
        .displayName("Max Sessions")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());

    JsonNode notified = objectMapper.readTree("{\"50\":\"2025-01-01T00:00:00Z\"}");
    ProjectUsageLimit limit = ProjectUsageLimit.builder()
        .projectUsageLimitId(1L)
        .projectId("proj-notified")
        .usageLimits(objectMapper.writeValueAsString(limits))
        .thresholdsNotified(notified)
        .isActive(true)
        .build();

    when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.just(limit));
    when(clickhouseQueryService.getCurrentMonthUsage())
        .thenReturn(Single.just(Map.of("proj-notified",
            UsageStats.builder().projectId("proj-notified").eventsUsed(55L).sessionsUsed(55L).build())));

    UsageNotificationResult result = usageLimitService.getUsageNotifications().blockingGet();

    assertTrue(result.getNotifications().isEmpty());
  }

  @Test
  void shouldChooseSessionsWhenSessionsMetricHigher() throws Exception {
    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put("max_events_per_project", UsageLimitValue.builder()
        .displayName("Max Events")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());
    limits.put("max_user_sessions_per_project", UsageLimitValue.builder()
        .displayName("Max Sessions")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());

    ProjectUsageLimit limit = ProjectUsageLimit.builder()
        .projectUsageLimitId(1L)
        .projectId("proj-sessions-win")
        .tenantId("tenant-s")
        .usageLimits(objectMapper.writeValueAsString(limits))
        .isActive(true)
        .createdBy("creator@sessions.com")
        .build();

    when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.just(limit));
    when(clickhouseQueryService.getCurrentMonthUsage())
        .thenReturn(Single.just(Map.of("proj-sessions-win",
            UsageStats.builder().projectId("proj-sessions-win").eventsUsed(40L).sessionsUsed(80L).build())));
    when(openFgaService.getProjectAdmins("proj-sessions-win"))
        .thenReturn(Single.just(new HashSet<>()));

    UsageNotificationResult result = usageLimitService.getUsageNotifications().blockingGet();

    assertEquals(1, result.getNotifications().size());
    UsageNotification n = result.getNotifications().get(0);
    assertEquals("sessions", n.getNotifyFor());
    assertEquals("proj-sessions-win", n.getProjectId());
  }

  @Test
  void shouldChooseEventsWhenEventsMetricHigher() throws Exception {
    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put("max_events_per_project", UsageLimitValue.builder()
        .displayName("Max Events")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());
    limits.put("max_user_sessions_per_project", UsageLimitValue.builder()
        .displayName("Max Sessions")
        .windowType("MONTHLY")
        .dataType("NUMBER")
        .value(100L)
        .overage(10)
        .build());

    ProjectUsageLimit limit = ProjectUsageLimit.builder()
        .projectUsageLimitId(1L)
        .projectId("proj-events-win")
        .tenantId("tenant-e")
        .usageLimits(objectMapper.writeValueAsString(limits))
        .isActive(true)
        .createdBy("creator@events.com")
        .build();

    when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.just(limit));
    when(clickhouseQueryService.getCurrentMonthUsage())
        .thenReturn(Single.just(Map.of("proj-events-win",
            UsageStats.builder().projectId("proj-events-win").eventsUsed(85L).sessionsUsed(50L).build())));
    when(openFgaService.getProjectAdmins("proj-events-win"))
        .thenReturn(Single.just(new HashSet<>()));

    UsageNotificationResult result = usageLimitService.getUsageNotifications().blockingGet();

    assertEquals(1, result.getNotifications().size());
    UsageNotification n = result.getNotifications().get(0);
    assertEquals("events", n.getNotifyFor());
    assertEquals("proj-events-win", n.getProjectId());
  }

  @Test
  void shouldPropagateErrorWhenClickHouseFails() throws Exception {
    Map<String, UsageLimitValue> limits = new HashMap<>();
    limits.put("max_events_per_project", UsageLimitValue.builder()
        .value(100L)
        .build());
    limits.put("max_user_sessions_per_project", UsageLimitValue.builder()
        .value(100L)
        .build());

    ProjectUsageLimit limit = ProjectUsageLimit.builder()
        .projectId("proj-ch-err")
        .usageLimits(objectMapper.writeValueAsString(limits))
        .isActive(true)
        .build();

    when(usageLimitDao.getAllActiveLimits()).thenReturn(Flowable.just(limit));
    when(clickhouseQueryService.getCurrentMonthUsage())
        .thenReturn(Single.error(new RuntimeException("clickhouse unavailable")));

    RuntimeException ex = assertThrows(RuntimeException.class,
        () -> usageLimitService.getUsageNotifications().blockingGet());
    assertEquals("clickhouse unavailable", ex.getMessage());
  }
}
