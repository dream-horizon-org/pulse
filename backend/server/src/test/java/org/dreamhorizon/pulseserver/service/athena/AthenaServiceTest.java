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
import java.sql.Timestamp;
import java.time.Instant;
import org.dreamhorizon.pulseserver.client.athena.AthenaClient;
import org.dreamhorizon.pulseserver.client.athena.models.ResultSetWithToken;
import org.dreamhorizon.pulseserver.dao.athena.AthenaJobDao;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJob;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
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
      Instant submissionTime = Instant.now();
      Instant completionTime = submissionTime.plusSeconds(10);
      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .state(QueryExecutionState.SUCCEEDED)
          .submissionDateTime(submissionTime)
          .completionDateTime(completionTime)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .statistics(stats)
          .resultConfiguration(resultConfig)
          .status(status)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobCompleted(anyString(), anyString(), ArgumentMatchers.any(Timestamp.class))).thenReturn(Single.just(true));

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
      Instant submissionTime = Instant.now();
      Instant completionTime = submissionTime.plusSeconds(10);
      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .state(QueryExecutionState.SUCCEEDED)
          .submissionDateTime(submissionTime)
          .completionDateTime(completionTime)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .statistics(stats)
          .resultConfiguration(resultConfig)
          .status(status)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobCompleted(anyString(), anyString(), ArgumentMatchers.any(Timestamp.class))).thenReturn(Single.just(true));

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

      Instant submissionTime = Instant.now();
      Instant completionTime = submissionTime.plusSeconds(3);
      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .state(QueryExecutionState.CANCELLED)
          .stateChangeReason("Query cancelled")
          .submissionDateTime(submissionTime)
          .completionDateTime(completionTime)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .status(status)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobFailed(anyString(), anyString(), ArgumentMatchers.any(Timestamp.class))).thenReturn(Single.just(true));

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

      Instant submissionTime = Instant.now();
      Instant completionTime = submissionTime.plusSeconds(5);
      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .state(QueryExecutionState.FAILED)
          .stateChangeReason(null)
          .submissionDateTime(submissionTime)
          .completionDateTime(completionTime)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .status(status)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobFailed(anyString(), anyString(), ArgumentMatchers.any(Timestamp.class))).thenReturn(Single.just(true));

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
      Instant submissionTime = Instant.now();
      QueryExecutionStatus status = QueryExecutionStatus.builder()
          .state(QueryExecutionState.RUNNING)
          .submissionDateTime(submissionTime)
          .build();
      QueryExecution execution = QueryExecution.builder()
          .status(status)
          .build();
      when(athenaClient.getQueryExecution(anyString())).thenReturn(Single.just(execution));
      when(athenaJobDao.updateJobStatus(anyString(), any(), ArgumentMatchers.any(Timestamp.class))).thenReturn(Single.just(true));

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

  }
}

