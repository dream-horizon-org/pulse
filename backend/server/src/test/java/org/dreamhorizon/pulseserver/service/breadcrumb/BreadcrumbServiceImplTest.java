package org.dreamhorizon.pulseserver.service.breadcrumb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.dreamhorizon.pulseserver.service.query.QueryService;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.dreamhorizon.pulseserver.tenant.Tenant;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

  @Test
  void shouldRejectSessionIdWithSpecialCharacters() {
    QueryJob job = QueryJob.builder().jobId("j4").status(QueryJobStatus.RUNNING).build();
    when(queryService.submitQuery(anyString(), eq("user@test.com")))
        .thenReturn(Single.just(job));

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

  @Test
  void shouldDelegateToQueryService() {
    QueryJob job = QueryJob.builder()
        .jobId("j-delegated")
        .status(QueryJobStatus.COMPLETED)
        .build();
    when(queryService.submitQuery(anyString(), eq("user@test.com")))
        .thenReturn(Single.just(job));

    QueryJob result = service.getSessionBreadcrumbs("session-x", "2026-02-27T15:14:26Z", "user@test.com")
        .blockingGet();

    assertThat(result.getJobId()).isEqualTo("j-delegated");
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
    verify(queryService).submitQuery(anyString(), eq("user@test.com"));
  }
}
