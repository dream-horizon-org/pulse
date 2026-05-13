package org.dreamhorizon.pulseserver.resources.webvitals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.dao.webvitals.WebVitalsDao;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalByScreenRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalSummaryRow;
import org.dreamhorizon.pulseserver.dao.webvitals.models.WebVitalTrendRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.resources.webvitals.models.WebVitalsByScreenQueryParams;
import org.dreamhorizon.pulseserver.resources.webvitals.models.WebVitalsSummaryQueryParams;
import org.dreamhorizon.pulseserver.resources.webvitals.models.WebVitalsTrendQueryParams;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.webvitals.WebVitalsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@DisplayName("WebVitals Integration Tests")
class WebVitalsIntegrationTest {

  @Mock private ClickhouseQueryService clickhouseQueryService;

  private WebVitalsDao webVitalsDao;
  private WebVitalsServiceImpl webVitalsService;
  private WebVitalsResource webVitalsResource;

  private static final Instant START_TIME = Instant.parse("2026-05-01T00:00:00Z");
  private static final Instant END_TIME = Instant.parse("2026-05-02T00:00:00Z");
  private static final String TEST_PROJECT_ID = "test-project";

  @BeforeEach
  void setUp() {
    webVitalsDao = new WebVitalsDao(clickhouseQueryService);
    webVitalsService = new WebVitalsServiceImpl(webVitalsDao);
    webVitalsResource = new WebVitalsResource(webVitalsService);
  }

  @Nested
  @DisplayName("End-to-end Summary Flow")
  class EndToEndSummaryFlow {

