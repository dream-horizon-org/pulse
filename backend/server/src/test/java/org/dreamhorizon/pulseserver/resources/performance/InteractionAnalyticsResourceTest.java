package org.dreamhorizon.pulseserver.resources.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownRes;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthRes;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthRow;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsRes;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionOrderBy;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionRow;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsRes;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.interaction.PerformanceMetricService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TDD RED tests for Phase 1: 4 dedicated interaction analytics endpoints.
 *
 * <p>All tests fail until InteractionAnalyticsResource and its DTOs are implemented.
 *
 * <p>TRACEABILITY MATRIX (plan requirement → test method):
 *
 * <p>Phase 1 — Backend: 4 Dedicated Endpoints with Rich Responses
 *
 * <p>1. POST /v1/interactions/health — delegates to PerformanceMetricService
 *    → HealthEndpoint#shouldDelegateHealthRequestAndInjectProjectId
 *    → HealthEndpoint#shouldInjectProjectIdForHealthEndpoint
 *
 * <p>2. Health: default orderBy spanfreq DESC when caller omits it
 *    → HealthEndpoint#shouldApplyDefaultOrderByWhenOmitted
 *
 * <p>3. Health: caller-supplied orderBy is respected (e.g. sort by apdex)
 *    → HealthEndpoint#shouldRespectCallerSuppliedOrderByByApdex
 *
 * <p>4. Health: topN defaults to 10, explicit value passed through
 *    → HealthEndpoint#shouldUseDefaultTopNOfTenWhenNotProvided
 *    → HealthEndpoint#shouldPassExplicitTopNToService
 *
 * <p>5. Health: interactionNames filter (single / multiple / null)
 *    → HealthEndpoint#shouldPassSingleInteractionNameToService
 *    → HealthEndpoint#shouldPassMultipleInteractionNamesToService
 *    → HealthEndpoint#shouldOmitInteractionNamesWhenNull
 *
 * <p>6. Health: user dimension filters forwarded to service
 *    → HealthEndpoint#shouldPassUserFiltersToService
 *
 * <p>7. Health: typed response DTO returned from service propagated
 *    → HealthEndpoint#shouldReturnTypedInteractionsList
 *
 * <p>8. POST /v1/interactions/metrics — delegates to service
 *    → MetricsEndpoint#shouldDelegateMetricsRequest
 *    → MetricsEndpoint#shouldInjectProjectIdForMetricsEndpoint
 *
 * <p>9. Metrics: NO orderBy accepted (timeseries always ASC, aggregate irrelevant)
 *    → MetricsEndpoint#shouldRejectCallerProvidedOrderByForMetrics
 *
 * <p>10. Metrics: timeseries flag forwarded true / false
 *     → MetricsEndpoint#shouldPassTimeseriesTrueFlagToService
 *     → MetricsEndpoint#shouldDefaultTimeseriesFalseWhenOmitted
 *
 * <p>11. Metrics: all 7 metricType values forwarded to service
 *     → MetricsEndpoint#shouldPassCompositeMetricType
 *     → MetricsEndpoint#shouldPassApdexMetricType
 *     → MetricsEndpoint#shouldPassLatencyMetricType
 *     → MetricsEndpoint#shouldPassErrorRateMetricType
 *     → MetricsEndpoint#shouldPassUserCategoriesMetricType
 *     → MetricsEndpoint#shouldPassFramesMetricType
 *     → MetricsEndpoint#shouldPassNetworkMetricType
 *
 * <p>12. Metrics: interactionName forwarded to service
 *     → MetricsEndpoint#shouldPassInteractionNameToService
 *
 * <p>13. POST /v1/interactions/breakdown — delegates to service
 *     → BreakdownEndpoint#shouldDelegateBreakdownRequest
 *     → BreakdownEndpoint#shouldInjectProjectIdForBreakdownEndpoint
 *
 * <p>14. Breakdown: caller-supplied orderBy forwarded
 *     → BreakdownEndpoint#shouldPassThroughCallerOrderByForBreakdown
 *
 * <p>15. Breakdown: all 9 dimension values forwarded (DIMENSION_CONFIG coverage)
 *     → BreakdownEndpoint#shouldDelegateDeviceDimension
 *     → BreakdownEndpoint#shouldDelegateRegionDimension
 *     → BreakdownEndpoint#shouldDelegateReleaseDimension
 *     → BreakdownEndpoint#shouldDelegatePlatformDimension
 *     → BreakdownEndpoint#shouldDelegateOsDimension
 *     → BreakdownEndpoint#shouldDelegateNetworkDimension
 *     → BreakdownEndpoint#shouldDelegateLatencyByNetworkDimension
 *     → BreakdownEndpoint#shouldDelegateLatencyByDeviceDimension
 *     → BreakdownEndpoint#shouldDelegateLatencyByOsDimension
 *
 * <p>16. Breakdown: default limit = 10
 *     → BreakdownEndpoint#shouldUseDefaultLimitOfTenWhenNotProvided
 *
 * <p>17. Breakdown: explicit limit forwarded
 *     → BreakdownEndpoint#shouldPassExplicitLimitToService
 *
 * <p>18. Breakdown: user filters forwarded
 *     → BreakdownEndpoint#shouldPassUserFiltersForBreakdown
 *
 * <p>19. Breakdown: dimension echoed in response
 *     → BreakdownEndpoint#shouldReturnResponseWithDimensionField
 *
 * <p>20. POST /v1/interactions/sessions — delegates to service
 *     → SessionsEndpoint#shouldDelegateSessionsRequest
 *     → SessionsEndpoint#shouldInjectProjectIdForSessionsEndpoint
 *
 * <p>21. Sessions: default orderBy timestamp DESC when caller omits it
 *     → SessionsEndpoint#shouldApplyDefaultTimestampDescOrderWhenSessionsOrderByOmitted
 *
 * <p>22. Sessions: caller-supplied orderBy respected
 *     → SessionsEndpoint#shouldRespectCallerProvidedOrderBy
 *
 * <p>23. Sessions: eventType forwarded for all event types
 *     → SessionsEndpoint#shouldPassCrashEventTypeToService
 *     → SessionsEndpoint#shouldPassAnrEventTypeToService
 *     → SessionsEndpoint#shouldPassErrorEventTypeToService
 *     → SessionsEndpoint#shouldPassNonFatalEventTypeToService
 *     → SessionsEndpoint#shouldPassFrozenFrameEventTypeToService
 *     → SessionsEndpoint#shouldPassNetworkErrorEventTypeToService
 *     → SessionsEndpoint#shouldHandleNullEventType
 *
 * <p>24. Sessions: both scope values forwarded
 *     → SessionsEndpoint#shouldDelegateSessionsScopeRequest
 *     → SessionsEndpoint#shouldDelegateStatsScopeRequest
 *
 * <p>25. Sessions: limit (default 10, explicit value)
 *     → SessionsEndpoint#shouldUseDefaultLimitOfTenForSessions
 *     → SessionsEndpoint#shouldPassExplicitLimitToService
 *
 * <p>26. Sessions: typed response returned
 *     → SessionsEndpoint#shouldReturnTypedSessionRows
 */
