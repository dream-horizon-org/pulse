package org.dreamhorizon.pulseserver.resources.query.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.resources.query.models.SubmitQueryRequestDto;
import org.dreamhorizon.pulseserver.resources.query.models.SubmitQueryResponseDto;
import org.dreamhorizon.pulseserver.rest.io.Response;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith({MockitoExtension.class, VertxExtension.class})
@MockitoSettings(strictness = Strictness.LENIENT)
public class SubmitQueryTest {

  @Mock
  QueryService queryService;

  SubmitQuery submitQuery;

  @BeforeEach
  void setUp() {
    submitQuery = new SubmitQuery(queryService);
  }

  @Test
  void shouldSubmitQuerySuccessfully(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String queryString = "SELECT * FROM table";
      SubmitQueryRequestDto request = new SubmitQueryRequestDto(
          queryString, Collections.emptyList(), null);

      Timestamp now = new Timestamp(System.currentTimeMillis());
      QueryJob job = QueryJob.builder()
          .jobId("job-123")
          .queryString(queryString)
          .queryExecutionId("exec-123")
          .status(QueryJobStatus.SUBMITTED)
          .dataScannedInBytes(null)
          .createdAt(now)
          .build();

      when(queryService.submitQuery(queryString, Collections.emptyList(), null))
          .thenReturn(Single.just(job));

      CompletionStage<Response<SubmitQueryResponseDto>> result = submitQuery.submitQuery(request);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getJobId()).isEqualTo("job-123");
          assertThat(response.getData().getStatus()).isEqualTo("SUBMITTED");
          verify(queryService).submitQuery(queryString, Collections.emptyList(), null);
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldHandleCompletedQueryWithResults(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String queryString = "SELECT * FROM table";
      SubmitQueryRequestDto request = new SubmitQueryRequestDto(
          queryString, Collections.emptyList(), null);

      Timestamp now = new Timestamp(System.currentTimeMillis());
      JsonArray resultData = new JsonArray();
      resultData.add(new io.vertx.core.json.JsonObject().put("col1", "value1"));

      QueryJob job = QueryJob.builder()
          .jobId("job-123")
          .queryString(queryString)
          .queryExecutionId("exec-123")
          .status(QueryJobStatus.COMPLETED)
          .resultLocation("s3://bucket/path")
          .resultData(resultData)
          .dataScannedInBytes(1000L)
          .createdAt(now)
          .completedAt(now)
          .build();

      when(queryService.submitQuery(queryString, Collections.emptyList(), null))
          .thenReturn(Single.just(job));

      CompletionStage<Response<SubmitQueryResponseDto>> result = submitQuery.submitQuery(request);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getStatus()).isEqualTo("COMPLETED");
          assertThat(response.getData().getResultData()).isNotNull();
          assertThat(response.getData().getMessage()).contains("Query completed successfully");
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldHandleCompletedQueryWithoutResults(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String queryString = "SELECT * FROM table";
      SubmitQueryRequestDto request = new SubmitQueryRequestDto(
          queryString, Collections.emptyList(), null);

      Timestamp now = new Timestamp(System.currentTimeMillis());
      QueryJob job = QueryJob.builder()
          .jobId("job-123")
          .queryString(queryString)
          .queryExecutionId("exec-123")
          .status(QueryJobStatus.COMPLETED)
          .resultLocation("s3://bucket/path")
          .resultData(null)
          .dataScannedInBytes(1000L)
          .createdAt(now)
          .completedAt(now)
          .build();

      when(queryService.submitQuery(queryString, Collections.emptyList(), null))
          .thenReturn(Single.just(job));

      CompletionStage<Response<SubmitQueryResponseDto>> result = submitQuery.submitQuery(request);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getStatus()).isEqualTo("COMPLETED");
          assertThat(response.getData().getResultData()).isNull();
          assertThat(response.getData().getMessage()).contains("results are not available yet");
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldHandleFailedQuery(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String queryString = "SELECT * FROM table";
      SubmitQueryRequestDto request = new SubmitQueryRequestDto(
          queryString, Collections.emptyList(), null);

      Timestamp now = new Timestamp(System.currentTimeMillis());
      QueryJob job = QueryJob.builder()
          .jobId("job-123")
          .queryString(queryString)
          .queryExecutionId("exec-123")
          .status(QueryJobStatus.FAILED)
          .errorMessage("Query failed")
          .dataScannedInBytes(1000L)
          .createdAt(now)
          .completedAt(now)
          .build();

      when(queryService.submitQuery(queryString, Collections.emptyList(), null))
          .thenReturn(Single.just(job));

      CompletionStage<Response<SubmitQueryResponseDto>> result = submitQuery.submitQuery(request);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getStatus()).isEqualTo("FAILED");
          assertThat(response.getData().getMessage()).isEqualTo("Query failed");
        });
        testContext.completeNow();
      });
    });
  }
}
