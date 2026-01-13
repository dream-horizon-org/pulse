package org.dreamhorizon.pulseserver.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.sql.Timestamp;
import org.dreamhorizon.pulseserver.client.query.QueryClient;
import org.dreamhorizon.pulseserver.client.query.models.QueryExecutionInfo;
import org.dreamhorizon.pulseserver.client.query.models.QueryResultSet;
import org.dreamhorizon.pulseserver.client.query.models.QueryStatus;
import org.dreamhorizon.pulseserver.config.AthenaConfig;
import org.dreamhorizon.pulseserver.dao.query.QueryJobDao;
import org.dreamhorizon.pulseserver.service.query.models.QueryJob;
import org.dreamhorizon.pulseserver.service.query.models.QueryJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
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

  @Mock
  AthenaConfig athenaConfig;

  QueryServiceImpl queryService;

  @BeforeEach
  void setUp() {
    when(athenaConfig.getDatabase()).thenReturn("test_database");
    queryService = new QueryServiceImpl(queryClient, queryJobDao, athenaConfig);
  }

  @Test
  void shouldRejectQueryWithoutTimestampFilter() {
    String query = "SELECT * FROM pulse_athena_db.otel_data WHERE column1 = 'value'";

    var testObserver = queryService.submitQuery(query, "test@example.com").test();

    testObserver.assertError(IllegalArgumentException.class);
    testObserver.assertError(error -> error.getMessage().contains("timestamp filter"));
    verify(queryJobDao, never()).createJob(anyString(), anyString());
  }

  @Test
  void shouldRejectNonSelectQuery() {
    String query = "INSERT INTO table VALUES (1, 'test')";

    var testObserver = queryService.submitQuery(query, "test@example.com").test();

    testObserver.assertError(IllegalArgumentException.class);
    testObserver.assertError(error -> error.getMessage().contains("SELECT"));
    verify(queryJobDao, never()).createJob(anyString(), anyString());
  }

  @Test
  void shouldRejectQueryWithoutWhereClause() {
    String query = "SELECT * FROM pulse_athena_db.otel_data";

    var testObserver = queryService.submitQuery(query, "test@example.com").test();

    testObserver.assertError(IllegalArgumentException.class);
    testObserver.assertError(error -> error.getMessage().contains("timestamp filter"));
    verify(queryJobDao, never()).createJob(anyString(), anyString());
  }

  @Test
  void shouldAcceptQueryWithTimestampLiteral() {
    String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'";
    String jobId = "job-123";
    String queryExecutionId = "exec-123";

    Timestamp submissionTime = new Timestamp(System.currentTimeMillis());
    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.RUNNING)
        .submissionDateTime(submissionTime)
        .build();

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString(query)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .createdAt(submissionTime)
        .build();

    when(queryJobDao.createJob(anyString(), anyString())).thenReturn(Single.just(jobId));
    when(queryClient.submitQuery(anyString())).thenReturn(Single.just(queryExecutionId));
    when(queryClient.getQueryExecution(queryExecutionId)).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobWithExecutionId(anyString(), anyString(), any(QueryJobStatus.class), ArgumentMatchers.any(Timestamp.class)))
        .thenReturn(Single.just(true));
    when(queryClient.getQueryStatus(queryExecutionId)).thenReturn(Single.just(QueryStatus.RUNNING));
    when(queryJobDao.getJobById(jobId)).thenReturn(Single.just(job));

    QueryJob result = queryService.submitQuery(query, "test@example.com").blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getJobId()).isEqualTo(jobId);
    verify(queryJobDao).createJob(eq(query), eq("test@example.com"));
    verify(queryClient).submitQuery(eq(query));
  }

  @Test
  void shouldSubmitQuerySuccessfully() {
    String query = "SELECT * FROM pulse_athena_db.otel_data WHERE timestamp >= TIMESTAMP '2025-12-23 11:00:00' AND column1 = 'value'";
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Long dataScannedBytes = 1000L;

    Timestamp submissionTime = new Timestamp(System.currentTimeMillis());
    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .dataScannedInBytes(dataScannedBytes)
        .status(QueryStatus.RUNNING)
        .submissionDateTime(submissionTime)
        .build();

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString(query)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .dataScannedInBytes(dataScannedBytes)
        .createdAt(submissionTime)
        .build();

    when(queryJobDao.createJob(anyString(), anyString())).thenReturn(Single.just(jobId));
    when(queryClient.submitQuery(anyString())).thenReturn(Single.just(queryExecutionId));
    when(queryClient.getQueryExecution(queryExecutionId)).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobWithExecutionId(anyString(), anyString(), any(QueryJobStatus.class), ArgumentMatchers.any(Timestamp.class)))
        .thenReturn(Single.just(true));
    when(queryClient.getQueryStatus(queryExecutionId)).thenReturn(Single.just(QueryStatus.RUNNING));
    when(queryJobDao.getJobById(jobId)).thenReturn(Single.just(job));

    QueryJob result = queryService.submitQuery(query, "test@example.com").blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getJobId()).isEqualTo(jobId);
    verify(queryJobDao).createJob(anyString(), anyString());
    verify(queryClient).submitQuery(anyString());
  }

  @Test
  void shouldHandleQueryFailure() {
    String query = "SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00' AND column1 = 'value'";
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

    Timestamp submissionTime = new Timestamp(System.currentTimeMillis());
    Timestamp completionTime = new Timestamp(System.currentTimeMillis() + 5000);
    QueryExecutionInfo executionInfoWithTimestamps = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.FAILED)
        .stateChangeReason("Query failed")
        .submissionDateTime(submissionTime)
        .completionDateTime(completionTime)
        .build();
    when(queryJobDao.createJob(anyString(), anyString())).thenReturn(Single.just(jobId));
    when(queryClient.submitQuery(anyString())).thenReturn(Single.just(queryExecutionId));
    when(queryClient.getQueryExecution(queryExecutionId))
        .thenReturn(Single.just(executionInfoWithTimestamps))
        .thenReturn(Single.just(executionInfoWithTimestamps));
    when(queryJobDao.updateJobWithExecutionId(anyString(), anyString(), any(QueryJobStatus.class), ArgumentMatchers.any(Timestamp.class)))
        .thenReturn(Single.just(true));
    when(queryClient.getQueryStatus(queryExecutionId)).thenReturn(Single.just(QueryStatus.FAILED));
    when(queryJobDao.updateJobFailed(eq(jobId), eq("Query failed"), any(Timestamp.class)))
        .thenReturn(Single.just(true));
    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(job));

    QueryJob result = queryService.submitQuery(query, "test@example.com").blockingGet();

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
        .queryString("SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'")
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
        .queryString("SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'")
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    Timestamp completionTime = new Timestamp(System.currentTimeMillis() + 10000);
    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.SUCCEEDED)
        .resultLocation(resultLocation)
        .dataScannedInBytes(1000L)
        .completionDateTime(completionTime)
        .build();

    QueryJob updatedJob = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'")
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.COMPLETED)
        .resultLocation(resultLocation)
        .dataScannedInBytes(1000L)
        .createdAt(now)
        .updatedAt(completionTime)
        .completedAt(completionTime)
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(updatedJob));
    when(queryClient.getQueryStatus(queryExecutionId)).thenReturn(Single.just(QueryStatus.SUCCEEDED));
    when(queryClient.getQueryExecution(queryExecutionId)).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobCompleted(
        eq(jobId),
        eq(resultLocation),
        any(Timestamp.class)
    )).thenReturn(Single.just(true));

    QueryJob result = queryService.getJobStatus(jobId, null, null).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
    verify(queryJobDao).updateJobCompleted(eq(jobId), eq(resultLocation), ArgumentMatchers.any(Timestamp.class));
  }

  @Test
  void shouldFetchPaginatedResults() {
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'")
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
        .queryString("SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'")
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
        .queryString("SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'")
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    Timestamp completionTime = new Timestamp(System.currentTimeMillis() + 10000);
    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.SUCCEEDED)
        .resultLocation(resultLocation)
        .completionDateTime(completionTime)
        .build();

    QueryJob completedJob = QueryJob.builder()
        .jobId(jobId)
        .queryString("SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'")
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.COMPLETED)
        .resultLocation(resultLocation)
        .createdAt(now)
        .updatedAt(completionTime)
        .completedAt(completionTime)
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(completedJob));
    when(queryClient.waitForQueryCompletion(queryExecutionId))
        .thenReturn(Single.just(QueryStatus.SUCCEEDED));
    when(queryClient.getQueryExecution(queryExecutionId)).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobCompleted(
        eq(jobId),
        eq(resultLocation),
        any(Timestamp.class)
    )).thenReturn(Single.just(true));

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
        .queryString("SELECT * FROM pulse_athena_db.otel_data WHERE \"timestamp\" >= TIMESTAMP '2025-12-23 11:00:00'")
        .queryExecutionId(null)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .updatedAt(now)
        .build();

    when(queryJobDao.getJobById(jobId)).thenReturn(Single.just(job));

    var testObserver = queryService.waitForJobCompletion(jobId).test();

    testObserver.assertError(RuntimeException.class);
  }

  @Test
  void shouldGetTablesAndColumnsSuccessfully() {
    String tablesQueryExecutionId = "tables-exec-123";
    String columnsQueryExecutionId = "columns-exec-123";

    JsonArray tablesResult = new JsonArray();
    JsonObject table1 = new JsonObject();
    table1.put("table_schema", "test_database");
    table1.put("table_name", "table1");
    table1.put("table_type", "BASE TABLE");
    tablesResult.add(table1);

    JsonObject table2 = new JsonObject();
    table2.put("table_schema", "test_database");
    table2.put("table_name", "table2");
    table2.put("table_type", "BASE TABLE");
    tablesResult.add(table2);

    JsonArray columnsResult = new JsonArray();
    JsonObject col1 = new JsonObject();
    col1.put("table_schema", "test_database");
    col1.put("table_name", "table1");
    col1.put("column_name", "id");
    col1.put("data_type", "varchar");
    col1.put("ordinal_position", "1");
    col1.put("is_nullable", "YES");
    columnsResult.add(col1);

    JsonObject col2 = new JsonObject();
    col2.put("table_schema", "test_database");
    col2.put("table_name", "table1");
    col2.put("column_name", "name");
    col2.put("data_type", "varchar");
    col2.put("ordinal_position", "2");
    col2.put("is_nullable", "NO");
    columnsResult.add(col2);

    QueryResultSet tablesResultSet = QueryResultSet.builder()
        .resultData(tablesResult)
        .build();

    QueryResultSet columnsResultSet = QueryResultSet.builder()
        .resultData(columnsResult)
        .build();

    when(queryClient.submitQuery(anyString()))
        .thenReturn(Single.just(tablesQueryExecutionId))
        .thenReturn(Single.just(columnsQueryExecutionId));
    when(queryClient.waitForQueryCompletion(tablesQueryExecutionId))
        .thenReturn(Single.just(QueryStatus.SUCCEEDED));
    when(queryClient.waitForQueryCompletion(columnsQueryExecutionId))
        .thenReturn(Single.just(QueryStatus.SUCCEEDED));
    when(queryClient.getQueryResults(eq(tablesQueryExecutionId), isNull(), isNull()))
        .thenReturn(Single.just(tablesResultSet));
    when(queryClient.getQueryResults(eq(columnsQueryExecutionId), isNull(), isNull()))
        .thenReturn(Single.just(columnsResultSet));

    var result = queryService.getTablesAndColumns().blockingGet();

    assertThat(result).isNotNull();
    assertThat(result).hasSize(2);
    assertThat(result.get(0).getTableName()).isEqualTo("table1");
    assertThat(result.get(0).getColumns()).hasSize(2);
    assertThat(result.get(0).getColumns().get(0).getColumnName()).isEqualTo("id");
    assertThat(result.get(0).getColumns().get(1).getColumnName()).isEqualTo("name");
    assertThat(result.get(1).getTableName()).isEqualTo("table2");
    assertThat(result.get(1).getColumns()).isEmpty();
  }

  @Test
  void shouldHandleErrorWhenDatabaseNotConfigured() {
    when(athenaConfig.getDatabase()).thenReturn(null);

    QueryServiceImpl serviceWithNullDb = new QueryServiceImpl(queryClient, queryJobDao, athenaConfig);

    var testObserver = serviceWithNullDb.getTablesAndColumns().test();

    testObserver.assertError(IllegalArgumentException.class);
    testObserver.assertError(error -> error.getMessage().contains("Database name is not configured"));
  }

  @Test
  void shouldHandleErrorWhenTablesQueryFails() {
    String tablesQueryExecutionId = "tables-exec-123";

    when(queryClient.submitQuery(anyString()))
        .thenReturn(Single.just(tablesQueryExecutionId));
    when(queryClient.waitForQueryCompletion(tablesQueryExecutionId))
        .thenReturn(Single.just(QueryStatus.FAILED));

    var testObserver = queryService.getTablesAndColumns().test();

    testObserver.assertError(RuntimeException.class);
    testObserver.assertError(error -> error.getMessage().contains("Failed to query tables"));
  }

  @Test
  void shouldHandleErrorWhenColumnsQueryFails() {
    String tablesQueryExecutionId = "tables-exec-123";
    String columnsQueryExecutionId = "columns-exec-123";

    JsonArray tablesResult = new JsonArray();
    JsonObject table1 = new JsonObject();
    table1.put("table_schema", "test_database");
    table1.put("table_name", "table1");
    table1.put("table_type", "BASE TABLE");
    tablesResult.add(table1);

    QueryResultSet tablesResultSet = QueryResultSet.builder()
        .resultData(tablesResult)
        .build();

    when(queryClient.submitQuery(anyString()))
        .thenReturn(Single.just(tablesQueryExecutionId))
        .thenReturn(Single.just(columnsQueryExecutionId));
    when(queryClient.waitForQueryCompletion(tablesQueryExecutionId))
        .thenReturn(Single.just(QueryStatus.SUCCEEDED));
    when(queryClient.waitForQueryCompletion(columnsQueryExecutionId))
        .thenReturn(Single.just(QueryStatus.FAILED));
    when(queryClient.getQueryResults(eq(tablesQueryExecutionId), isNull(), isNull()))
        .thenReturn(Single.just(tablesResultSet));

    var testObserver = queryService.getTablesAndColumns().test();

    testObserver.assertError(RuntimeException.class);
    testObserver.assertError(error -> error.getMessage().contains("Failed to query columns"));
  }

  @Test
  void shouldGetQueryHistoryAndUpdateRunningQueries() {
    String userEmail = "test@example.com";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob runningJob1 = QueryJob.builder()
        .jobId("job-1")
        .queryExecutionId("exec-1")
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    QueryJob runningJob2 = QueryJob.builder()
        .jobId("job-2")
        .queryExecutionId("exec-2")
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    QueryJob completedJob = QueryJob.builder()
        .jobId("job-3")
        .status(QueryJobStatus.COMPLETED)
        .createdAt(now)
        .build();

    QueryJob updatedJob1 = QueryJob.builder()
        .jobId("job-1")
        .queryExecutionId("exec-1")
        .status(QueryJobStatus.COMPLETED)
        .createdAt(now)
        .build();


    when(queryJobDao.getQueryHistory(userEmail, 20, 0))
        .thenReturn(Single.just(java.util.Arrays.asList(runningJob1, runningJob2, completedJob)));
    when(queryClient.getQueryStatus("exec-1")).thenReturn(Single.just(QueryStatus.SUCCEEDED));
    when(queryClient.getQueryStatus("exec-2")).thenReturn(Single.just(QueryStatus.RUNNING));
    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId("exec-1")
        .status(QueryStatus.SUCCEEDED)
        .resultLocation("s3://bucket/path")
        .completionDateTime(now)
        .build();
    when(queryClient.getQueryExecution("exec-1")).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobCompleted(eq("job-1"), anyString(), any(Timestamp.class)))
        .thenReturn(Single.just(true));
    when(queryJobDao.updateJobStatistics(eq("job-1"), any(), any(), any(), any(), any(Timestamp.class)))
        .thenReturn(Single.just(true));
    when(queryJobDao.getJobById("job-1"))
        .thenReturn(Single.just(updatedJob1));

    var result = queryService.getQueryHistory(userEmail, 20, 0).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result).hasSize(3);
    verify(queryClient).getQueryStatus("exec-1");
    verify(queryClient).getQueryStatus("exec-2");
  }

  @Test
  void shouldGetQueryHistoryWithNoRunningQueries() {
    String userEmail = "test@example.com";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob completedJob = QueryJob.builder()
        .jobId("job-1")
        .status(QueryJobStatus.COMPLETED)
        .createdAt(now)
        .build();

    when(queryJobDao.getQueryHistory(userEmail, 20, 0))
        .thenReturn(Single.just(java.util.Arrays.asList(completedJob)));

    var result = queryService.getQueryHistory(userEmail, 20, 0).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
    verify(queryClient, never()).getQueryStatus(anyString());
  }

  @Test
  void shouldHandleErrorWhenCheckingRunningQueryStatus() {
    String userEmail = "test@example.com";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob runningJob = QueryJob.builder()
        .jobId("job-1")
        .queryExecutionId("exec-1")
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    when(queryJobDao.getQueryHistory(userEmail, 20, 0))
        .thenReturn(Single.just(java.util.Arrays.asList(runningJob)));
    when(queryClient.getQueryStatus("exec-1"))
        .thenReturn(Single.error(new RuntimeException("AWS error")));

    var result = queryService.getQueryHistory(userEmail, 20, 0).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStatus()).isEqualTo(QueryJobStatus.RUNNING);
  }

  @Test
  void shouldLimitRunningQueriesTo20() {
    String userEmail = "test@example.com";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    java.util.List<QueryJob> jobs = new java.util.ArrayList<>();
    for (int i = 0; i < 25; i++) {
      jobs.add(QueryJob.builder()
          .jobId("job-" + i)
          .queryExecutionId("exec-" + i)
          .status(QueryJobStatus.RUNNING)
          .createdAt(now)
          .build());
    }

    when(queryJobDao.getQueryHistory(userEmail, 20, 0))
        .thenReturn(Single.just(jobs));
    when(queryClient.getQueryStatus(anyString())).thenReturn(Single.just(QueryStatus.RUNNING));

    var result = queryService.getQueryHistory(userEmail, 20, 0).blockingGet();

    assertThat(result).isNotNull();
    verify(queryClient, org.mockito.Mockito.times(20)).getQueryStatus(anyString());
  }

  @Test
  void shouldCancelQuerySuccessfully() {
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    QueryJob cancelledJob = QueryJob.builder()
        .jobId(jobId)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.CANCELLED)
        .createdAt(now)
        .build();

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.CANCELLED)
        .completionDateTime(now)
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(cancelledJob));
    when(queryClient.cancelQuery(queryExecutionId)).thenReturn(Single.just(true));
    when(queryClient.getQueryExecution(queryExecutionId)).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobStatus(eq(jobId), eq(QueryJobStatus.CANCELLED), any(Timestamp.class)))
        .thenReturn(Single.just(true));

    QueryJob result = queryService.cancelQuery(jobId).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.CANCELLED);
    verify(queryClient).cancelQuery(queryExecutionId);
  }

  @Test
  void shouldCancelQueryWithNoExecutionId() {
    String jobId = "job-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryExecutionId(null)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    QueryJob cancelledJob = QueryJob.builder()
        .jobId(jobId)
        .status(QueryJobStatus.CANCELLED)
        .createdAt(now)
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(cancelledJob));
    when(queryJobDao.updateJobStatus(eq(jobId), eq(QueryJobStatus.CANCELLED), any(Timestamp.class)))
        .thenReturn(Single.just(true));

    QueryJob result = queryService.cancelQuery(jobId).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.CANCELLED);
    verify(queryClient, never()).cancelQuery(anyString());
  }

  @Test
  void shouldNotCancelQueryInFinalState() {
    String jobId = "job-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .status(QueryJobStatus.COMPLETED)
        .createdAt(now)
        .build();

    when(queryJobDao.getJobById(jobId)).thenReturn(Single.just(job));

    QueryJob result = queryService.cancelQuery(jobId).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.COMPLETED);
    verify(queryClient, never()).cancelQuery(anyString());
  }

  @Test
  void shouldHandleCancelQueryFailure() {
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.CANCELLED)
        .completionDateTime(now)
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(job));
    when(queryClient.cancelQuery(queryExecutionId)).thenReturn(Single.just(false));
    when(queryClient.getQueryExecution(queryExecutionId)).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobStatus(eq(jobId), eq(QueryJobStatus.CANCELLED), any(Timestamp.class)))
        .thenReturn(Single.just(true));

    QueryJob result = queryService.cancelQuery(jobId).blockingGet();

    assertThat(result).isNotNull();
    verify(queryClient).cancelQuery(queryExecutionId);
  }

  @Test
  void shouldHandleCancelQueryError() {
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    QueryJob cancelledJob = QueryJob.builder()
        .jobId(jobId)
        .status(QueryJobStatus.CANCELLED)
        .createdAt(now)
        .build();

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.CANCELLED)
        .completionDateTime(now)
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(cancelledJob));
    when(queryClient.cancelQuery(queryExecutionId))
        .thenReturn(Single.error(new RuntimeException("Cancel failed")));
    when(queryClient.getQueryExecution(queryExecutionId)).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobStatus(eq(jobId), eq(QueryJobStatus.CANCELLED), any(Timestamp.class)))
        .thenReturn(Single.just(true));

    QueryJob result = queryService.cancelQuery(jobId).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.CANCELLED);
  }

  @Test
  void shouldHandleCancelQueryWithErrorGettingExecution() {
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    QueryJob cancelledJob = QueryJob.builder()
        .jobId(jobId)
        .status(QueryJobStatus.CANCELLED)
        .createdAt(now)
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(cancelledJob));
    when(queryClient.cancelQuery(queryExecutionId)).thenReturn(Single.just(true));
    when(queryClient.getQueryExecution(queryExecutionId))
        .thenReturn(Single.error(new RuntimeException("Get execution failed")));
    when(queryJobDao.updateJobStatus(eq(jobId), eq(QueryJobStatus.CANCELLED), any(Timestamp.class)))
        .thenReturn(Single.just(true));

    QueryJob result = queryService.cancelQuery(jobId).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.CANCELLED);
  }

  @Test
  void shouldHandleCheckAndUpdateRunningJobWithFailedStatus() {
    String userEmail = "test@example.com";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob runningJob = QueryJob.builder()
        .jobId("job-1")
        .queryExecutionId("exec-1")
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    QueryJob failedJob = QueryJob.builder()
        .jobId("job-1")
        .queryExecutionId("exec-1")
        .status(QueryJobStatus.FAILED)
        .errorMessage("Query failed")
        .createdAt(now)
        .build();

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId("exec-1")
        .status(QueryStatus.FAILED)
        .stateChangeReason("Query failed")
        .completionDateTime(now)
        .build();

    when(queryJobDao.getQueryHistory(userEmail, 20, 0))
        .thenReturn(Single.just(java.util.Arrays.asList(runningJob)));
    when(queryClient.getQueryStatus("exec-1")).thenReturn(Single.just(QueryStatus.FAILED));
    when(queryClient.getQueryExecution("exec-1")).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobFailed(eq("job-1"), anyString(), any(Timestamp.class)))
        .thenReturn(Single.just(true));
    when(queryJobDao.getJobById("job-1"))
        .thenReturn(Single.just(failedJob));

    var result = queryService.getQueryHistory(userEmail, 20, 0).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getStatus()).isEqualTo(QueryJobStatus.FAILED);
  }

  @Test
  void shouldHandleCheckAndUpdateRunningJobWithOtherStatus() {
    String userEmail = "test@example.com";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob runningJob = QueryJob.builder()
        .jobId("job-1")
        .queryExecutionId("exec-1")
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    QueryJob queuedJob = QueryJob.builder()
        .jobId("job-1")
        .queryExecutionId("exec-1")
        .status(QueryJobStatus.SUBMITTED)
        .createdAt(now)
        .build();

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId("exec-1")
        .status(QueryStatus.QUEUED)
        .submissionDateTime(now)
        .build();

    when(queryJobDao.getQueryHistory(userEmail, 20, 0))
        .thenReturn(Single.just(java.util.Arrays.asList(runningJob)));
    when(queryClient.getQueryStatus("exec-1")).thenReturn(Single.just(QueryStatus.QUEUED));
    when(queryClient.getQueryExecution("exec-1")).thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobStatus(eq("job-1"), eq(QueryJobStatus.SUBMITTED), any(Timestamp.class)))
        .thenReturn(Single.just(true));
    when(queryJobDao.getJobById("job-1"))
        .thenReturn(Single.just(queuedJob));

    var result = queryService.getQueryHistory(userEmail, 20, 0).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result).hasSize(1);
  }

  @Test
  void shouldHandleFetchAndUpdateJobResultsError() {
    String jobId = "job-123";
    String queryExecutionId = "exec-123";
    Timestamp now = new Timestamp(System.currentTimeMillis());

    QueryJob job = QueryJob.builder()
        .jobId(jobId)
        .queryExecutionId(queryExecutionId)
        .status(QueryJobStatus.RUNNING)
        .createdAt(now)
        .build();

    QueryJob failedJob = QueryJob.builder()
        .jobId(jobId)
        .status(QueryJobStatus.FAILED)
        .errorMessage("Failed to update result location")
        .createdAt(now)
        .build();

    QueryExecutionInfo executionInfo = QueryExecutionInfo.builder()
        .queryExecutionId(queryExecutionId)
        .status(QueryStatus.SUCCEEDED)
        .resultLocation("s3://bucket/path")
        .completionDateTime(now)
        .build();

    when(queryJobDao.getJobById(jobId))
        .thenReturn(Single.just(job))
        .thenReturn(Single.just(failedJob));
    when(queryClient.getQueryStatus(queryExecutionId)).thenReturn(Single.just(QueryStatus.SUCCEEDED));
    when(queryClient.getQueryExecution(queryExecutionId))
        .thenReturn(Single.just(executionInfo))
        .thenReturn(Single.just(executionInfo));
    when(queryJobDao.updateJobCompleted(eq(jobId), anyString(), any(Timestamp.class)))
        .thenReturn(Single.error(new RuntimeException("Update failed")));
    when(queryJobDao.updateJobFailed(eq(jobId), anyString(), any(Timestamp.class)))
        .thenReturn(Single.just(true));

    QueryJob result = queryService.getJobStatus(jobId, null, null).blockingGet();

    assertThat(result).isNotNull();
    assertThat(result.getStatus()).isEqualTo(QueryJobStatus.FAILED);
  }
}
