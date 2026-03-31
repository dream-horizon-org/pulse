package org.dreamhorizon.pulseserver.dao.athena;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.context.ProjectContext;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJob;
import org.dreamhorizon.pulseserver.service.athena.models.AthenaJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AthenaJobDaoTest {

  @Mock
  MysqlClient mysqlClient;

  @Mock
  MySQLPool writerPool;

  @Mock
  MySQLPool readerPool;

  @Mock
  PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  RowSet<Row> rowSet;

  @Mock
  Row row;

  @Mock
  RowIterator<Row> rowIterator;

  AthenaJobDao athenaJobDao;

  @BeforeEach
  void setUp() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    athenaJobDao = new AthenaJobDao(mysqlClient);
    ProjectContext.setProjectId("test-project");
  }

  @Nested
  class TestCreateJob {

    @Test
    void shouldCreateJobSuccessfully() {
      String projectId = "test-project";
      String queryString = "SELECT * FROM table";
      String userEmail = "test@example.com";
      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      String jobId = athenaJobDao.createJob(projectId, queryString, userEmail).blockingGet();

      assertThat(jobId).isNotNull();
      assertThat(jobId).isNotEmpty();

      ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
      verify(writerPool).preparedQuery(queryCaptor.capture());
      assertThat(queryCaptor.getValue()).isEqualTo(AthenaJobQueries.CREATE_JOB);

      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      Tuple capturedTuple = tupleCaptor.getValue();
      assertThat(capturedTuple.getString(0)).isEqualTo(jobId);
      assertThat(capturedTuple.getString(1)).isEqualTo(projectId);
      assertThat(capturedTuple.getString(2)).isEqualTo(queryString);
      assertThat(capturedTuple.getString(3)).isEqualTo(userEmail);
    }

    @Test
    void shouldPropagateErrorWhenCreateJobFails() {
      String queryString = "SELECT * FROM table";
      String userEmail = "test@example.com";
      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      RuntimeException error = new RuntimeException("Database error");
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.error(error));

      var testObserver = athenaJobDao.createJob("test-project", queryString, userEmail).test();
      testObserver.assertError(Throwable.class);
    }
  }

  @Nested
  class TestUpdateJobWithExecutionId {

    @Test
    void shouldUpdateJobWithExecutionIdSuccessfully() {
      String jobId = "job-123";
      String queryExecutionId = "exec-123";
      AthenaJobStatus status = AthenaJobStatus.RUNNING;
      Timestamp submissionDateTime = new Timestamp(System.currentTimeMillis());

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobWithExecutionId(jobId, queryExecutionId, status, submissionDateTime)
          .blockingGet();

      assertThat(result).isTrue();

      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      Tuple capturedTuple = tupleCaptor.getValue();
      assertThat(capturedTuple.getString(0)).isEqualTo(queryExecutionId);
      assertThat(capturedTuple.getString(1)).isEqualTo(status.name());
      assertThat((Timestamp) capturedTuple.getValue(2)).isEqualTo(submissionDateTime);
      assertThat((Timestamp) capturedTuple.getValue(3)).isNotNull();
      assertThat(capturedTuple.getString(4)).isEqualTo(jobId);
    }
  }

  @Nested
  class TestUpdateJobStatus {

    @Test
    void shouldUpdateJobStatusSuccessfully() {
      String jobId = "job-123";
      AthenaJobStatus status = AthenaJobStatus.COMPLETED;
      Timestamp updatedAt = new Timestamp(System.currentTimeMillis());

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobStatus(jobId, status, updatedAt).blockingGet();

      assertThat(result).isTrue();
    }
  }

  @Nested
  class TestUpdateJobCompleted {

    @Test
    void shouldUpdateJobCompletedSuccessfully() {
      String jobId = "job-123";
      String resultLocation = "s3://bucket/path";
      Timestamp completionDateTime = new Timestamp(System.currentTimeMillis());

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobCompleted(jobId, resultLocation, completionDateTime).blockingGet();

      assertThat(result).isTrue();

      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      Tuple capturedTuple = tupleCaptor.getValue();
      assertThat(capturedTuple.getString(0)).isEqualTo(resultLocation);
      assertThat((Timestamp) capturedTuple.getValue(1)).isEqualTo(completionDateTime);
      assertThat((Timestamp) capturedTuple.getValue(2)).isNotNull();
      assertThat(capturedTuple.getString(3)).isEqualTo(jobId);
    }
  }

  @Nested
  class TestUpdateJobFailed {

    @Test
    void shouldUpdateJobFailedSuccessfully() {
      String jobId = "job-123";
      String errorMessage = "Query failed";
      Timestamp completionDateTime = new Timestamp(System.currentTimeMillis());

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobFailed(jobId, errorMessage, completionDateTime).blockingGet();

      assertThat(result).isTrue();
    }
  }

  @Nested
  class TestGetJobById {

    @Test
    void shouldGetJobByIdSuccessfully() {
      String jobId = "job-123";
      LocalDateTime now = LocalDateTime.now();

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.hasNext()).thenReturn(true, false);
      when(rowIterator.next()).thenReturn(row);

      when(row.getString("job_id")).thenReturn(jobId);
      when(row.getString("query_string")).thenReturn("SELECT * FROM table");
      when(row.getString("query_execution_id")).thenReturn("exec-123");
      when(row.getString("status")).thenReturn("RUNNING");
      when(row.getString("result_location")).thenReturn("s3://bucket/path");
      when(row.getString("error_message")).thenReturn(null);
      when(row.getLocalDateTime("created_at")).thenReturn(now);
      when(row.getLocalDateTime("updated_at")).thenReturn(now);
      when(row.getLocalDateTime("completed_at")).thenReturn(null);

      AthenaJob job = athenaJobDao.getJobById(jobId).blockingGet();

      assertThat(job).isNotNull();
      assertThat(job.getJobId()).isEqualTo(jobId);
      assertThat(job.getStatus()).isEqualTo(AthenaJobStatus.RUNNING);
      assertThat(job.getResultData()).isNull();
    }

    @Test
    void shouldThrowWhenJobNotFound() {
      String jobId = "job-123";

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.size()).thenReturn(0);

      assertThrows(RuntimeException.class, () -> athenaJobDao.getJobById(jobId).blockingGet());
    }

    @Test
    void shouldHandleJobWithCompletedAt() {
      String jobId = "job-123";
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime completedAt = now.plusHours(1);

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.hasNext()).thenReturn(true, false);
      when(rowIterator.next()).thenReturn(row);

      when(row.getString("job_id")).thenReturn(jobId);
      when(row.getString("query_string")).thenReturn("SELECT * FROM table");
      when(row.getString("query_execution_id")).thenReturn("exec-123");
      when(row.getString("status")).thenReturn("COMPLETED");
      when(row.getString("result_location")).thenReturn("s3://bucket/path");
      when(row.getString("error_message")).thenReturn(null);
      when(row.getLocalDateTime("created_at")).thenReturn(now);
      when(row.getLocalDateTime("updated_at")).thenReturn(now);
      when(row.getLocalDateTime("completed_at")).thenReturn(completedAt);

      AthenaJob job = athenaJobDao.getJobById(jobId).blockingGet();

      assertThat(job).isNotNull();
      assertThat(job.getCompletedAt()).isNotNull();
      assertThat(job.getCompletedAt()).isEqualTo(Timestamp.valueOf(completedAt));
    }

    @Test
    void shouldPropagateErrorWhenGetJobFails() {
      String jobId = "job-123";

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      RuntimeException error = new RuntimeException("Database error");
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.error(error));

      var testObserver = athenaJobDao.getJobById(jobId).test();
      testObserver.assertError(Throwable.class);
    }

    @Test
    void shouldMapRowWithNullTimestampsAndLongValues() {
      String jobId = "job-123";
      LocalDateTime now = LocalDateTime.now();

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.hasNext()).thenReturn(true, false);
      when(rowIterator.next()).thenReturn(row);

      when(row.getString("job_id")).thenReturn(jobId);
      when(row.getString("project_id")).thenReturn("test-project");
      when(row.getString("query_string")).thenReturn("SELECT 1");
      when(row.getString("user_email")).thenReturn("user@test.com");
      when(row.getString("query_execution_id")).thenReturn("exec-1");
      when(row.getString("status")).thenReturn("COMPLETED");
      when(row.getString("result_location")).thenReturn("s3://bucket/");
      when(row.getString("error_message")).thenReturn(null);
      when(row.getLocalDateTime("created_at")).thenReturn(now);
      when(row.getLocalDateTime("updated_at")).thenReturn(now);
      when(row.getLocalDateTime("completed_at")).thenReturn(null);
      when(row.getLong("data_scanned_in_bytes")).thenThrow(new RuntimeException("not a long"));
      when(row.getLong("execution_time_millis")).thenThrow(new RuntimeException("not a long"));

      AthenaJob job = athenaJobDao.getJobById(jobId).blockingGet();

      assertThat(job).isNotNull();
      assertThat(job.getDataScannedInBytes()).isNull();
      assertThat(job.getExecutionTimeMillis()).isNull();
      assertThat(job.getCompletedAt()).isNull();
    }
  }

  @Nested
  class TestUpdateJobStatistics {

    @Test
    void shouldUpdateJobStatisticsSuccessfully() {
      String jobId = "job-123";
      Long dataScanned = 1024L;
      Long executionTime = 500L;
      Long engineTime = 400L;
      Long queueTime = 100L;
      Timestamp updatedAt = new Timestamp(System.currentTimeMillis());

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobStatistics(
          jobId, dataScanned, executionTime, engineTime, queueTime, updatedAt).blockingGet();

      assertThat(result).isTrue();

      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      Tuple captured = tupleCaptor.getValue();
      assertThat(captured.getLong(0)).isEqualTo(dataScanned);
      assertThat(captured.getLong(1)).isEqualTo(executionTime);
      assertThat(captured.getString(5)).isEqualTo(jobId);
    }

    @Test
    void shouldUseCurrentTimeWhenUpdatedAtIsNull() {
      String jobId = "job-123";
      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobStatistics(
          jobId, 100L, 200L, 150L, 50L, null).blockingGet();

      assertThat(result).isTrue();
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      assertThat(tupleCaptor.getValue().getValue(4)).isNotNull();
    }
  }

  @Nested
  class TestUpdateJobWithNullSubmissionDateTime {

    @Test
    void shouldUseCurrentTimeWhenSubmissionDateTimeIsNull() {
      String jobId = "job-123";
      String queryExecutionId = "exec-123";
      AthenaJobStatus status = AthenaJobStatus.RUNNING;

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobWithExecutionId(
          jobId, queryExecutionId, status, null).blockingGet();

      assertThat(result).isTrue();
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      Tuple captured = tupleCaptor.getValue();
      assertThat(captured.getValue(2)).isNull();
      assertThat(captured.getValue(3)).isNotNull();
    }
  }

  @Nested
  class TestUpdateJobStatusWithNull {

    @Test
    void shouldUseCurrentTimeWhenUpdatedAtIsNull() {
      String jobId = "job-123";
      AthenaJobStatus status = AthenaJobStatus.FAILED;

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobStatus(jobId, status, null).blockingGet();

      assertThat(result).isTrue();
    }
  }

  @Nested
  class TestUpdateJobCompletedWithNull {

    @Test
    void shouldUseCurrentTimeWhenCompletionDateTimeIsNull() {
      String jobId = "job-123";
      String resultLocation = "s3://bucket/result";

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobCompleted(jobId, resultLocation, null).blockingGet();

      assertThat(result).isTrue();
    }
  }

  @Nested
  class TestUpdateJobFailedWithNull {

    @Test
    void shouldUseCurrentTimeWhenCompletionDateTimeIsNull() {
      String jobId = "job-123";
      String errorMessage = "Query failed";

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobFailed(jobId, errorMessage, null).blockingGet();

      assertThat(result).isTrue();
    }
  }

  @Nested
  class TestGetQueryHistory {

    @Test
    void shouldGetQueryHistorySuccessfully() {
      String userEmail = "user@test.com";
      Integer limit = 10;
      Integer offset = 0;
      LocalDateTime now = LocalDateTime.now();

      when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.hasNext()).thenReturn(true, false);
      when(rowIterator.next()).thenReturn(row);

      when(row.getString("job_id")).thenReturn("job-1");
      when(row.getString("project_id")).thenReturn("test-project");
      when(row.getString("query_string")).thenReturn("SELECT * FROM t");
      when(row.getString("user_email")).thenReturn(userEmail);
      when(row.getString("query_execution_id")).thenReturn("exec-1");
      when(row.getString("status")).thenReturn("COMPLETED");
      when(row.getString("result_location")).thenReturn(null);
      when(row.getString("error_message")).thenReturn(null);
      when(row.getLocalDateTime("created_at")).thenReturn(now);
      when(row.getLocalDateTime("updated_at")).thenReturn(now);
      when(row.getLocalDateTime("completed_at")).thenReturn(now);

      List<AthenaJob> jobs = athenaJobDao.getQueryHistory(userEmail, limit, offset).blockingGet();

      assertThat(jobs).hasSize(1);
      assertThat(jobs.get(0).getJobId()).isEqualTo("job-1");
      assertThat(jobs.get(0).getQueryString()).isEqualTo("SELECT * FROM t");

      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      assertThat(tupleCaptor.getValue().getString(0)).isEqualTo("test-project");
      assertThat(tupleCaptor.getValue().getString(1)).isEqualTo(userEmail);
    }
  }

  @Nested
  class TestGetQueriesForStatistics {

    @Test
    void shouldGetQueriesForStatisticsSuccessfully() {
      String userEmail = "user@test.com";
      LocalDateTime start = LocalDateTime.now().minusDays(7);
      LocalDateTime end = LocalDateTime.now();

      when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.iterator()).thenReturn(rowIterator);
      when(rowIterator.hasNext()).thenReturn(false);

      List<AthenaJob> jobs = athenaJobDao.getQueriesForStatistics(userEmail, start, end).blockingGet();

      assertThat(jobs).isEmpty();

      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      assertThat(tupleCaptor.getValue().getString(0)).isEqualTo("test-project");
    }
  }
}