@ExtendWith({MockitoExtension.class, VertxExtension.class})
class InteractionAnalyticsResourceTest {

  @Mock PerformanceMetricService performanceMetricService;

  InteractionAnalyticsResource resource;

  @BeforeEach
  void setUp() {
    ProjectContext.setProjectId("project-tdd");
    resource = new InteractionAnalyticsResource(performanceMetricService);
  }

  // ==========================================================================
  // HEALTH ENDPOINT
  // ==========================================================================

  @Nested
  class HealthEndpoint {

    @Test
    void shouldDelegateHealthRequestAndInjectProjectId(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("project-tdd");
        InteractionHealthReq request = InteractionHealthReq.builder().topN(5).build();
        InteractionHealthRes response =
            InteractionHealthRes.builder().interactions(List.of()).build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(response));

        resource.getInteractionHealth(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            assertThat(resp.getData()).isEqualTo(response);
            ArgumentCaptor<InteractionHealthReq> captor =
                ArgumentCaptor.forClass(InteractionHealthReq.class);
            verify(performanceMetricService).getInteractionHealth(captor.capture());
            assertThat(captor.getValue().getProjectId()).isEqualTo("project-tdd");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldInjectProjectIdForHealthEndpoint(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("my-project-123");
        InteractionHealthReq request = InteractionHealthReq.builder().build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(InteractionHealthRes.builder().interactions(List.of()).build()));

        resource.getInteractionHealth(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionHealthReq> captor =
                ArgumentCaptor.forClass(InteractionHealthReq.class);
            verify(performanceMetricService).getInteractionHealth(captor.capture());
            assertThat(captor.getValue().getProjectId()).isEqualTo("my-project-123");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultOrderByWhenOmitted(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionHealthReq request = InteractionHealthReq.builder().build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(InteractionHealthRes.builder().interactions(List.of()).build()));

        resource.getInteractionHealth(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionHealthReq> captor =
                ArgumentCaptor.forClass(InteractionHealthReq.class);
            verify(performanceMetricService).getInteractionHealth(captor.capture());
            List<InteractionOrderBy> orderBy = captor.getValue().getOrderBy();
            assertThat(orderBy).isNotNull().isNotEmpty();
            assertThat(orderBy.get(0).getField()).isEqualTo(ClickhouseConstants.ALIAS_SPAN_FREQ);
            assertThat(orderBy.get(0).getDirection()).isEqualToIgnoringCase("DESC");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldRespectCallerSuppliedOrderByByApdex(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionHealthReq request =
            InteractionHealthReq.builder()
                .orderBy(
                    List.of(InteractionOrderBy.builder().field("apdex").direction("ASC").build()))
                .build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(InteractionHealthRes.builder().interactions(List.of()).build()));

        resource.getInteractionHealth(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionHealthReq> captor =
                ArgumentCaptor.forClass(InteractionHealthReq.class);
            verify(performanceMetricService).getInteractionHealth(captor.capture());
            List<InteractionOrderBy> orderBy = captor.getValue().getOrderBy();
            assertThat(orderBy).hasSize(1);
            assertThat(orderBy.get(0).getField()).isEqualTo("apdex");
            assertThat(orderBy.get(0).getDirection()).isEqualToIgnoringCase("ASC");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldUseDefaultTopNOfTenWhenNotProvided(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionHealthReq request = InteractionHealthReq.builder().build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(InteractionHealthRes.builder().interactions(List.of()).build()));

        resource.getInteractionHealth(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionHealthReq> captor =
                ArgumentCaptor.forClass(InteractionHealthReq.class);
            verify(performanceMetricService).getInteractionHealth(captor.capture());
            assertThat(captor.getValue().getTopN()).isEqualTo(10);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassExplicitTopNToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionHealthReq request = InteractionHealthReq.builder().topN(25).build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(InteractionHealthRes.builder().interactions(List.of()).build()));

        resource.getInteractionHealth(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionHealthReq> captor =
                ArgumentCaptor.forClass(InteractionHealthReq.class);
            verify(performanceMetricService).getInteractionHealth(captor.capture());
            assertThat(captor.getValue().getTopN()).isEqualTo(25);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassSingleInteractionNameToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionHealthReq request =
            InteractionHealthReq.builder()
                .interactionNames(List.of("ContestJoin"))
                .build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(InteractionHealthRes.builder().interactions(List.of()).build()));

        resource.getInteractionHealth(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionHealthReq> captor =
                ArgumentCaptor.forClass(InteractionHealthReq.class);
            verify(performanceMetricService).getInteractionHealth(captor.capture());
            assertThat(captor.getValue().getInteractionNames()).containsExactly("ContestJoin");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassMultipleInteractionNamesToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionHealthReq request =
            InteractionHealthReq.builder()
                .interactionNames(List.of("ContestJoin", "MatchEntry", "ProfileLoad"))
                .build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(InteractionHealthRes.builder().interactions(List.of()).build()));

        resource.getInteractionHealth(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionHealthReq> captor =
                ArgumentCaptor.forClass(InteractionHealthReq.class);
            verify(performanceMetricService).getInteractionHealth(captor.capture());
            assertThat(captor.getValue().getInteractionNames())
                .containsExactlyInAnyOrder("ContestJoin", "MatchEntry", "ProfileLoad");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldOmitInteractionNamesWhenNull(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionHealthReq request = InteractionHealthReq.builder().topN(10).build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(InteractionHealthRes.builder().interactions(List.of()).build()));

        resource.getInteractionHealth(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionHealthReq> captor =
                ArgumentCaptor.forClass(InteractionHealthReq.class);
            verify(performanceMetricService).getInteractionHealth(captor.capture());
            assertThat(captor.getValue().getInteractionNames()).isNullOrEmpty();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassUserFiltersToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionHealthReq request =
            InteractionHealthReq.builder()
                .filters(Map.of("platform", "Android"))
                .build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(InteractionHealthRes.builder().interactions(List.of()).build()));

        resource.getInteractionHealth(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionHealthReq> captor =
                ArgumentCaptor.forClass(InteractionHealthReq.class);
            verify(performanceMetricService).getInteractionHealth(captor.capture());
            assertThat(captor.getValue().getFilters()).containsEntry("platform", "Android");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturnTypedInteractionsList(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionHealthRow row =
            InteractionHealthRow.builder()
                .name("ContestJoin")
                .totalCount(5400L)
                .successCount(5200L)
                .errorCount(200L)
                .errorRatePct(3.7)
                .apdex(0.82)
                .p50Ms(320.0)
                .build();
        InteractionHealthRes response =
            InteractionHealthRes.builder().interactions(List.of(row)).build();

        when(performanceMetricService.getInteractionHealth(any()))
            .thenReturn(Single.just(response));

        resource.getInteractionHealth(InteractionHealthReq.builder().build())
            .whenComplete((resp, err) -> {
              testContext.verify(() -> {
                assertThat(err).isNull();
                assertThat(resp.getData().getInteractions()).hasSize(1);
                InteractionHealthRow r = resp.getData().getInteractions().get(0);
                assertThat(r.getName()).isEqualTo("ContestJoin");
                assertThat(r.getTotalCount()).isEqualTo(5400L);
                assertThat(r.getApdex()).isEqualTo(0.82);
                // typed numeric — NOT a string
                assertThat(r.getApdex()).isInstanceOf(Double.class);
              });
              testContext.completeNow();
            });
      });
    }
  }

  // ==========================================================================
  // METRICS ENDPOINT
  // ==========================================================================

  @Nested
  class MetricsEndpoint {

    @Test
    void shouldDelegateMetricsRequest(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("COMPOSITE")
                .build();
        InteractionMetricsRes response = InteractionMetricsRes.builder().build();

        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(response));

        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            assertThat(resp.getData()).isEqualTo(response);
            verify(performanceMetricService).getInteractionMetrics(any());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldInjectProjectIdForMetricsEndpoint(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("project-tdd");
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("APDEX")
                .build();

        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));

        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().getProjectId()).isEqualTo("project-tdd");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldRejectCallerProvidedOrderByForMetrics(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("APDEX")
                .orderBy(
                    List.of(InteractionOrderBy.builder().field("apdex").direction("DESC").build()))
                .build();

        

        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(resp).isNull();
            assertThat(err).isNotNull();
            assertThat(err.getMessage()).containsIgnoringCase("orderBy");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassTimeseriesTrueFlagToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("APDEX")
                .timeseries(true)
                .build();

        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));

        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().isTimeseries()).isTrue();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldDefaultTimeseriesFalseWhenOmitted(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("LATENCY")
                .build();

        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));

        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().isTimeseries()).isFalse();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassInteractionNameToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("MatchEntry")
                .metricType("COMPOSITE")
                .build();

        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));

        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().getInteractionName()).isEqualTo("MatchEntry");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassCompositeMetricType(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("COMPOSITE")
                .build();
        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));
        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().getMetricType()).isEqualTo("COMPOSITE");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassApdexMetricType(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("APDEX")
                .build();
        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));
        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().getMetricType()).isEqualTo("APDEX");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassLatencyMetricType(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("LATENCY")
                .build();
        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));
        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().getMetricType()).isEqualTo("LATENCY");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassErrorRateMetricType(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("ERROR_RATE")
                .build();
        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));
        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().getMetricType()).isEqualTo("ERROR_RATE");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassUserCategoriesMetricType(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("USER_CATEGORIES")
                .build();
        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));
        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().getMetricType()).isEqualTo("USER_CATEGORIES");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassFramesMetricType(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("FRAMES")
                .build();
        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));
        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().getMetricType()).isEqualTo("FRAMES");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassNetworkMetricType(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionMetricsReq request =
            InteractionMetricsReq.builder()
                .interactionName("ContestJoin")
                .metricType("NETWORK")
                .build();
        when(performanceMetricService.getInteractionMetrics(any()))
            .thenReturn(Single.just(InteractionMetricsRes.builder().build()));
        resource.getInteractionMetrics(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionMetricsReq> captor =
                ArgumentCaptor.forClass(InteractionMetricsReq.class);
            verify(performanceMetricService).getInteractionMetrics(captor.capture());
            assertThat(captor.getValue().getMetricType()).isEqualTo("NETWORK");
          });
          testContext.completeNow();
        });
      });
    }
  }

  // ==========================================================================
  // BREAKDOWN ENDPOINT
  // ==========================================================================

  @Nested
  class BreakdownEndpoint {

    @Test
    void shouldDelegateBreakdownRequest(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionBreakdownReq request =
            InteractionBreakdownReq.builder()
                .interactionName("ContestJoin")
                .dimension("DEVICE")
                .build();
        InteractionBreakdownRes response = InteractionBreakdownRes.builder().build();

        when(performanceMetricService.getInteractionBreakdown(any()))
            .thenReturn(Single.just(response));

        resource.getInteractionBreakdown(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            assertThat(resp.getData()).isEqualTo(response);
            verify(performanceMetricService).getInteractionBreakdown(any());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldInjectProjectIdForBreakdownEndpoint(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("project-tdd");
        InteractionBreakdownReq request =
            InteractionBreakdownReq.builder()
                .interactionName("ContestJoin")
                .dimension("REGION")
                .build();
        when(performanceMetricService.getInteractionBreakdown(any()))
            .thenReturn(Single.just(InteractionBreakdownRes.builder().build()));
        resource.getInteractionBreakdown(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionBreakdownReq> captor =
                ArgumentCaptor.forClass(InteractionBreakdownReq.class);
            verify(performanceMetricService).getInteractionBreakdown(captor.capture());
            assertThat(captor.getValue().getProjectId()).isEqualTo("project-tdd");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassThroughCallerOrderByForBreakdown(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionBreakdownReq request =
            InteractionBreakdownReq.builder()
                .interactionName("ContestJoin")
                .dimension("DEVICE")
                .orderBy(
                    List.of(InteractionOrderBy.builder().field("crash").direction("DESC").build()))
                .build();
        when(performanceMetricService.getInteractionBreakdown(any()))
            .thenReturn(Single.just(InteractionBreakdownRes.builder().build()));
        resource.getInteractionBreakdown(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionBreakdownReq> captor =
                ArgumentCaptor.forClass(InteractionBreakdownReq.class);
            verify(performanceMetricService).getInteractionBreakdown(captor.capture());
            assertThat(captor.getValue().getOrderBy()).isNotEmpty();
            assertThat(captor.getValue().getOrderBy().get(0).getField()).isEqualTo("crash");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldDelegateDeviceDimension(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertBreakdownDimension("DEVICE", vertx, testContext);
    }

    @Test
    void shouldDelegateRegionDimension(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertBreakdownDimension("REGION", vertx, testContext);
    }

    @Test
    void shouldDelegateReleaseDimension(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertBreakdownDimension("RELEASE", vertx, testContext);
    }

    @Test
    void shouldDelegatePlatformDimension(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertBreakdownDimension("PLATFORM", vertx, testContext);
    }

    @Test
    void shouldDelegateOsDimension(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertBreakdownDimension("OS", vertx, testContext);
    }

    @Test
    void shouldDelegateNetworkDimension(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertBreakdownDimension("NETWORK", vertx, testContext);
    }

    @Test
    void shouldDelegateLatencyByNetworkDimension(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertBreakdownDimension("LATENCY_BY_NETWORK", vertx, testContext);
    }

    @Test
    void shouldDelegateLatencyByDeviceDimension(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertBreakdownDimension("LATENCY_BY_DEVICE", vertx, testContext);
    }

    @Test
    void shouldDelegateLatencyByOsDimension(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertBreakdownDimension("LATENCY_BY_OS", vertx, testContext);
    }

    private void assertBreakdownDimension(
        String dimension, io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionBreakdownReq request =
            InteractionBreakdownReq.builder()
                .interactionName("ContestJoin")
                .dimension(dimension)
                .build();
        when(performanceMetricService.getInteractionBreakdown(any()))
            .thenReturn(Single.just(InteractionBreakdownRes.builder().build()));
        resource.getInteractionBreakdown(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionBreakdownReq> captor =
                ArgumentCaptor.forClass(InteractionBreakdownReq.class);
            verify(performanceMetricService).getInteractionBreakdown(captor.capture());
            assertThat(captor.getValue().getDimension()).isEqualTo(dimension);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldUseDefaultLimitOfTenWhenNotProvided(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionBreakdownReq request =
            InteractionBreakdownReq.builder()
                .interactionName("ContestJoin")
                .dimension("DEVICE")
                .build();
        when(performanceMetricService.getInteractionBreakdown(any()))
            .thenReturn(Single.just(InteractionBreakdownRes.builder().build()));
        resource.getInteractionBreakdown(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionBreakdownReq> captor =
                ArgumentCaptor.forClass(InteractionBreakdownReq.class);
            verify(performanceMetricService).getInteractionBreakdown(captor.capture());
            assertThat(captor.getValue().getLimit()).isEqualTo(10);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassExplicitLimitToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionBreakdownReq request =
            InteractionBreakdownReq.builder()
                .interactionName("ContestJoin")
                .dimension("REGION")
                .limit(20)
                .build();
        when(performanceMetricService.getInteractionBreakdown(any()))
            .thenReturn(Single.just(InteractionBreakdownRes.builder().build()));
        resource.getInteractionBreakdown(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionBreakdownReq> captor =
                ArgumentCaptor.forClass(InteractionBreakdownReq.class);
            verify(performanceMetricService).getInteractionBreakdown(captor.capture());
            assertThat(captor.getValue().getLimit()).isEqualTo(20);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassUserFiltersForBreakdown(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionBreakdownReq request =
            InteractionBreakdownReq.builder()
                .interactionName("ContestJoin")
                .dimension("DEVICE")
                .filters(Map.of("platform", "Android"))
                .build();
        when(performanceMetricService.getInteractionBreakdown(any()))
            .thenReturn(Single.just(InteractionBreakdownRes.builder().build()));
        resource.getInteractionBreakdown(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionBreakdownReq> captor =
                ArgumentCaptor.forClass(InteractionBreakdownReq.class);
            verify(performanceMetricService).getInteractionBreakdown(captor.capture());
            assertThat(captor.getValue().getFilters()).containsEntry("platform", "Android");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturnResponseWithDimensionField(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionBreakdownRes response =
            InteractionBreakdownRes.builder()
                .dimension("device")
                .breakdown(List.of(Map.of("deviceModel", "Pixel 6", "frozenFrame", 45L)))
                .build();
        when(performanceMetricService.getInteractionBreakdown(any()))
            .thenReturn(Single.just(response));
        resource
            .getInteractionBreakdown(
                InteractionBreakdownReq.builder()
                    .interactionName("ContestJoin")
                    .dimension("DEVICE")
                    .build())
            .whenComplete((resp, err) -> {
              testContext.verify(() -> {
                assertThat(err).isNull();
                assertThat(resp.getData().getDimension()).isEqualTo("device");
                assertThat(resp.getData().getBreakdown()).hasSize(1);
              });
              testContext.completeNow();
            });
      });
    }
  }

  // ==========================================================================
  // SESSIONS ENDPOINT
  // ==========================================================================

  @Nested
  class SessionsEndpoint {

    @Test
    void shouldDelegateSessionsScopeRequest(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionSessionsReq request =
            InteractionSessionsReq.builder()
                .interactionName("ContestJoin")
                .scope("SESSIONS")
                .build();
        InteractionSessionsRes response = InteractionSessionsRes.builder().build();

        when(performanceMetricService.getInteractionSessions(any()))
            .thenReturn(Single.just(response));

        resource.getInteractionSessions(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            assertThat(err).isNull();
            assertThat(resp.getData()).isEqualTo(response);
            verify(performanceMetricService).getInteractionSessions(any());
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldDelegateStatsScopeRequest(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionSessionsReq request =
            InteractionSessionsReq.builder()
                .interactionName("ContestJoin")
                .scope("STATS")
                .build();
        when(performanceMetricService.getInteractionSessions(any()))
            .thenReturn(Single.just(InteractionSessionsRes.builder().build()));
        resource.getInteractionSessions(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionSessionsReq> captor =
                ArgumentCaptor.forClass(InteractionSessionsReq.class);
            verify(performanceMetricService).getInteractionSessions(captor.capture());
            assertThat(captor.getValue().getScope()).isEqualTo("STATS");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldInjectProjectIdForSessionsEndpoint(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        ProjectContext.setProjectId("project-tdd");
        InteractionSessionsReq request =
            InteractionSessionsReq.builder()
                .interactionName("ContestJoin")
                .scope("SESSIONS")
                .build();
        when(performanceMetricService.getInteractionSessions(any()))
            .thenReturn(Single.just(InteractionSessionsRes.builder().build()));
        resource.getInteractionSessions(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionSessionsReq> captor =
                ArgumentCaptor.forClass(InteractionSessionsReq.class);
            verify(performanceMetricService).getInteractionSessions(captor.capture());
            assertThat(captor.getValue().getProjectId()).isEqualTo("project-tdd");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldApplyDefaultTimestampDescOrderWhenSessionsOrderByOmitted(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionSessionsReq request =
            InteractionSessionsReq.builder()
                .interactionName("ContestJoin")
                .scope("SESSIONS")
                .build();
        when(performanceMetricService.getInteractionSessions(any()))
            .thenReturn(Single.just(InteractionSessionsRes.builder().build()));
        resource.getInteractionSessions(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionSessionsReq> captor =
                ArgumentCaptor.forClass(InteractionSessionsReq.class);
            verify(performanceMetricService).getInteractionSessions(captor.capture());
            List<InteractionOrderBy> orderBy = captor.getValue().getOrderBy();
            assertThat(orderBy).isNotNull().isNotEmpty();
            assertThat(orderBy.get(0).getField()).isEqualTo("timestamp");
            assertThat(orderBy.get(0).getDirection()).isEqualToIgnoringCase("DESC");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldRespectCallerProvidedOrderBy(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionSessionsReq request =
            InteractionSessionsReq.builder()
                .interactionName("ContestJoin")
                .scope("SESSIONS")
                .orderBy(
                    List.of(
                        InteractionOrderBy.builder().field("durationMs").direction("DESC").build()))
                .build();
        when(performanceMetricService.getInteractionSessions(any()))
            .thenReturn(Single.just(InteractionSessionsRes.builder().build()));
        resource.getInteractionSessions(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionSessionsReq> captor =
                ArgumentCaptor.forClass(InteractionSessionsReq.class);
            verify(performanceMetricService).getInteractionSessions(captor.capture());
            assertThat(captor.getValue().getOrderBy().get(0).getField()).isEqualTo("durationMs");
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassCrashEventTypeToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertSessionEventType("crash", vertx, testContext);
    }

    @Test
    void shouldPassAnrEventTypeToService(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertSessionEventType("anr", vertx, testContext);
    }

    @Test
    void shouldPassErrorEventTypeToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertSessionEventType("error", vertx, testContext);
    }

    @Test
    void shouldPassNonFatalEventTypeToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertSessionEventType("non_fatal", vertx, testContext);
    }

    @Test
    void shouldPassFrozenFrameEventTypeToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertSessionEventType("frozen_frame", vertx, testContext);
    }

    @Test
    void shouldPassNetworkErrorEventTypeToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      assertSessionEventType("network_error", vertx, testContext);
    }

    private void assertSessionEventType(
        String eventType, io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionSessionsReq request =
            InteractionSessionsReq.builder()
                .interactionName("ContestJoin")
                .scope("SESSIONS")
                .eventType(eventType)
                .build();
        when(performanceMetricService.getInteractionSessions(any()))
            .thenReturn(Single.just(InteractionSessionsRes.builder().build()));
        resource.getInteractionSessions(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionSessionsReq> captor =
                ArgumentCaptor.forClass(InteractionSessionsReq.class);
            verify(performanceMetricService).getInteractionSessions(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo(eventType);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldHandleNullEventType(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionSessionsReq request =
            InteractionSessionsReq.builder()
                .interactionName("ContestJoin")
                .scope("SESSIONS")
                .build(); // no eventType
        when(performanceMetricService.getInteractionSessions(any()))
            .thenReturn(Single.just(InteractionSessionsRes.builder().build()));
        resource.getInteractionSessions(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionSessionsReq> captor =
                ArgumentCaptor.forClass(InteractionSessionsReq.class);
            verify(performanceMetricService).getInteractionSessions(captor.capture());
            assertThat(captor.getValue().getEventType()).isNull();
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldUseDefaultLimitOfTenForSessions(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionSessionsReq request =
            InteractionSessionsReq.builder()
                .interactionName("ContestJoin")
                .scope("SESSIONS")
                .build();
        when(performanceMetricService.getInteractionSessions(any()))
            .thenReturn(Single.just(InteractionSessionsRes.builder().build()));
        resource.getInteractionSessions(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionSessionsReq> captor =
                ArgumentCaptor.forClass(InteractionSessionsReq.class);
            verify(performanceMetricService).getInteractionSessions(captor.capture());
            assertThat(captor.getValue().getLimit()).isEqualTo(10);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldPassExplicitLimitToService(
        io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionSessionsReq request =
            InteractionSessionsReq.builder()
                .interactionName("ContestJoin")
                .scope("SESSIONS")
                .limit(50)
                .build();
        when(performanceMetricService.getInteractionSessions(any()))
            .thenReturn(Single.just(InteractionSessionsRes.builder().build()));
        resource.getInteractionSessions(request).whenComplete((resp, err) -> {
          testContext.verify(() -> {
            ArgumentCaptor<InteractionSessionsReq> captor =
                ArgumentCaptor.forClass(InteractionSessionsReq.class);
            verify(performanceMetricService).getInteractionSessions(captor.capture());
            assertThat(captor.getValue().getLimit()).isEqualTo(50);
          });
          testContext.completeNow();
        });
      });
    }

    @Test
    void shouldReturnTypedSessionRows(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
      vertx.runOnContext(v -> {
        InteractionSessionsRes response =
            InteractionSessionsRes.builder()
                .sessions(
                    List.of(
                        InteractionSessionRow.builder()
                            .traceId("abc123")
                            .durationMs(450L)
                            .statusCode("Error")
                            .build()))
                .build();
        when(performanceMetricService.getInteractionSessions(any()))
            .thenReturn(Single.just(response));
        resource
            .getInteractionSessions(
                InteractionSessionsReq.builder()
                    .interactionName("ContestJoin")
                    .scope("SESSIONS")
                    .build())
            .whenComplete((resp, err) -> {
              testContext.verify(() -> {
                assertThat(err).isNull();
                assertThat(resp.getData().getSessions()).hasSize(1);
                assertThat(resp.getData().getSessions().get(0).getTraceId()).isEqualTo("abc123");
                // durationMs must be numeric, not a string
                assertThat(resp.getData().getSessions().get(0).getDurationMs())
                    .isInstanceOf(Long.class);
              });
              testContext.completeNow();
            });
      });
    }
  }
}
