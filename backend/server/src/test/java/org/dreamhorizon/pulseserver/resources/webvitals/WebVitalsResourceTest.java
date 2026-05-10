package org.dreamhorizon.pulseserver.resources.webvitals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.webvitals.WebVitalsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@DisplayName("WebVitalsResource")
class WebVitalsResourceTest {

  @Mock private WebVitalsService webVitalsService;

  @InjectMocks private WebVitalsResource webVitalsResource;

  private static final Instant START_TIME = Instant.parse("2026-05-01T00:00:00Z");
  private static final Instant END_TIME = Instant.parse("2026-05-02T00:00:00Z");

  @BeforeEach
  void setUp() {
    lenient().when(webVitalsService.getSummary(START_TIME, END_TIME, null))
        .thenReturn(Single.just(WebVitalsSummaryResponseDto.builder().vitals(List.of()).build()));
  }

  @Nested
  @DisplayName("GET /v1/web-vitals/summary")
  class GetSummary {

    @Test
    @DisplayName("should_return_400_when_X_Project_ID_header_missing")
    void shouldReturn400WhenProjectIdMissing(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.clear();
            try {
              webVitalsResource.getSummary(START_TIME.toString(), END_TIME.toString(), null);
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

    @Test
    @DisplayName("should_call_global_summary_service_when_no_screenName")
    void shouldCallGlobalSummaryWhenNoScreenName(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            CompletionStage<Response<WebVitalsSummaryResponseDto>> cs =
                webVitalsResource.getSummary(START_TIME.toString(), END_TIME.toString(), null);
            cs.whenComplete(
                (resp, err) -> {
                  tc.verify(
                      () -> {
                        assertThat(err).isNull();
                        assertThat(resp).isNotNull();
                        verify(webVitalsService).getSummary(START_TIME, END_TIME, null);
                      });
                  tc.completeNow();
                });
          });
    }

