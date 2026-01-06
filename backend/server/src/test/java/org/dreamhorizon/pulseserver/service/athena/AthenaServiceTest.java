package org.dreamhorizon.pulseserver.service.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import java.util.Collections;
import org.dreamhorizon.pulseserver.client.athena.AthenaClient;
import org.dreamhorizon.pulseserver.client.athena.models.ResultSetWithToken;
import org.dreamhorizon.pulseserver.dao.athena.AthenaJobDao;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJob;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Datum;
import software.amazon.awssdk.services.athena.model.QueryExecution;
import software.amazon.awssdk.services.athena.model.QueryExecutionState;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatistics;
import software.amazon.awssdk.services.athena.model.QueryExecutionStatus;
import software.amazon.awssdk.services.athena.model.ResultConfiguration;
import software.amazon.awssdk.services.athena.model.ResultSet;
import software.amazon.awssdk.services.athena.model.ResultSetMetadata;
import software.amazon.awssdk.services.athena.model.Row;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AthenaServiceTest {

  @Mock
  AthenaClient athenaClient;

  @Mock
  AthenaJobDao athenaJobDao;

  AthenaService athenaService;

  @BeforeEach
  void setUp() {
    athenaService = new AthenaService(athenaClient, athenaJobDao);
  }

  @Nested
  class TestSubmitQuery {

    @Test
    void shouldRejectInvalidQuery() {
      String invalidQuery = "INVALID QUERY";

      var testObserver = athenaService.submitQuery(invalidQuery, Collections.emptyList(), null).test();

      testObserver.assertError(IllegalArgumentException.class);
    }

    @Test
    void shouldSubmitQuerySuccessfully() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String jobId = "job-123";
      String queryExecutionId = "exec-123";

      when(athenaJobDao.createJob(anyString())).thenReturn(Single.just(jobId));
      when(athenaClient.submitQuery(anyString(), any())).thenReturn(Single.just(queryExecutionId));
      
      QueryExecutionStatistics stats = QueryExecutionStatistics.builder()
          .dataScannedInBytes(1000L)
          .build();
      ResultConfiguration resultConfig = ResultConfiguration.builder()
          .outputLocation("s3://bucket/path")
          .build();
      QueryExecution execution = QueryExecution.builder()
          .statistics(stats)
          .resultConfiguration(resultConfig)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobWithExecutionId(anyString(), anyString(), any())).thenReturn(Single.just(true));
      when(athenaClient.getQueryStatus(anyString())).thenReturn(Single.just(QueryExecutionState.SUCCEEDED));

      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(Row.builder()
              .data(Datum.builder().varCharValue("header").build())
              .build())
          .build();
      ResultSetWithToken resultSetWithToken = new ResultSetWithToken(resultSet, null);
      when(athenaClient.getQueryResults(anyString(), anyInt(), isNull())).thenReturn(Single.just(resultSetWithToken));
      when(athenaJobDao.updateJobCompleted(anyString(), anyString())).thenReturn(Single.just(true));
      
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .resultLocation("s3://bucket/path")
          .build();
      when(athenaJobDao.getJobById(anyString())).thenReturn(Single.just(job));

      AthenaJob result = athenaService.submitQuery(query, Collections.emptyList(), null)
          .blockingGet();

      assertThat(result).isNotNull();
    }

    @Test
    void shouldEnrichQueryWithTimestamp() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";
      String timestamp = "2025-12-23 11:29:35";
      String jobId = "job-123";
      String queryExecutionId = "exec-123";

      when(athenaJobDao.createJob(anyString())).thenReturn(Single.just(jobId));
      when(athenaClient.submitQuery(anyString(), any())).thenReturn(Single.just(queryExecutionId));
      
      QueryExecutionStatistics stats = QueryExecutionStatistics.builder()
          .dataScannedInBytes(1000L)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .statistics(stats)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobWithExecutionId(anyString(), anyString(), any())).thenReturn(Single.just(true));
      when(athenaClient.getQueryStatus(anyString())).thenReturn(Single.just(QueryExecutionState.RUNNING));

      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.RUNNING)
          .build();
      when(athenaJobDao.getJobById(anyString())).thenReturn(Single.just(job));

      AthenaJob result = athenaService.submitQuery(query, Collections.emptyList(), timestamp)
          .blockingGet();

      assertThat(result).isNotNull();
      verify(athenaJobDao).createJob(anyString());
    }

    @Test
    void shouldHandleQueryFailure() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String jobId = "job-123";
      String queryExecutionId = "exec-123";

      when(athenaJobDao.createJob(anyString())).thenReturn(Single.just(jobId));
      when(athenaClient.submitQuery(anyString(), any())).thenReturn(Single.just(queryExecutionId));
      
      QueryExecutionStatistics stats = QueryExecutionStatistics.builder()
          .dataScannedInBytes(1000L)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .statistics(stats)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobWithExecutionId(anyString(), anyString(), any())).thenReturn(Single.just(true));
      when(athenaClient.getQueryStatus(anyString())).thenReturn(Single.just(QueryExecutionState.FAILED));

      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .stateChangeReason("Query failed")
          .build();
      QueryExecution failedExecution = QueryExecution.builder()
          .status(status)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(failedExecution));
      when(athenaJobDao.updateJobFailed(anyString(), anyString())).thenReturn(Single.just(true));

      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.FAILED)
          .build();
      when(athenaJobDao.getJobById(anyString())).thenReturn(Single.just(job));

      AthenaJob result = athenaService.submitQuery(query, Collections.emptyList(), null)
          .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.FAILED);
    }
  }

  @Nested
  class TestGetJobStatus {

    @Test
    void shouldGetJobStatusForCompletedJob() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .queryExecutionId("exec-123")
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));

      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(Row.builder()
              .data(Datum.builder().varCharValue("header").build())
              .build())
          .build();
      ResultSetWithToken resultSetWithToken = new ResultSetWithToken(resultSet, "next-token");
      when(athenaClient.getQueryResults(anyString(), anyInt(), isNull())).thenReturn(Single.just(resultSetWithToken));

      AthenaJob result = athenaService.getJobStatus(jobId, 100, null).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.COMPLETED);
    }

    @Test
    void shouldReturnErrorWhenJobNotFound() {
      String jobId = "job-123";

      RuntimeException error = new RuntimeException("Job not found");
      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.error(error));

      var testObserver = athenaService.getJobStatus(jobId, null, null).test();
      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldUpdateJobStatusWhenRunning() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.RUNNING)
          .queryExecutionId("exec-123")
          .build();

      AthenaJob updatedJob = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .resultLocation("s3://bucket/path")
          .build();

      when(athenaJobDao.getJobById(anyString()))
          .thenReturn(Single.just(job))
          .thenReturn(Single.just(updatedJob));
      when(athenaClient.getQueryStatus(anyString())).thenReturn(Single.just(QueryExecutionState.SUCCEEDED));

      QueryExecutionStatistics stats = QueryExecutionStatistics.builder()
          .dataScannedInBytes(1000L)
          .build();
      ResultConfiguration resultConfig = ResultConfiguration.builder()
          .outputLocation("s3://bucket/path")
          .build();
      QueryExecution execution = QueryExecution.builder()
          .statistics(stats)
          .resultConfiguration(resultConfig)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobCompleted(anyString(), anyString())).thenReturn(Single.just(true));

      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(Row.builder()
              .data(Datum.builder().varCharValue("header").build())
              .build())
          .build();
      ResultSetWithToken resultSetWithToken = new ResultSetWithToken(resultSet, null);
      when(athenaClient.getQueryResults(anyString(), anyInt(), isNull())).thenReturn(Single.just(resultSetWithToken));

      AthenaJob result = athenaService.getJobStatus(jobId, null, null).blockingGet();

      assertThat(result).isNotNull();
    }
  }

  @Nested
  class TestWaitForJobCompletion {

    @Test
    void shouldWaitForJobCompletionSuccessfully() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.RUNNING)
          .queryExecutionId("exec-123")
          .build();

      AthenaJob completedJob = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .resultLocation("s3://bucket/path")
          .build();

      when(athenaJobDao.getJobById(anyString()))
          .thenReturn(Single.just(job))
          .thenReturn(Single.just(completedJob));
      when(athenaClient.waitForQueryCompletion(anyString())).thenReturn(Single.just(QueryExecutionState.SUCCEEDED));

      QueryExecutionStatistics stats = QueryExecutionStatistics.builder()
          .dataScannedInBytes(1000L)
          .build();
      ResultConfiguration resultConfig = ResultConfiguration.builder()
          .outputLocation("s3://bucket/path")
          .build();
      QueryExecution execution = QueryExecution.builder()
          .statistics(stats)
          .resultConfiguration(resultConfig)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobCompleted(anyString(), anyString())).thenReturn(Single.just(true));

      AthenaJob result = athenaService.waitForJobCompletion(jobId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.COMPLETED);
    }

    @Test
    void shouldReturnErrorWhenJobNotFound() {
      String jobId = "job-123";

      RuntimeException error = new RuntimeException("Job not found");
      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.error(error));

      var testObserver = athenaService.waitForJobCompletion(jobId).test();
      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldReturnErrorWhenNoQueryExecutionId() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.RUNNING)
          .queryExecutionId(null)
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));

      var testObserver = athenaService.waitForJobCompletion(jobId).test();
      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldHandleQueryCancelled() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.RUNNING)
          .queryExecutionId("exec-123")
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));
      when(athenaClient.waitForQueryCompletion(anyString())).thenReturn(Single.just(QueryExecutionState.CANCELLED));

      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .stateChangeReason("Query cancelled")
          .build();
      QueryExecution execution = QueryExecution.builder()
          .status(status)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobFailed(anyString(), anyString())).thenReturn(Single.just(true));

      AthenaJob failedJob = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.CANCELLED)
          .build();
      when(athenaJobDao.getJobById(anyString())).thenReturn(Single.just(failedJob));

      AthenaJob result = athenaService.waitForJobCompletion(jobId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.CANCELLED);
    }

    @Test
    void shouldHandleQueryFailedWithoutStateChangeReason() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.RUNNING)
          .queryExecutionId("exec-123")
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));
      when(athenaClient.waitForQueryCompletion(anyString())).thenReturn(Single.just(QueryExecutionState.FAILED));

      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .stateChangeReason(null)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .status(status)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobFailed(anyString(), anyString())).thenReturn(Single.just(true));

      AthenaJob failedJob = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.FAILED)
          .build();
      when(athenaJobDao.getJobById(anyString())).thenReturn(Single.just(failedJob));

      AthenaJob result = athenaService.waitForJobCompletion(jobId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.FAILED);
    }
  }

  @Nested
  class TestFetchResults {

    @Test
    void shouldHandleFetchResultsError() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String jobId = "job-123";
      String queryExecutionId = "exec-123";

      when(athenaJobDao.createJob(anyString())).thenReturn(Single.just(jobId));
      when(athenaClient.submitQuery(anyString(), any())).thenReturn(Single.just(queryExecutionId));
      
      QueryExecutionStatistics stats = QueryExecutionStatistics.builder()
          .dataScannedInBytes(1000L)
          .build();
      ResultConfiguration resultConfig = ResultConfiguration.builder()
          .outputLocation("s3://bucket/path")
          .build();
      QueryExecution execution = QueryExecution.builder()
          .statistics(stats)
          .resultConfiguration(resultConfig)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobWithExecutionId(anyString(), anyString(), any())).thenReturn(Single.just(true));
      when(athenaClient.getQueryStatus(anyString())).thenReturn(Single.just(QueryExecutionState.SUCCEEDED));
      when(athenaJobDao.updateJobCompleted(anyString(), anyString())).thenReturn(Single.just(true));
      when(athenaClient.getQueryResults(anyString(), anyInt(), isNull()))
          .thenReturn(Single.error(new RuntimeException("Failed to fetch results")));

      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .resultLocation("s3://bucket/path")
          .build();
      when(athenaJobDao.getJobById(anyString())).thenReturn(Single.just(job));

      AthenaJob result = athenaService.submitQuery(query, Collections.emptyList(), null)
          .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getResultData()).isNull();
    }

    @Test
    void shouldHandleGetJobStatusWithNullQueryExecutionId() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .queryExecutionId(null)
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));

      AthenaJob result = athenaService.getJobStatus(jobId, null, null).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.COMPLETED);
    }

    @Test
    void shouldHandleGetJobStatusWithFailedStatus() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.FAILED)
          .queryExecutionId("exec-123")
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));

      AthenaJob result = athenaService.getJobStatus(jobId, null, null).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.FAILED);
    }

    @Test
    void shouldHandleGetJobStatusWithCancelledStatus() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.CANCELLED)
          .queryExecutionId("exec-123")
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));

      AthenaJob result = athenaService.getJobStatus(jobId, null, null).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.CANCELLED);
    }

    @Test
    void shouldHandleGetJobStatusWhenStatusChangesToRunning() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.RUNNING)
          .queryExecutionId("exec-123")
          .build();

      AthenaJob updatedJob = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.RUNNING)
          .queryExecutionId("exec-123")
          .build();

      when(athenaJobDao.getJobById(anyString()))
          .thenReturn(Single.just(job))
          .thenReturn(Single.just(updatedJob));
      when(athenaClient.getQueryStatus(anyString())).thenReturn(Single.just(QueryExecutionState.RUNNING));
      when(athenaJobDao.updateJobStatus(anyString(), any())).thenReturn(Single.just(true));

      AthenaJob result = athenaService.getJobStatus(jobId, null, null).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.RUNNING);
    }

    @Test
    void shouldHandleFetchPaginatedResultsWithNullQueryExecutionId() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .queryExecutionId(null)
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));

      AthenaJob result = athenaService.getJobStatus(jobId, 100, null).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getQueryExecutionId()).isNull();
    }

    @Test
    void shouldHandleFetchPaginatedResultsWithMaxResultsGreaterThan1000() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .queryExecutionId("exec-123")
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));

      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(Row.builder()
              .data(Datum.builder().varCharValue("header").build())
              .build())
          .build();
      ResultSetWithToken resultSetWithToken = new ResultSetWithToken(resultSet, null);
      when(athenaClient.getQueryResults(anyString(), eq(1000), isNull()))
          .thenReturn(Single.just(resultSetWithToken));

      AthenaJob result = athenaService.getJobStatus(jobId, 2000, null).blockingGet();

      assertThat(result).isNotNull();
      verify(athenaClient).getQueryResults(anyString(), eq(1000), isNull());
    }

    @Test
    void shouldHandleFetchPaginatedResultsWithNextToken() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .queryExecutionId("exec-123")
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));

      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(Row.builder()
              .data(Datum.builder().varCharValue("header").build())
              .build())
          .build();
      ResultSetWithToken resultSetWithToken = new ResultSetWithToken(resultSet, "next-token");
      when(athenaClient.getQueryResults(anyString(), anyInt(), anyString()))
          .thenReturn(Single.just(resultSetWithToken));

      AthenaJob result = athenaService.getJobStatus(jobId, 100, "token-123").blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getNextToken()).isEqualTo("next-token");
    }

    @Test
    void shouldHandleWaitForJobCompletionWhenAlreadyCompleted() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .queryExecutionId("exec-123")
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));

      AthenaJob result = athenaService.waitForJobCompletion(jobId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.COMPLETED);
    }

    @Test
    void shouldHandleWaitForJobCompletionWhenAlreadyFailed() {
      String jobId = "job-123";
      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.FAILED)
          .queryExecutionId("exec-123")
          .build();

      when(athenaJobDao.getJobById(jobId)).thenReturn(Single.just(job));

      AthenaJob result = athenaService.waitForJobCompletion(jobId).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getStatus()).isEqualTo(AthenaJobStatus.FAILED);
    }

    @Test
    void shouldHandleSubmitQueryWithNullStatistics() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String jobId = "job-123";
      String queryExecutionId = "exec-123";

      when(athenaJobDao.createJob(anyString())).thenReturn(Single.just(jobId));
      when(athenaClient.submitQuery(anyString(), any())).thenReturn(Single.just(queryExecutionId));
      
      QueryExecution execution = QueryExecution.builder()
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobWithExecutionId(anyString(), anyString(), any())).thenReturn(Single.just(true));
      when(athenaClient.getQueryStatus(anyString())).thenReturn(Single.just(QueryExecutionState.RUNNING));

      AthenaJob job = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.RUNNING)
          .build();
      when(athenaJobDao.getJobById(anyString())).thenReturn(Single.just(job));

      AthenaJob result = athenaService.submitQuery(query, Collections.emptyList(), null)
          .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getDataScannedInBytes()).isNull();
    }

    @Test
    void shouldHandleSubmitQueryWithNullResultConfiguration() {
      String query = "SELECT * FROM pulse_athena_db.otel_data WHERE year = 2025 AND month = 12 AND day = 23 AND hour = 11";
      String jobId = "job-123";
      String queryExecutionId = "exec-123";

      when(athenaJobDao.createJob(anyString())).thenReturn(Single.just(jobId));
      when(athenaClient.submitQuery(anyString(), any())).thenReturn(Single.just(queryExecutionId));
      
      QueryExecutionStatistics stats = QueryExecutionStatistics.builder()
          .dataScannedInBytes(1000L)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .statistics(stats)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobWithExecutionId(anyString(), anyString(), any())).thenReturn(Single.just(true));
      when(athenaClient.getQueryStatus(anyString())).thenReturn(Single.just(QueryExecutionState.SUCCEEDED));
      when(athenaJobDao.updateJobCompleted(anyString(), isNull())).thenReturn(Single.just(true));

      ResultSetMetadata metadata = ResultSetMetadata.builder()
          .columnInfo(ColumnInfo.builder().name("col1").build())
          .build();
      ResultSet resultSet = ResultSet.builder()
          .resultSetMetadata(metadata)
          .rows(Row.builder()
              .data(Datum.builder().varCharValue("header").build())
              .build())
          .build();
      ResultSetWithToken resultSetWithToken = new ResultSetWithToken(resultSet, null);
      when(athenaClient.getQueryResults(anyString(), anyInt(), isNull())).thenReturn(Single.just(resultSetWithToken));

      AthenaJob completedJob = AthenaJob.builder()
          .jobId(jobId)
          .status(AthenaJobStatus.COMPLETED)
          .resultLocation(null)
          .build();
      when(athenaJobDao.getJobById(anyString())).thenReturn(Single.just(completedJob));

      AthenaJob result = athenaService.submitQuery(query, Collections.emptyList(), null)
          .blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getResultLocation()).isNull();
    }
  }
}

