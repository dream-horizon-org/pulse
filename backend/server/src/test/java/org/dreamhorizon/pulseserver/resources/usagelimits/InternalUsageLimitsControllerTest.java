package org.dreamhorizon.pulseserver.resources.usagelimits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.dreamhorizon.pulseserver.constant.CronJobType;
import org.dreamhorizon.pulseserver.resources.internal.models.CronRedisSyncJobAcceptedRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.MarkNotificationsRestRequest;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.NotificationStatusRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectLimitHistoryRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitListRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ProjectUsageLimitRestResponse;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.ResetLimitsRestRequest;
import org.dreamhorizon.pulseserver.resources.usagelimits.models.SetCustomLimitsRestRequest;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.JwtService;
import org.dreamhorizon.pulseserver.service.cron.CronRedisMaterializationJobService;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService;
import org.dreamhorizon.pulseserver.service.usagelimit.UsageLimitService.NotificationStatusResponse;
import org.dreamhorizon.pulseserver.service.usagelimit.models.ProjectUsageLimitInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
class InternalUsageLimitsControllerTest {

  @Mock
  private UsageLimitService usageLimitService;
  @Mock
  private CronRedisMaterializationJobService cronRedisMaterializationJobService;
  @Mock
  private JwtService jwtService;

  private InternalUsageLimitsController controller;

  private static ProjectUsageLimitInfo buildLimitInfo(String projectId) {
    return ProjectUsageLimitInfo.builder()
        .projectUsageLimitId(1L)
        .projectId(projectId)
        .usageLimits(Collections.emptyMap())
        .isActive(true)
        .createdAt(Instant.now())
        .createdBy("user@example.com")
        .build();
  }

  @BeforeEach
  void setUp() {
    controller = new InternalUsageLimitsController(
        usageLimitService, cronRedisMaterializationJobService, jwtService);
  }

  // ==================== syncUsageCreditsToRedis ====================

  @Test
  void shouldReturnAcceptedPayloadWhenUsageCreditsEnqueueSucceeds(Vertx vertx, VertxTestContext testContext) {
    when(cronRedisMaterializationJobService.acceptUsageCreditsSyncToRedis()).thenReturn(
        Single.just(CronRedisSyncJobAcceptedRestResponse.builder()
            .jobId(7L)
            .deduplicated(true)
            .jobType(CronJobType.USAGE_CREDITS_TO_REDIS)
            .build()));

    vertx.runOnContext(v -> controller
        .syncUsageCreditsToRedis()
        .whenComplete(
            (response, err) -> {
              testContext.verify(() -> {
                assertThat(err).isNull();
                assertThat(response.getHttpStatusCode()).isEqualTo(202);
                assertThat(response.getData().getJobId()).isEqualTo(7L);
                assertThat(response.getData().isDeduplicated()).isTrue();
                assertThat(response.getData().getJobType()).isEqualTo(CronJobType.USAGE_CREDITS_TO_REDIS);
              });
              testContext.completeNow();
            }));
  }

  @Test
  void shouldReturnAcceptedPayloadWhenUsageLimitNotificationsEnqueueSucceeds(
      Vertx vertx, VertxTestContext testContext) {
    when(cronRedisMaterializationJobService.acceptUsageLimitNotifications()).thenReturn(
        Single.just(CronRedisSyncJobAcceptedRestResponse.builder()
            .jobId(8L)
            .deduplicated(false)
            .jobType(CronJobType.USAGE_LIMIT_NOTIFICATIONS)
            .build()));

    vertx.runOnContext(v -> controller
        .processUsageLimitNotifications()
        .whenComplete(
            (response, err) -> {
              testContext.verify(() -> {
                assertThat(err).isNull();
                assertThat(response.getHttpStatusCode()).isEqualTo(202);
                assertThat(response.getData().getJobId()).isEqualTo(8L);
                assertThat(response.getData().isDeduplicated()).isFalse();
                assertThat(response.getData().getJobType()).isEqualTo(CronJobType.USAGE_LIMIT_NOTIFICATIONS);
              });
              testContext.completeNow();
            }));
  }

  // ==================== getProjectLimits ====================

  @Nested
  class GetProjectLimits {

    @Test
    void shouldReturnLimitsWhenFound() throws Exception {
      ProjectUsageLimitInfo info = buildLimitInfo("proj-1");
      when(usageLimitService.getProjectLimits("proj-1")).thenReturn(Maybe.just(info));

      Response<ProjectUsageLimitRestResponse> response =
          controller.getProjectLimits("proj-1").toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getProjectId()).isEqualTo("proj-1");
      assertThat(response.getData().getProjectUsageLimitId()).isEqualTo(1L);
    }

