package org.dreamhorizon.pulseserver.resources.query.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.resources.query.models.QueryStatisticsResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.query.QueryStatisticsService;
import org.dreamhorizon.pulseserver.service.query.models.QueryStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
public class GetQueryStatisticsTest {

  @Mock
  QueryStatisticsService queryStatisticsService;

  GetQueryStatistics getQueryStatistics;

  @BeforeEach
  void setUp() {
    getQueryStatistics = new GetQueryStatistics(queryStatisticsService);
  }

  @Test
  void shouldGetQueryStatisticsSuccessfully(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String userEmail = "test@example.com";
      LocalDateTime startDate = LocalDateTime.now().minusDays(7);
      LocalDateTime endDate = LocalDateTime.now();

      QueryStatistics stats = QueryStatistics.builder()
          .userEmail(userEmail)
          .period(QueryStatistics.Period.builder()
              .startDate(startDate)
              .endDate(endDate)
              .build())
          .summary(QueryStatistics.QueryStatisticsSummary.builder()
              .totalQueries(10)
              .succeeded(8)
              .failed(1)
              .cancelled(1)
              .running(0)
              .build())
          .dataStatistics(QueryStatistics.DataStatistics.builder()
              .totalDataScannedBytes(1000000L)
              .totalDataScannedGB(0.001)
              .averageDataScannedBytes(125000L)
              .maxDataScannedBytes(500000L)
              .minDataScannedBytes(10000L)
              .build())
          .timeStatistics(QueryStatistics.TimeStatistics.builder()
              .totalExecutionTimeMillis(5000L)
              .totalExecutionTimeSeconds(5L)
              .averageExecutionTimeMillis(625L)
              .maxExecutionTimeMillis(2000L)
              .minExecutionTimeMillis(100L)
              .build())
          .queries(Collections.emptyList())
          .build();

      when(queryStatisticsService.getQueryStatistics(eq(userEmail), any(LocalDateTime.class), any(LocalDateTime.class)))
          .thenReturn(Single.just(stats));

      CompletionStage<Response<QueryStatisticsResponseDto>> result = 
          getQueryStatistics.getQueryStatistics(userEmail, "2026-01-01T00:00:00", "2026-01-07T23:59:59");

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getUserEmail()).isEqualTo(userEmail);
          assertThat(response.getData().getSummary().getTotalQueries()).isEqualTo(10);
          assertThat(response.getData().getSummary().getSucceeded()).isEqualTo(8);
          assertThat(response.getData().getDataStatistics().getTotalDataScannedBytes()).isEqualTo(1000000L);
          assertThat(response.getData().getTimeStatistics().getTotalExecutionTimeMillis()).isEqualTo(5000L);
          verify(queryStatisticsService).getQueryStatistics(eq(userEmail), any(LocalDateTime.class), any(LocalDateTime.class));
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldHandleNullDates(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String userEmail = "test@example.com";
      QueryStatistics stats = QueryStatistics.builder()
          .userEmail(userEmail)
          .period(QueryStatistics.Period.builder()
              .startDate(null)
              .endDate(null)
              .build())
          .summary(QueryStatistics.QueryStatisticsSummary.builder()
              .totalQueries(0)
              .succeeded(0)
              .failed(0)
              .cancelled(0)
              .running(0)
              .build())
          .dataStatistics(QueryStatistics.DataStatistics.builder()
              .totalDataScannedBytes(0L)
              .totalDataScannedGB(0.0)
              .averageDataScannedBytes(0L)
              .maxDataScannedBytes(0L)
              .minDataScannedBytes(0L)
              .build())
          .timeStatistics(QueryStatistics.TimeStatistics.builder()
              .totalExecutionTimeMillis(0L)
              .totalExecutionTimeSeconds(0L)
              .averageExecutionTimeMillis(0L)
              .maxExecutionTimeMillis(0L)
              .minExecutionTimeMillis(0L)
              .build())
          .queries(Collections.emptyList())
          .build();

      when(queryStatisticsService.getQueryStatistics(eq(userEmail), isNull(), isNull()))
          .thenReturn(Single.just(stats));

      CompletionStage<Response<QueryStatisticsResponseDto>> result = 
          getQueryStatistics.getQueryStatistics(userEmail, null, null);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData().getPeriod().getStartDate()).isNull();
          assertThat(response.getData().getPeriod().getEndDate()).isNull();
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldHandleInvalidDateFormats(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String userEmail = "test@example.com";
      QueryStatistics stats = QueryStatistics.builder()
          .userEmail(userEmail)
          .period(QueryStatistics.Period.builder()
              .startDate(null)
              .endDate(null)
              .build())
          .summary(QueryStatistics.QueryStatisticsSummary.builder()
              .totalQueries(0)
              .succeeded(0)
              .failed(0)
              .cancelled(0)
              .running(0)
              .build())
          .dataStatistics(QueryStatistics.DataStatistics.builder()
              .totalDataScannedBytes(0L)
              .totalDataScannedGB(0.0)
              .averageDataScannedBytes(0L)
              .maxDataScannedBytes(0L)
              .minDataScannedBytes(0L)
              .build())
          .timeStatistics(QueryStatistics.TimeStatistics.builder()
              .totalExecutionTimeMillis(0L)
              .totalExecutionTimeSeconds(0L)
              .averageExecutionTimeMillis(0L)
              .maxExecutionTimeMillis(0L)
              .minExecutionTimeMillis(0L)
              .build())
          .queries(Collections.emptyList())
          .build();

      when(queryStatisticsService.getQueryStatistics(eq(userEmail), isNull(), isNull()))
          .thenReturn(Single.just(stats));

      CompletionStage<Response<QueryStatisticsResponseDto>> result = 
          getQueryStatistics.getQueryStatistics(userEmail, "invalid-date", "also-invalid");

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          verify(queryStatisticsService).getQueryStatistics(eq(userEmail), isNull(), isNull());
        });
        testContext.completeNow();
      });
    });
  }
}

