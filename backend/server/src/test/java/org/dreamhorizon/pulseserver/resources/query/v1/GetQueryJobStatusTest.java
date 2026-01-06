package org.dreamhorizon.pulseserver.resources.query.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import java.sql.Timestamp;
import java.util.concurrent.CompletionStage;
import org.dreamhorizon.pulseserver.resources.query.models.GetJobStatusResponseDto;
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
public class GetQueryJobStatusTest {

  @Mock
  QueryService queryService;

  GetQueryJobStatus getQueryJobStatus;

  @BeforeEach
  void setUp() {
    getQueryJobStatus = new GetQueryJobStatus(queryService);
  }

  @Test
  void shouldGetJobStatus(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String jobId = "job-123";
      Timestamp now = new Timestamp(System.currentTimeMillis());

      QueryJob job = QueryJob.builder()
          .jobId(jobId)
          .queryString("SELECT * FROM table")
          .queryExecutionId("exec-123")
          .status(QueryJobStatus.COMPLETED)
          .resultLocation("s3://bucket/path")
          .dataScannedInBytes(1000L)
          .createdAt(now)
          .updatedAt(now)
          .completedAt(now)
          .build();

      when(queryService.getJobStatus(jobId, 1000, null))
          .thenReturn(Single.just(job));

      CompletionStage<Response<GetJobStatusResponseDto>> result = 
          getQueryJobStatus.getJobStatus(jobId, 1000, null);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getJobId()).isEqualTo(jobId);
          assertThat(response.getData().getStatus()).isEqualTo("COMPLETED");
          verify(queryService).getJobStatus(jobId, 1000, null);
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldHandleNextTokenWithSpaces(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String jobId = "job-123";
      String nextToken = "token with spaces";
      Timestamp now = new Timestamp(System.currentTimeMillis());

      QueryJob job = QueryJob.builder()
          .jobId(jobId)
          .queryString("SELECT * FROM table")
          .status(QueryJobStatus.COMPLETED)
          .createdAt(now)
          .updatedAt(now)
          .build();

      when(queryService.getJobStatus(jobId, 1000, "token+with+spaces"))
          .thenReturn(Single.just(job));

      CompletionStage<Response<GetJobStatusResponseDto>> result = 
          getQueryJobStatus.getJobStatus(jobId, 1000, nextToken);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          verify(queryService).getJobStatus(jobId, 1000, "token+with+spaces");
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldGetJobStatusWithResults(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String jobId = "job-123";
      Timestamp now = new Timestamp(System.currentTimeMillis());

      JsonArray resultData = new JsonArray();
      resultData.add(new io.vertx.core.json.JsonObject().put("col1", "value1"));

      QueryJob job = QueryJob.builder()
          .jobId(jobId)
          .queryString("SELECT * FROM table")
          .queryExecutionId("exec-123")
          .status(QueryJobStatus.COMPLETED)
          .resultData(resultData)
          .nextToken("next-token")
          .dataScannedInBytes(1000L)
          .createdAt(now)
          .updatedAt(now)
          .completedAt(now)
          .build();

      when(queryService.getJobStatus(jobId, 100, "token"))
          .thenReturn(Single.just(job));

      CompletionStage<Response<GetJobStatusResponseDto>> result = 
          getQueryJobStatus.getJobStatus(jobId, 100, "token");

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          assertThat(response.getData().getResultData()).isNotNull();
          assertThat(response.getData().getNextToken()).isEqualTo("next-token");
        });
        testContext.completeNow();
      });
    });
  }

  @Test
  void shouldUseDefaultMaxResults(io.vertx.core.Vertx vertx, VertxTestContext testContext) {
    vertx.runOnContext(v -> {
      String jobId = "job-123";
      Timestamp now = new Timestamp(System.currentTimeMillis());

      QueryJob job = QueryJob.builder()
          .jobId(jobId)
          .queryString("SELECT * FROM table")
          .status(QueryJobStatus.RUNNING)
          .createdAt(now)
          .updatedAt(now)
          .build();

      // When maxResults is null, JAX-RS @DefaultValue will make it 1000
      // But in direct method call, we need to mock for null or pass 1000
      when(queryService.getJobStatus(jobId, null, null))
          .thenReturn(Single.just(job));

      CompletionStage<Response<GetJobStatusResponseDto>> result = 
          getQueryJobStatus.getJobStatus(jobId, null, null);

      result.whenComplete((response, error) -> {
        if (error != null) {
          testContext.failNow(error);
          return;
        }
        testContext.verify(() -> {
          assertThat(response).isNotNull();
          assertThat(response.getData()).isNotNull();
          verify(queryService).getJobStatus(jobId, null, null);
        });
        testContext.completeNow();
      });
    });
  }
}