    @Test
    void shouldPropagateErrorWhenLimitsNotFound() {
      when(usageLimitService.getProjectLimits("proj-missing")).thenReturn(Maybe.empty());

      try {
        controller.getProjectLimits("proj-missing").toCompletableFuture().get();
      } catch (ExecutionException ex) {
        assertThat(ex.getCause()).hasMessageContaining("proj-missing");
      } catch (Exception ex) {
        throw new AssertionError("unexpected exception type", ex);
      }
    }

    @Test
    void shouldPropagateServiceError() {
      when(usageLimitService.getProjectLimits("proj-err"))
          .thenReturn(Maybe.error(new RuntimeException("DB failure")));

      try {
        controller.getProjectLimits("proj-err").toCompletableFuture().get();
      } catch (ExecutionException ex) {
        assertThat(ex.getCause()).hasMessageContaining("DB failure");
      } catch (Exception ex) {
        throw new AssertionError("unexpected exception type", ex);
      }
    }
  }

  // ==================== setCustomLimits ====================

  @Nested
  class SetCustomLimits {

    @Test
    void shouldReturnUpdatedLimitsOnSuccess() throws Exception {
      ProjectUsageLimitInfo info = buildLimitInfo("proj-1");
      when(usageLimitService.setCustomLimits(any())).thenReturn(Single.just(info));

      SetCustomLimitsRestRequest request =
          SetCustomLimitsRestRequest.builder().limits(Collections.emptyMap()).build();

      Response<ProjectUsageLimitRestResponse> response =
          controller.setCustomLimits("Bearer token.valid.here", "proj-1", request)
              .toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getProjectId()).isEqualTo("proj-1");
    }

    @Test
    void shouldUseSystemPerformedByWhenAuthorizationIsNull() throws Exception {
      ProjectUsageLimitInfo info = buildLimitInfo("proj-1");
      when(usageLimitService.setCustomLimits(any())).thenReturn(Single.just(info));

      SetCustomLimitsRestRequest request =
          SetCustomLimitsRestRequest.builder().limits(Collections.emptyMap()).build();

      Response<ProjectUsageLimitRestResponse> response =
          controller.setCustomLimits(null, "proj-1", request)
              .toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
    }

    @Test
    void shouldPropagateServiceError() {
      when(usageLimitService.setCustomLimits(any()))
          .thenReturn(Single.error(new RuntimeException("custom limits not allowed")));

      SetCustomLimitsRestRequest request =
          SetCustomLimitsRestRequest.builder().limits(Collections.emptyMap()).build();

      try {
        controller.setCustomLimits(null, "proj-1", request).toCompletableFuture().get();
      } catch (ExecutionException ex) {
        assertThat(ex.getCause()).hasMessageContaining("custom limits not allowed");
      } catch (Exception ex) {
        throw new AssertionError("unexpected exception type", ex);
      }
    }
  }

  // ==================== resetToDefaults ====================

  @Nested
  class ResetToDefaults {

    @Test
    void shouldReturnResetLimitsOnSuccess() throws Exception {
      ProjectUsageLimitInfo info = buildLimitInfo("proj-1");
      when(usageLimitService.resetToDefaults(any())).thenReturn(Single.just(info));

      Response<ProjectUsageLimitRestResponse> response =
          controller.resetToDefaults(null, "proj-1", new ResetLimitsRestRequest())
              .toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getProjectId()).isEqualTo("proj-1");
    }

    @Test
    void shouldHandleNullRequestByDefaultingToFreeTier() throws Exception {
      ProjectUsageLimitInfo info = buildLimitInfo("proj-1");
      when(usageLimitService.resetToDefaults(any())).thenReturn(Single.just(info));

      Response<ProjectUsageLimitRestResponse> response =
          controller.resetToDefaults(null, "proj-1", null)
              .toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
    }

    @Test
    void shouldPropagateServiceError() {
      when(usageLimitService.resetToDefaults(any()))
          .thenReturn(Single.error(new RuntimeException("tier not found")));

      try {
        controller.resetToDefaults(null, "proj-1", new ResetLimitsRestRequest())
            .toCompletableFuture().get();
      } catch (ExecutionException ex) {
        assertThat(ex.getCause()).hasMessageContaining("tier not found");
      } catch (Exception ex) {
        throw new AssertionError("unexpected exception type", ex);
      }
    }
  }

  // ==================== getProjectLimitHistory ====================

  @Nested
  class GetProjectLimitHistory {

    @Test
    void shouldReturnHistoryOnSuccess() throws Exception {
      ProjectUsageLimitInfo info = buildLimitInfo("proj-1");
      when(usageLimitService.getProjectLimitHistory("proj-1"))
          .thenReturn(Flowable.just(info));

      Response<ProjectLimitHistoryRestResponse> response =
          controller.getProjectLimitHistory("proj-1").toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getProjectId()).isEqualTo("proj-1");
      assertThat(response.getData().getHistory()).hasSize(1);
      assertThat(response.getData().getTotalCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnEmptyHistoryWhenNoRecords() throws Exception {
      when(usageLimitService.getProjectLimitHistory("proj-new"))
          .thenReturn(Flowable.empty());

      Response<ProjectLimitHistoryRestResponse> response =
          controller.getProjectLimitHistory("proj-new").toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getHistory()).isEmpty();
      assertThat(response.getData().getTotalCount()).isEqualTo(0);
    }
  }

  // ==================== getAllActiveLimits ====================

  @Nested
  class GetAllActiveLimits {

    @Test
    void shouldReturnAllActiveLimitsWhenActiveOnlyTrue() throws Exception {
      ProjectUsageLimitInfo info = buildLimitInfo("proj-1");
      when(usageLimitService.getAllActiveLimits()).thenReturn(Flowable.just(info));

      Response<ProjectUsageLimitListRestResponse> response =
          controller.getAllActiveLimits(true).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getLimits()).hasSize(1);
      assertThat(response.getData().getTotalCount()).isEqualTo(1);
    }

    @Test
    void shouldReturnAllLimitsWhenActiveOnlyFalse() throws Exception {
      ProjectUsageLimitInfo active = buildLimitInfo("proj-1");
      ProjectUsageLimitInfo inactive = ProjectUsageLimitInfo.builder()
          .projectUsageLimitId(2L)
          .projectId("proj-2")
          .usageLimits(Collections.emptyMap())
          .isActive(false)
          .createdAt(Instant.now())
          .build();
      when(usageLimitService.getAllLimits())
          .thenReturn(Flowable.fromIterable(List.of(active, inactive)));

      Response<ProjectUsageLimitListRestResponse> response =
          controller.getAllActiveLimits(false).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getLimits()).hasSize(2);
    }

    @Test
    void shouldReturnEmptyListWhenNoActiveLimits() throws Exception {
      when(usageLimitService.getAllActiveLimits()).thenReturn(Flowable.empty());

      Response<ProjectUsageLimitListRestResponse> response =
          controller.getAllActiveLimits(true).toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getLimits()).isEmpty();
    }
  }

  // ==================== markThresholdsNotified ====================

  @Nested
  class MarkThresholdsNotified {

    @Test
    void shouldReturnNotificationStatusOnSuccess() throws Exception {
      NotificationStatusResponse serviceResponse = NotificationStatusResponse.builder()
          .projectId("proj-1")
          .month("2026-05")
          .projectUsageLimitId(1L)
          .notificationActive(true)
          .createdAt(Instant.now())
          .build();
      when(usageLimitService.markThresholdsNotified(anyString(), anyList()))
          .thenReturn(Single.just(serviceResponse));

      MarkNotificationsRestRequest request =
          MarkNotificationsRestRequest.builder().thresholds(List.of(50, 75)).build();

      Response<NotificationStatusRestResponse> response =
          controller.markThresholdsNotified("proj-1", request)
              .toCompletableFuture().get();

      assertThat(response.getHttpStatusCode()).isEqualTo(200);
      assertThat(response.getData().getProjectId()).isEqualTo("proj-1");
      assertThat(response.getData().getMonth()).isEqualTo("2026-05");
      assertThat(response.getData().getProjectUsageLimitId()).isEqualTo(1L);
    }

    @Test
    void shouldPropagateServiceError() {
      when(usageLimitService.markThresholdsNotified(anyString(), anyList()))
          .thenReturn(Single.error(new IllegalStateException("no active limit")));

      MarkNotificationsRestRequest request =
          MarkNotificationsRestRequest.builder().thresholds(List.of(50)).build();

      try {
        controller.markThresholdsNotified("proj-err", request).toCompletableFuture().get();
      } catch (ExecutionException ex) {
        assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no active limit");
      } catch (Exception ex) {
        throw new AssertionError("unexpected exception type", ex);
      }
    }
  }
}