    @Test
    @DisplayName("should_execute_end_to_end_summary_flow")
    void should_execute_end_to_end_summary_flow(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            try (MockedStatic<ProjectContext> ctx = mockStatic(ProjectContext.class)) {
              ctx.when(ProjectContext::requireProjectId).thenReturn(TEST_PROJECT_ID);

              WebVitalSummaryRow row =
                  WebVitalSummaryRow.builder()
                      .vitalName("LCP")
                      .p75("2500.0")
                      .goodCount("80")
                      .needsImprovementCount("15")
                      .poorCount("5")
                      .totalCount("100")
                      .build();

              QueryResultResponse<WebVitalSummaryRow> mockResponse =
                  QueryResultResponse.<WebVitalSummaryRow>builder()
                      .rows(List.of(row))
                      .build();

              when(clickhouseQueryService.executeQueryOrCreateJob(
                      any(QueryConfiguration.class), eq(WebVitalSummaryRow.class)))
                  .thenReturn(Single.just(mockResponse));

              CompletionStage<Response<WebVitalsSummaryResponseDto>> cs =
                  webVitalsResource.getSummary(
                      summaryQuery(START_TIME.toString(), END_TIME.toString(), null));

              cs.whenComplete(
                  (resp, err) -> {
                    tc.verify(
                        () -> {
                          assertThat(err).isNull();
                          assertThat(resp).isNotNull();
                          assertThat(resp.getData()).isNotNull();
                          assertThat(resp.getData().getVitals()).hasSize(1);
                          VitalSummaryDto vital = resp.getData().getVitals().get(0);
                          assertThat(vital.getName()).isEqualTo("LCP");
                          assertThat(vital.getP75()).isEqualTo(2500.0);
                          assertThat(vital.getGoodPct()).isEqualTo(80.0);
                          assertThat(vital.getNeedsImprovementPct()).isEqualTo(15.0);
                          assertThat(vital.getPoorPct()).isEqualTo(5.0);
                          assertThat(vital.getTotalCount()).isEqualTo(100L);
                        });
                    tc.completeNow();
                  });
            }
          });
    }

    @Test
    @DisplayName("should_execute_end_to_end_summary_flow_with_epoch_millis_query_strings")
    void should_execute_end_to_end_summary_flow_with_epoch_millis_query_strings(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            try (MockedStatic<ProjectContext> ctx = mockStatic(ProjectContext.class)) {
              ctx.when(ProjectContext::requireProjectId).thenReturn(TEST_PROJECT_ID);

              WebVitalSummaryRow row =
                  WebVitalSummaryRow.builder()
                      .vitalName("LCP")
                      .p75("2500.0")
                      .goodCount("80")
                      .needsImprovementCount("15")
                      .poorCount("5")
                      .totalCount("100")
                      .build();

              QueryResultResponse<WebVitalSummaryRow> mockResponse =
                  QueryResultResponse.<WebVitalSummaryRow>builder()
                      .rows(List.of(row))
                      .build();

              when(clickhouseQueryService.executeQueryOrCreateJob(
                      any(QueryConfiguration.class), eq(WebVitalSummaryRow.class)))
                  .thenReturn(Single.just(mockResponse));

              CompletionStage<Response<WebVitalsSummaryResponseDto>> cs =
                  webVitalsResource.getSummary(
                      summaryQuery(
                          String.valueOf(START_TIME.toEpochMilli()),
                          String.valueOf(END_TIME.toEpochMilli()),
                          null));

              cs.whenComplete(
                  (resp, err) -> {
                    tc.verify(
                        () -> {
                          assertThat(err).isNull();
                          assertThat(resp).isNotNull();
                          assertThat(resp.getData().getVitals()).hasSize(1);
                          VitalSummaryDto vital = resp.getData().getVitals().get(0);
                          assertThat(vital.getName()).isEqualTo("LCP");
                          assertThat(vital.getP75()).isEqualTo(2500.0);
                        });
                    tc.completeNow();
                  });
            }
          });
    }

    @Test
    @DisplayName("should_execute_end_to_end_per_screen_summary_flow")
    void should_execute_end_to_end_per_screen_summary_flow(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            try (MockedStatic<ProjectContext> ctx = mockStatic(ProjectContext.class)) {
              ctx.when(ProjectContext::requireProjectId).thenReturn(TEST_PROJECT_ID);

              WebVitalSummaryRow row =
                  WebVitalSummaryRow.builder()
                      .vitalName("INP")
                      .p75("150.5")
                      .goodCount("60")
                      .needsImprovementCount("25")
                      .poorCount("15")
                      .totalCount("100")
                      .build();

              QueryResultResponse<WebVitalSummaryRow> mockResponse =
                  QueryResultResponse.<WebVitalSummaryRow>builder()
                      .rows(List.of(row))
                      .build();

              when(clickhouseQueryService.executeQueryOrCreateJob(
                      any(QueryConfiguration.class), eq(WebVitalSummaryRow.class)))
                  .thenReturn(Single.just(mockResponse));

              CompletionStage<Response<WebVitalsSummaryResponseDto>> cs =
                  webVitalsResource.getSummary(
                      summaryQuery(START_TIME.toString(), END_TIME.toString(), "Home"));

              cs.whenComplete(
                  (resp, err) -> {
                    tc.verify(
                        () -> {
                          assertThat(err).isNull();
                          assertThat(resp).isNotNull();
                          assertThat(resp.getData().getVitals()).hasSize(1);
                          VitalSummaryDto vital = resp.getData().getVitals().get(0);
                          assertThat(vital.getName()).isEqualTo("INP");
                          assertThat(vital.getP75()).isEqualTo(150.5);
                          assertThat(vital.getGoodPct()).isEqualTo(60.0);
                        });
                    tc.completeNow();
                  });
            }
          });
    }
  }

  @Nested
  @DisplayName("End-to-end Trend Flow")
  class EndToEndTrendFlow {

    @Test
    @DisplayName("should_execute_end_to_end_trend_flow")
    void should_execute_end_to_end_trend_flow(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            try (MockedStatic<ProjectContext> ctx = mockStatic(ProjectContext.class)) {
              ctx.when(ProjectContext::requireProjectId).thenReturn(TEST_PROJECT_ID);

              WebVitalTrendRow row1 =
                  WebVitalTrendRow.builder()
                      .bucket("2026-05-01T00:00:00Z")
                      .p75("2400.0")
                      .build();
              WebVitalTrendRow row2 =
                  WebVitalTrendRow.builder()
                      .bucket("2026-05-01T00:30:00Z")
                      .p75("2500.0")
                      .build();
              WebVitalTrendRow row3 =
                  WebVitalTrendRow.builder()
                      .bucket("2026-05-01T01:00:00Z")
                      .p75("2600.0")
                      .build();

              QueryResultResponse<WebVitalTrendRow> mockResponse =
                  QueryResultResponse.<WebVitalTrendRow>builder()
                      .rows(List.of(row1, row2, row3))
                      .build();

              when(clickhouseQueryService.executeQueryOrCreateJob(
                      any(QueryConfiguration.class), eq(WebVitalTrendRow.class)))
                  .thenReturn(Single.just(mockResponse));

              CompletionStage<Response<WebVitalsTrendResponseDto>> cs =
                  webVitalsResource.getTrend(
                      trendQuery(START_TIME.toString(), END_TIME.toString(), "LCP", 30, null));

              cs.whenComplete(
                  (resp, err) -> {
                    tc.verify(
                        () -> {
                          assertThat(err).isNull();
                          assertThat(resp).isNotNull();
                          assertThat(resp.getData().getPoints()).hasSize(3);
                          assertThat(resp.getData().getPoints().get(0).getP75())
                              .isEqualTo(2400.0);
                          assertThat(resp.getData().getPoints().get(1).getP75())
                              .isEqualTo(2500.0);
                          assertThat(resp.getData().getPoints().get(2).getP75())
                              .isEqualTo(2600.0);
                        });
                    tc.completeNow();
                  });
            }
          });
    }

    @Test
    @DisplayName("should_execute_end_to_end_trend_flow_with_epoch_millis_query_strings")
    void should_execute_end_to_end_trend_flow_with_epoch_millis_query_strings(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            try (MockedStatic<ProjectContext> ctx = mockStatic(ProjectContext.class)) {
              ctx.when(ProjectContext::requireProjectId).thenReturn(TEST_PROJECT_ID);

              WebVitalTrendRow row =
                  WebVitalTrendRow.builder()
                      .bucket("2026-05-01T00:00:00Z")
                      .p75("2400.0")
                      .build();

              QueryResultResponse<WebVitalTrendRow> mockResponse =
                  QueryResultResponse.<WebVitalTrendRow>builder()
                      .rows(List.of(row))
                      .build();

              when(clickhouseQueryService.executeQueryOrCreateJob(
                      any(QueryConfiguration.class), eq(WebVitalTrendRow.class)))
                  .thenReturn(Single.just(mockResponse));

              CompletionStage<Response<WebVitalsTrendResponseDto>> cs =
                  webVitalsResource.getTrend(
                      trendQuery(
                          String.valueOf(START_TIME.toEpochMilli()),
                          String.valueOf(END_TIME.toEpochMilli()),
                          "LCP",
                          30,
                          null));

              cs.whenComplete(
                  (resp, err) -> {
                    tc.verify(
                        () -> {
                          assertThat(err).isNull();
                          assertThat(resp.getData().getPoints()).hasSize(1);
                          assertThat(resp.getData().getPoints().get(0).getP75()).isEqualTo(2400.0);
                        });
                    tc.completeNow();
                  });
            }
          });
    }
  }

  @Nested
  @DisplayName("End-to-end By-Screen Flow")
  class EndToEndByScreenFlow {

    @Test
    @DisplayName("should_execute_end_to_end_by_screen_flow")
    void should_execute_end_to_end_by_screen_flow(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            try (MockedStatic<ProjectContext> ctx = mockStatic(ProjectContext.class)) {
              ctx.when(ProjectContext::requireProjectId).thenReturn(TEST_PROJECT_ID);

              WebVitalByScreenRow row1 =
                  WebVitalByScreenRow.builder()
                      .screenName("Home")
                      .p75("2500.0")
                      .totalCount("150")
                      .goodPct("85.0")
                      .build();
              WebVitalByScreenRow row2 =
                  WebVitalByScreenRow.builder()
                      .screenName("Details")
                      .p75("3000.0")
                      .totalCount("100")
                      .goodPct("70.0")
                      .build();
              WebVitalByScreenRow row3 =
                  WebVitalByScreenRow.builder()
                      .screenName("Product")
                      .p75("2800.0")
                      .totalCount("120")
                      .goodPct("75.0")
                      .build();

              QueryResultResponse<WebVitalByScreenRow> mockResponse =
                  QueryResultResponse.<WebVitalByScreenRow>builder()
                      .rows(List.of(row1, row2, row3))
                      .build();

              when(clickhouseQueryService.executeQueryOrCreateJob(
                      any(QueryConfiguration.class), eq(WebVitalByScreenRow.class)))
                  .thenReturn(Single.just(mockResponse));

              CompletionStage<Response<WebVitalsByScreenResponseDto>> cs =
                  webVitalsResource.getByScreen(
                      byScreenQuery(START_TIME.toString(), END_TIME.toString(), "INP"));

              cs.whenComplete(
                  (resp, err) -> {
                    tc.verify(
                        () -> {
                          assertThat(err).isNull();
                          assertThat(resp).isNotNull();
                          assertThat(resp.getData().getScreens()).hasSize(3);
                          ScreenVitalDto screen1 = resp.getData().getScreens().get(0);
                          assertThat(screen1.getScreenName()).isEqualTo("Home");
                          assertThat(screen1.getP75()).isEqualTo(2500.0);
                          assertThat(screen1.getTotalCount()).isEqualTo(150L);
                          assertThat(screen1.getGoodPct()).isEqualTo(85.0);
                        });
                    tc.completeNow();
                  });
            }
          });
    }

    @Test
    @DisplayName("should_execute_end_to_end_by_screen_flow_with_epoch_millis_query_strings")
    void should_execute_end_to_end_by_screen_flow_with_epoch_millis_query_strings(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            try (MockedStatic<ProjectContext> ctx = mockStatic(ProjectContext.class)) {
              ctx.when(ProjectContext::requireProjectId).thenReturn(TEST_PROJECT_ID);

              WebVitalByScreenRow row =
                  WebVitalByScreenRow.builder()
                      .screenName("Home")
                      .p75("2500.0")
                      .totalCount("150")
                      .goodPct("85.0")
                      .build();

              QueryResultResponse<WebVitalByScreenRow> mockResponse =
                  QueryResultResponse.<WebVitalByScreenRow>builder()
                      .rows(List.of(row))
                      .build();

              when(clickhouseQueryService.executeQueryOrCreateJob(
                      any(QueryConfiguration.class), eq(WebVitalByScreenRow.class)))
                  .thenReturn(Single.just(mockResponse));

              CompletionStage<Response<WebVitalsByScreenResponseDto>> cs =
                  webVitalsResource.getByScreen(
                      byScreenQuery(
                          String.valueOf(START_TIME.toEpochMilli()),
                          String.valueOf(END_TIME.toEpochMilli()),
                          "INP"));

              cs.whenComplete(
                  (resp, err) -> {
                    tc.verify(
                        () -> {
                          assertThat(err).isNull();
                          assertThat(resp.getData().getScreens()).hasSize(1);
                          assertThat(resp.getData().getScreens().get(0).getScreenName())
                              .isEqualTo("Home");
                        });
                    tc.completeNow();
                  });
            }
          });
    }
  }

  @Nested
  @DisplayName("Error Handling")
  class ErrorHandling {

    @Test
    @DisplayName("should_handle_invalid_project_id_in_flow")
    void should_handle_invalid_project_id_in_flow(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.clear();
            try {
              webVitalsResource.getSummary(
                  summaryQuery(START_TIME.toString(), END_TIME.toString(), null));
              tc.failNow("Should have thrown WebApplicationException");
            } catch (WebApplicationException e) {
              tc.verify(
                  () -> {
                    assertThat(e.getResponse().getStatus()).isEqualTo(400);
                  });
              tc.completeNow();
            }
          });
    }
  }

  private static WebVitalsSummaryQueryParams summaryQuery(
      String startTime, String endTime, String screenName) {
    WebVitalsSummaryQueryParams q = new WebVitalsSummaryQueryParams();
    q.setStartTime(startTime);
    q.setEndTime(endTime);
    q.setScreenName(screenName);
    return q;
  }

  private static WebVitalsTrendQueryParams trendQuery(
      String startTime,
      String endTime,
      String vitalName,
      Integer bucketMinutes,
      String screenName) {
    WebVitalsTrendQueryParams q = new WebVitalsTrendQueryParams();
    q.setStartTime(startTime);
    q.setEndTime(endTime);
    q.setVitalName(vitalName);
    q.setBucketMinutes(bucketMinutes);
    q.setScreenName(screenName);
    return q;
  }

  private static WebVitalsByScreenQueryParams byScreenQuery(
      String startTime, String endTime, String vitalName) {
    WebVitalsByScreenQueryParams q = new WebVitalsByScreenQueryParams();
    q.setStartTime(startTime);
    q.setEndTime(endTime);
    q.setVitalName(vitalName);
    return q;
  }
}
