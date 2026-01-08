package org.dreamhorizon.pulseserver.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import java.sql.Timestamp;
import java.util.Collections;
import org.dreamhorizon.pulseserver.client.query.QueryClient;
import org.dreamhorizon.pulseserver.client.query.models.QueryExecutionInfo;
import org.dreamhorizon.pulseserver.client.query.models.QueryResultSet;
import org.dreamhorizon.pulseserver.client.query.models.QueryStatus;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.query.QueryJobDao;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class QueryServiceImplTest {

  @Mock
  QueryClient queryClient;

  @Mock
  QueryJobDao queryJobDao;

  QueryServiceImpl queryService;

  @BeforeEach
  void setUp() {
    queryService = new QueryServiceImpl(queryClient, queryJobDao);
  }

  @Test
  void shouldRejectInvalidQuery() {
    String invalidQuery = "INVALID QUERY";

    var testObserver = queryService.submitQuery(invalidQuery, Collections.emptyList(), null).test();

    testObserver.assertError(IllegalArgumentException.class);
    verify(queryJobDao, never()).createJob(anyString());
  }

  @Test
  void shouldSubmitQuerySuccessfully() {
    String query = "SELECT * FROM table WHERE year = 2025 AND month = 1 AND day = 1 AND hour = 1";
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Long dataScannedBytes = 1000L;

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .dataScannedInBytes(dataScannedBytes)
        .status(QueryStatus.RUNNING)
        .build();

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString(query)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .dataScannedInBytes(dataScannedBytes)
        .createdAt(new Timestamp(System.currentTimeMillis()))
        .build();

    when(queryJobDao.createJob(anyString())).thenReturn(Single.just(jobId));
    when(queryClient.submitQuery(anyString(), anyList())).thenReturn(Single.just(queryExecutionId));
    when(queryClient.getQueryExecution(queryExecutionId)).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobWithExecutionId(anyString(), anyString(), any(QueryJobStatus.class)))
        .thenReturn(Single.just(true));
    when(queryClient.getQueryStatus(queryExecutionId)).thenReturn(Single.just(QueryStatus.RUNNING));
    when(queryJobDao.getJobById(jobId)).thenReturn(Single.just(job));

    QueryJob result = queryService.submitQuery(query, Collections.emptyList(), null).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getJobId()).isEqualTo(jobId);
    verify(queryJobDao).createJob(anyString());
    verify(queryClient).submitQuery(anyString(), anyList());
  }

  @Test
  void shouldHandleQueryCompletionWithinTimeout() {
    String query = "SELECT * FROM table WHERE year = 2025 AND month = 1 AND day = 1 AND hour = 1";
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Long dataScannedBytes = 1000L;
    String resultLocation = "s3://bucket/path";

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .dataScannedInBytes(dataScannedBytes)
        .status(QueryStatus.SUCCEEDED)
        .resultLocation(resultLocation)
        .build();

    JsonArray resultData = new JsonArray();
    resultData.add(new io.vertx.core.json.JsonObject().put("col1", "value1"));

    QueryResultSet resultSet = QueryResultSet.builder()
        .resultData(resultData)
        .nextToken(null)
        .build();

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString(query)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.COMPLETED)
        .resultLocation(resultLocation)
        .dataScannedInBytes(dataScannedBytes)
        .resultData(resultData)
        .createdAt(new Timestamp(System.currentTimeMillis()))
        .completedAt(new Timestamp(System.currentTimeMillis()))
        .build();

    when(queryJobDao.createJob(anyString())).thenReturn(Single.just(jobId));
    when(queryClient.submitQuery(anyString(), anyList())).thenReturn(Single.just(queryExecutionId));
    when(queryClient.getQueryExecution(queryExecutionId))
        .thenReturn(Single.just(executionInfo))
        .thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobWithExecutionId(anyString(), anyString(), any(QueryJobStatus.class)))
        .thenReturn(Single.just(true));
    when(queryClient.getQueryStatus(queryExecutionId)).thenReturn(Single.just(QueryStatus.SUCCEEDED));
    when(queryJobDao.updateJobCompleted(jobId, resultLocation)).thenReturn(Single.just(true));
    when(queryClient.getQueryResults(eq(queryExecutionId), eq(Constants.MAX_QUERY_RESULTS), isNull()))
        .thenReturn(Single.just(resultSet));
    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(job));

    QueryJob result = queryService.submitQuery(query, Collections.emptyList(), null).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
    assertThat(result.getResultData()).isNotNull();
  }

  @Test
  void shouldHandleQueryFailure() {
    String query = "SELECT * FROM table WHERE year = 2025 AND month = 1 AND day = 1 AND hour = 1";
    String jobId = "job-123";
    String queryExecutionId = "exec-123";

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.FAILED)
        .stateChangeReason("Query failed")
        .build();

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString(query)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.FAILED)
        .errorMessage("Query failed")
        .createdAt(new Timestamp(System.currentTimeMillis()))
        .build();

    when(queryJobDao.createJob(anyString())).thenReturn(Single.just(jobId));
    when(queryClient.submitQuery(anyString(), anyList())).thenReturn(Single.just(queryExecutionId));
    when(queryClient.getQueryExecution(queryExecutionId))
        .thenReturn(Single.just(executionInfo))
        .thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobWithExecutionId(anyString(), anyString(), any(QueryJobStatus.class)))
        .thenReturn(Single.just(true));
    when(queryClient.getQueryStatus(queryExecutionId)).thenReturn(Single.just(QueryStatus.FAILED));
    when(queryJobDao.updateJobFailed(jobId, "Query failed")).thenReturn(Single.just(true));
    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(job));

    QueryJob result = queryService.submitQuery(query, Collections.emptyList(), null).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.FAILED);
    assertThat(result.getErrorMessage()).isEqualTo("Query failed");
  }

  @Test
  void shouldReturnJobNotFoundError() {
    String jobId = "job-123";

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.error(new RuntimeException("Job not found")));

    var testObserver = queryService.getJobStatus(jobId, null, null).test();

    testObserver.assertError(RuntimeException.class);
  }

  @Test
  void shouldReturnJobInFinalState() {
    String jobId = "job-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM table")
        .status(QueryJobStatus.COMPLETED)
        .createdAt(now)
        .updatedAt(now)
        .build();

    when(queryJobDao.getJobById(jobId)).thenReturn(Single.just(job));

    QueryJob result = queryService.getJobStatus(jobId, null, null).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
  }

  @Test
  void shouldUpdateJobStatusWhenChanged() {
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    String resultLocation = "s3://bucket/path";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM table")
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.SUCCEEDED)
        .resultLocation(resultLocation)
        .dataScannedInBytes(1000L)
        .build();

    QueryJob updatedJob = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM table")
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.COMPLETED)
        .resultLocation(resultLocation)
        .dataScannedInBytes(1000L)
        .createdAt(now)
        .updatedAt(now)
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(updatedJob));
    when(queryClient.getQueryStatus(queryExecutionId)).thenReturn(Single.just(QueryStatus.SUCCEEDED));
    when(queryClient.getQueryExecution(queryExecutionId)).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobCompleted(jobId, resultLocation)).thenReturn(Single.just(true));

    QueryJob result = queryService.getJobStatus(jobId, null, null).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
    verify(queryJobDao).updateJobCompleted(eq(jobId), eq(resultLocation));
  }

  @Test
  void shouldFetchPaginatedResults() {
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM table")
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.COMPLETED)
        .createdAt(now)
        .updatedAt(now)
        .build();

    JsonArray resultData = new JsonArray();
    resultData.add(new io.vertx.core.json.JsonObject().put("col1", "value1"));

    QueryResultSet resultSet = QueryResultSet.builder()
        .resultData(resultData)
        .nextToken("next-token")
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(job));
    when(queryClient.getQueryResults(eq(queryExecutionId), eq(101), isNull()))
        .thenReturn(Single.just(resultSet));

    QueryJob result = queryService.getJobStatus(jobId, 100, null).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getResultData()).isNotNull();
    assertThat(result.getNextToken()).isEqualTo("next-token");
  }

  @Test
  void shouldReturnJobNotFoundErrorInWaitForCompletion() {
    String jobId = "job-123";

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.error(new RuntimeException("Job not found")));

    var testObserver = queryService.waitForJobCompletion(jobId).test();

    testObserver.assertError(RuntimeException.class);
  }

  @Test
  void shouldReturnJobAlreadyInFinalState() {
    String jobId = "job-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM table")
        .status(QueryJobStatus.COMPLETED)
        .createdAt(now)
        .updatedAt(now)
        .build();

    when(queryJobDao.getJobById(jobId)).thenReturn(Single.just(job));

    QueryJob result = queryService.waitForJobCompletion(jobId).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
  }

  @Test
  void shouldWaitForCompletion() {
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());
    String resultLocation = "s3://bucket/path";

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM table")
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.SUCCEEDED)
        .resultLocation(resultLocation)
        .build();

    QueryJob completedJob = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM table")
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.COMPLETED)
        .resultLocation(resultLocation)
        .createdAt(now)
        .updatedAt(now)
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(completedJob));
    when(queryClient.waitForQueryCompletion(queryExecutionId))
        .thenReturn(Single.just(QueryStatus.SUCCEEDED));
    when(queryClient.getQueryExecution(queryExecutionId)).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobCompleted(jobId, resultLocation)).thenReturn(Single.just(true));

    QueryJob result = queryService.waitForJobCompletion(jobId).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
    verify(queryClient).waitForQueryCompletion(eq(queryExecutionId));
  }

  @Test
  void shouldHandleNoExecutionId() {
    String jobId = "job-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM table")
        .queryExecutionId(null)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    when(queryJobDao.getJobById(jobId)).thenReturn(Single.just(job));

    var testObserver = queryService.waitForJobCompletion(jobId).test();

    testObserver.assertError(RuntimeException.class);
  }
}
