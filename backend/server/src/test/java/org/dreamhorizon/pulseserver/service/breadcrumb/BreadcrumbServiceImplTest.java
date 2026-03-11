package org.dreamhorizon.pulseserver.service.breadcrumb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.sql.Timestamp;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.dreamhorizon.pulseserver.resources.query.models.SubmitQueryResponseDto;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.dreamhorizon.pulseserver.tenant.Tenant;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class BreadcrumbServiceImplTest {

  @Mock
  QueryService queryService;

  @Captor
  ArgumentCaptor<String> sqlCaptor;

  AthenaConfig athenaConfig;
  BreadcrumbServiceImpl service;

  @BeforeEach
  void setUp() {
    athenaConfig = new AthenaConfig("us-east-1", "pulse_athena_db", "s3://output");
    service = new BreadcrumbServiceImpl(queryService, athenaConfig);
    TenantContext.setTenant(Tenant.builder()
        .tenantId("test_tenant")
        .build());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  class QueryBuilding {
    @Test
    void shouldBuildCorrectSqlQuery() {
      QueryJob job = QueryJob.builder().jobId("j1").status(QueryJobStatus.RUNNING).build();
      when(queryService.submitQuery(anyString(), eq("user@test.com")))
          .thenReturn(Single.just(job));

      service.getSessionBreadcrumbs("abc-123", "2026-02-27T15:14:26Z", "user@test.com")
          .blockingGet();

      verify(queryService).submitQuery(sqlCaptor.capture(), eq("user@test.com"));
      String sql = sqlCaptor.getValue();

      assertThat(sql).contains("SELECT event_name, \"timestamp\", screen_name, props");
      assertThat(sql).contains("FROM pulse_athena_db.otel_data_test_tenant");
      assertThat(sql).contains("WHERE session_id = 'abc-123'");
      assertThat(sql).contains("ORDER BY \"timestamp\" ASC LIMIT 100");
      assertThat(sql).contains("\"timestamp\" >= TIMESTAMP '2026-02-27 15:04:26'");
      assertThat(sql).contains("\"timestamp\" <= TIMESTAMP '2026-02-27 15:14:56'");
    }

    @Test
    void shouldIncludePartitionFilters() {
      QueryJob job = QueryJob.builder().jobId("j2").status(QueryJobStatus.RUNNING).build();
      when(queryService.submitQuery(anyString(), eq("user@test.com")))
          .thenReturn(Single.just(job));

      service.getSessionBreadcrumbs("abc-123", "2026-02-27T15:14:26Z", "user@test.com")
          .blockingGet();

      verify(queryService).submitQuery(sqlCaptor.capture(), eq("user@test.com"));
      String sql = sqlCaptor.getValue();

      assertThat(sql).contains("date = '2026-02-27'");
      assertThat(sql).contains("hour >= '15'");
      assertThat(sql).contains("hour <= '15'");
    }

    @Test
    void shouldUseDateRangeWhenWindowSpansDays() {
      QueryJob job = QueryJob.builder().jobId("j3").status(QueryJobStatus.RUNNING).build();
      when(queryService.submitQuery(anyString(), eq("user@test.com")))
          .thenReturn(Single.just(job));

      service.getSessionBreadcrumbs("abc-123", "2026-02-28T00:05:00Z", "user@test.com")
          .blockingGet();

      verify(queryService).submitQuery(sqlCaptor.capture(), eq("user@test.com"));
      String sql = sqlCaptor.getValue();

      assertThat(sql).contains("date >= '2026-02-27'");
      assertThat(sql).contains("date <= '2026-02-28'");
      assertThat(sql).doesNotContain("date = '");
    }
  }

  @Nested
  class Validation {
    @Test
    void shouldRejectSessionIdWithSpecialCharacters() {
      service.getSessionBreadcrumbs("test's-id", "2026-02-27T15:14:26Z", "user@test.com")
          .test()
          .assertError(IllegalArgumentException.class)
          .assertError(e -> e.getMessage().contains("invalid characters"));
    }

    @Test
    void shouldRejectNullSessionId() {
      service.getSessionBreadcrumbs(null, "2026-02-27T15:14:26Z", "user@test.com")
          .test()
          .assertError(IllegalArgumentException.class)
          .assertError(e -> e.getMessage().contains("Session ID is required"));

      verify(queryService, never()).submitQuery(anyString(), anyString());
    }

    @Test
    void shouldRejectBlankSessionId() {
      service.getSessionBreadcrumbs("  ", "2026-02-27T15:14:26Z", "user@test.com")
          .test()
          .assertError(IllegalArgumentException.class)
          .assertError(e -> e.getMessage().contains("Session ID is required"));

      verify(queryService, never()).submitQuery(anyString(), anyString());
    }

    @Test
    void shouldRejectSqlInjectionAttempt() {
      service.getSessionBreadcrumbs("abc'; DROP TABLE otel_data--", "2026-02-27T15:14:26Z", "user@test.com")
          .test()
          .assertError(IllegalArgumentException.class)
          .assertError(e -> e.getMessage().contains("invalid characters"));

      verify(queryService, never()).submitQuery(anyString(), anyString());
    }

    @Test
    void shouldRejectInvalidTimestamp() {
      service.getSessionBreadcrumbs("abc-123", "not-a-timestamp", "user@test.com")
          .test()
          .assertError(IllegalArgumentException.class)
          .assertError(e -> e.getMessage().contains("Invalid error timestamp"));

      verify(queryService, never()).submitQuery(anyString(), anyString());
    }

    @Test
    void shouldRejectNullTimestamp() {
      service.getSessionBreadcrumbs("abc-123", null, "user@test.com")
          .test()
          .assertError(IllegalArgumentException.class)
          .assertError(e -> e.getMessage().contains("Error timestamp is required"));

      verify(queryService, never()).submitQuery(anyString(), anyString());
    }

    @Test
    void shouldRejectNullUserEmail() {
      service.getSessionBreadcrumbs("abc-123", "2026-02-27T15:14:26Z", null)
          .test()
          .assertError(IllegalArgumentException.class)
          .assertError(e -> e.getMessage().contains("User email is required"));

      verify(queryService, never()).submitQuery(anyString(), anyString());
    }
  }

  @Nested
  class MapToResponse {
    @Test
    void shouldDelegateCompletedToMapCompletedResponse() {
      QueryJob job = QueryJob.builder()
          .jobId("j1").status(QueryJobStatus.COMPLETED)
          .resultData(new JsonArray()).build();

      SubmitQueryResponseDto result = service.mapToResponse(job);

      assertThat(result.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void shouldDelegateFailedToMapFailedOrCancelledResponse() {
      QueryJob job = QueryJob.builder()
          .jobId("j2").status(QueryJobStatus.FAILED).build();

      SubmitQueryResponseDto result = service.mapToResponse(job);

      assertThat(result.getStatus()).isEqualTo("FAILED");
    }

    @Test
    void shouldDelegateRunningToMapInProgressResponse() {
      QueryJob job = QueryJob.builder()
          .jobId("j3").status(QueryJobStatus.RUNNING).build();

      SubmitQueryResponseDto result = service.mapToResponse(job);

      assertThat(result.getStatus()).isEqualTo("RUNNING");
    }
  }

  @Nested
  class MapCompletedResponse {
    @Test
    void shouldIncludeResultDataWhenPresent() {
      Timestamp now = new Timestamp(System.currentTimeMillis());
      JsonArray resultData = new JsonArray();
      resultData.add(new JsonObject()
          .put("event_name", "user_login")
          .put("timestamp", "2026-02-27 15:10:00")
          .put("screen_name", "LoginScreen")
          .put("props", "{\"method\":\"google\"}"));

      QueryJob job = QueryJob.builder()
          .jobId("job-bc-001")
          .status(QueryJobStatus.COMPLETED)
          .resultData(resultData)
          .nextToken("token-abc")
          .dataScannedInBytes(2048L)
          .createdAt(now)
          .completedAt(now)
          .build();

      SubmitQueryResponseDto result =
          service.mapCompletedResponse(job);

      assertThat(result.getJobId()).isEqualTo("job-bc-001");
      assertThat(result.getStatus()).isEqualTo("COMPLETED");
      assertThat(result.getMessage())
          .contains("Breadcrumbs fetched successfully");
      assertThat(result.getResultData()).isNotNull();
      assertThat(result.getResultData().size()).isEqualTo(1);
      assertThat(result.getNextToken()).isEqualTo("token-abc");
      assertThat(result.getDataScannedInBytes()).isEqualTo(2048L);
      assertThat(result.getCreatedAt()).isEqualTo(now);
      assertThat(result.getCompletedAt()).isEqualTo(now);
    }

    @Test
    void shouldReturnPendingMessageWhenResultDataIsNull() {
      Timestamp now = new Timestamp(System.currentTimeMillis());
      QueryJob job = QueryJob.builder()
          .jobId("job-bc-003")
          .status(QueryJobStatus.COMPLETED)
          .resultData(null)
          .dataScannedInBytes(1024L)
          .createdAt(now)
          .completedAt(now)
          .build();

      SubmitQueryResponseDto result =
          service.mapCompletedResponse(job);

      assertThat(result.getStatus()).isEqualTo("COMPLETED");
      assertThat(result.getResultData()).isNull();
      assertThat(result.getMessage())
          .contains("results are not available yet");
      assertThat(result.getDataScannedInBytes()).isEqualTo(1024L);
    }
  }

  @Nested
  class MapFailedOrCancelledResponse {
    @Test
    void shouldUseErrorMessageWhenPresent() {
      Timestamp now = new Timestamp(System.currentTimeMillis());
      QueryJob job = QueryJob.builder()
          .jobId("job-bc-004")
          .status(QueryJobStatus.FAILED)
          .errorMessage("Table not found")
          .createdAt(now)
          .completedAt(now)
          .build();

      SubmitQueryResponseDto result =
          service.mapFailedOrCancelledResponse(job);

      assertThat(result.getJobId()).isEqualTo("job-bc-004");
      assertThat(result.getStatus()).isEqualTo("FAILED");
      assertThat(result.getMessage()).isEqualTo("Table not found");
      assertThat(result.getCreatedAt()).isEqualTo(now);
      assertThat(result.getCompletedAt()).isEqualTo(now);
    }

    @Test
    void shouldFallbackToDefaultMessageWhenNoErrorMessage() {
      QueryJob job = QueryJob.builder()
          .jobId("job-bc-005")
          .status(QueryJobStatus.CANCELLED)
          .build();

      SubmitQueryResponseDto result =
          service.mapFailedOrCancelledResponse(job);

      assertThat(result.getStatus()).isEqualTo("CANCELLED");
      assertThat(result.getMessage()).contains("cancelled");
    }

    @Test
    void shouldHandleFailedWithNullErrorMessage() {
      QueryJob job = QueryJob.builder()
          .jobId("job-bc-006")
          .status(QueryJobStatus.FAILED)
          .errorMessage(null)
          .build();

      SubmitQueryResponseDto result =
          service.mapFailedOrCancelledResponse(job);

      assertThat(result.getStatus()).isEqualTo("FAILED");
      assertThat(result.getMessage()).isEqualTo("Breadcrumb query failed");
    }
  }

  @Nested
  class MapInProgressResponse {
    @Test
    void shouldReturnRunningStatus() {
      Timestamp now = new Timestamp(System.currentTimeMillis());
      QueryJob job = QueryJob.builder()
          .jobId("job-bc-002")
          .status(QueryJobStatus.RUNNING)
          .createdAt(now)
          .build();

      SubmitQueryResponseDto result =
          service.mapInProgressResponse(job);

      assertThat(result.getJobId()).isEqualTo("job-bc-002");
      assertThat(result.getStatus()).isEqualTo("RUNNING");
      assertThat(result.getMessage()).contains("GET /query/job/");
      assertThat(result.getCreatedAt()).isEqualTo(now);
      assertThat(result.getCompletedAt()).isNull();
    }

    @Test
    void shouldReturnSubmittedStatus() {
      QueryJob job = QueryJob.builder()
          .jobId("job-bc-007")
          .status(QueryJobStatus.SUBMITTED)
          .build();

      SubmitQueryResponseDto result =
          service.mapInProgressResponse(job);

      assertThat(result.getStatus()).isEqualTo("SUBMITTED");
      assertThat(result.getMessage())
          .contains("Breadcrumb query submitted");
    }
  }
}