    @Test
    @DisplayName("should_accept_epoch_millisecond_strings_for_start_and_end")
    void shouldAcceptEpochMillisecondStrings(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            String startMs = String.valueOf(START_TIME.toEpochMilli());
            String endMs = String.valueOf(END_TIME.toEpochMilli());
            CompletionStage<Response<WebVitalsSummaryResponseDto>> cs =
                webVitalsResource.getSummary(startMs, endMs, null);
            cs.whenComplete(
                (resp, err) -> {
                  tc.verify(
                      () -> {
                        assertThat(err).isNull();
                        assertThat(resp).isNotNull();
                        verify(webVitalsService).getSummary(START_TIME, END_TIME, null);
                      });
                  tc.completeNow();
                });
          });
    }

    @Test
    @DisplayName("should_call_per_screen_summary_service_when_screenName_provided")
    void shouldCallPerScreenSummaryWhenScreenNameProvided(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            when(webVitalsService.getSummary(START_TIME, END_TIME, "Home"))
                .thenReturn(Single.just(WebVitalsSummaryResponseDto.builder().vitals(List.of()).build()));

            CompletionStage<Response<WebVitalsSummaryResponseDto>> cs =
                webVitalsResource.getSummary(
                    START_TIME.toString(), END_TIME.toString(), "Home");
            cs.whenComplete(
                (resp, err) -> {
                  tc.verify(
                      () -> {
                        assertThat(err).isNull();
                        assertThat(resp).isNotNull();
                        verify(webVitalsService).getSummary(START_TIME, END_TIME, "Home");
                      });
                  tc.completeNow();
                });
          });
    }

    @Test
    @DisplayName("should_return_200_with_correct_response_shape")
    void shouldReturn200WithCorrectResponseShape(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            VitalSummaryDto vital =
                VitalSummaryDto.builder()
                    .name("LCP")
                    .p75(2500.0)
                    .goodPct(80.0)
                    .needsImprovementPct(15.0)
                    .poorPct(5.0)
                    .totalCount(100L)
                    .build();

            WebVitalsSummaryResponseDto response =
                WebVitalsSummaryResponseDto.builder().vitals(List.of(vital)).build();

            when(webVitalsService.getSummary(START_TIME, END_TIME, null))
                .thenReturn(Single.just(response));

            CompletionStage<Response<WebVitalsSummaryResponseDto>> cs =
                webVitalsResource.getSummary(
                    START_TIME.toString(), END_TIME.toString(), null);
            cs.whenComplete(
                (resp, err) -> {
                  tc.verify(
                      () -> {
                        assertThat(err).isNull();
                        assertThat(resp).isNotNull();
                        assertThat(resp.getData()).isNotNull();
                        assertThat(resp.getData().getVitals()).hasSize(1);
                      });
                  tc.completeNow();
                });
          });
    }
  }

  @Nested
  @DisplayName("GET /v1/web-vitals/trend")
  class GetTrend {

    @Test
    @DisplayName("should_default_bucketMinutes_to_30_when_not_provided")
    void shouldDefaultBucketMinutesTo30(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            WebVitalsTrendResponseDto response =
                WebVitalsTrendResponseDto.builder().points(List.of()).build();

            when(webVitalsService.getTrend(START_TIME, END_TIME, "LCP", 30, null))
                .thenReturn(Single.just(response));

            CompletionStage<Response<WebVitalsTrendResponseDto>> cs =
                webVitalsResource.getTrend(
                    START_TIME.toString(), END_TIME.toString(), "LCP", null, null);
            cs.whenComplete(
                (resp, err) -> {
                  tc.verify(
                      () -> {
                        assertThat(err).isNull();
                        assertThat(resp).isNotNull();
                        verify(webVitalsService).getTrend(START_TIME, END_TIME, "LCP", 30, null);
                      });
                  tc.completeNow();
                });
          });
    }

    @Test
    @DisplayName("should_accept_epoch_millisecond_strings_for_trend_time_range")
    void shouldAcceptEpochMillisecondStringsForTrend(io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            WebVitalsTrendResponseDto response =
                WebVitalsTrendResponseDto.builder().points(List.of()).build();

            when(webVitalsService.getTrend(START_TIME, END_TIME, "LCP", 30, null))
                .thenReturn(Single.just(response));

            String startMs = String.valueOf(START_TIME.toEpochMilli());
            String endMs = String.valueOf(END_TIME.toEpochMilli());
            CompletionStage<Response<WebVitalsTrendResponseDto>> cs =
                webVitalsResource.getTrend(startMs, endMs, "LCP", null, null);
            cs.whenComplete(
                (resp, err) -> {
                  tc.verify(
                      () -> {
                        assertThat(err).isNull();
                        assertThat(resp).isNotNull();
                        verify(webVitalsService).getTrend(START_TIME, END_TIME, "LCP", 30, null);
                      });
                  tc.completeNow();
                });
          });
    }

    @Test
    @DisplayName("should_return_400_for_bucketMinutes_out_of_range_below_minimum")
    void shouldReturn400ForBucketMinutesBelowMinimum(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            try {
              webVitalsResource.getTrend(
                  START_TIME.toString(), END_TIME.toString(), "LCP", 4, null);
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

    @Test
    @DisplayName("should_return_400_for_bucketMinutes_out_of_range_above_maximum")
    void shouldReturn400ForBucketMinutesAboveMaximum(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            try {
              webVitalsResource.getTrend(
                  START_TIME.toString(), END_TIME.toString(), "LCP", 1441, null);
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

    @Test
    @DisplayName("should_return_200_with_correct_response_shape")
    void shouldReturn200WithCorrectResponseShape(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            TrendPointDto point =
                TrendPointDto.builder().bucket("2026-05-01T00:00:00Z").p75(2500.0).build();

            WebVitalsTrendResponseDto response =
                WebVitalsTrendResponseDto.builder().points(List.of(point)).build();

            when(webVitalsService.getTrend(START_TIME, END_TIME, "LCP", 60, null))
                .thenReturn(Single.just(response));

            CompletionStage<Response<WebVitalsTrendResponseDto>> cs =
                webVitalsResource.getTrend(
                    START_TIME.toString(), END_TIME.toString(), "LCP", 60, null);
            cs.whenComplete(
                (resp, err) -> {
                  tc.verify(
                      () -> {
                        assertThat(err).isNull();
                        assertThat(resp).isNotNull();
                        assertThat(resp.getData()).isNotNull();
                      });
                  tc.completeNow();
                });
          });
    }
  }

  @Nested
  @DisplayName("GET /v1/web-vitals/by-screen")
  class GetByScreen {

    @Test
    @DisplayName("should_return_200_with_correct_response_shape")
    void shouldReturn200WithCorrectResponseShape(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            ScreenVitalDto screen =
                ScreenVitalDto.builder()
                    .screenName("Home")
                    .p75(2500.0)
                    .totalCount(100L)
                    .goodPct(80.0)
                    .build();

            WebVitalsByScreenResponseDto response =
                WebVitalsByScreenResponseDto.builder().screens(List.of(screen)).build();

            when(webVitalsService.getByScreen(START_TIME, END_TIME, "LCP"))
                .thenReturn(Single.just(response));

            CompletionStage<Response<WebVitalsByScreenResponseDto>> cs =
                webVitalsResource.getByScreen(
                    START_TIME.toString(), END_TIME.toString(), "LCP");
            cs.whenComplete(
                (resp, err) -> {
                  tc.verify(
                      () -> {
                        assertThat(err).isNull();
                        assertThat(resp).isNotNull();
                        assertThat(resp.getData()).isNotNull();
                      });
                  tc.completeNow();
                });
          });
    }

    @Test
    @DisplayName("should_accept_epoch_millisecond_strings_for_by_screen_time_range")
    void shouldAcceptEpochMillisecondStringsForByScreen(
        io.vertx.core.Vertx vertx, VertxTestContext tc) {
      vertx.runOnContext(
          v -> {
            ProjectContext.setProjectId("test-project");
            WebVitalsByScreenResponseDto response =
                WebVitalsByScreenResponseDto.builder().screens(List.of()).build();

            when(webVitalsService.getByScreen(START_TIME, END_TIME, "LCP"))
                .thenReturn(Single.just(response));

            String startMs = String.valueOf(START_TIME.toEpochMilli());
            String endMs = String.valueOf(END_TIME.toEpochMilli());
            CompletionStage<Response<WebVitalsByScreenResponseDto>> cs =
                webVitalsResource.getByScreen(startMs, endMs, "LCP");
            cs.whenComplete(
                (resp, err) -> {
                  tc.verify(
                      () -> {
                        assertThat(err).isNull();
                        assertThat(resp).isNotNull();
                        verify(webVitalsService).getByScreen(START_TIME, END_TIME, "LCP");
                      });
                  tc.completeNow();
                });
          });
    }
  }
}
