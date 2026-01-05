package org.dreamhorizon.pulseserver.dao.athena;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
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
  }

  @Nested
  class TestCreateJob {

    @Test
    void shouldCreateJobSuccessfully() {
      String queryString = "SELECT * FROM table";
      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      String jobId = athenaJobDao.createJob(queryString).blockingGet();

      assertThat(jobId).isNotNull();
      assertThat(jobId).isNotEmpty();

      ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
      verify(writerPool).preparedQuery(queryCaptor.capture());
      assertThat(queryCaptor.getValue()).isEqualTo(AthenaJobQueries.CREATE_JOB);

      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      assertThat(tupleCaptor.getValue().getString(1)).isEqualTo(queryString);
    }

    @Test
    void shouldPropagateErrorWhenCreateJobFails() {
      String queryString = "SELECT * FROM table";
      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      RuntimeException error = new RuntimeException("Database error");
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.error(error));

      var testObserver = athenaJobDao.createJob(queryString).test();
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

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobWithExecutionId(jobId, queryExecutionId, status)
          .blockingGet();

      assertThat(result).isTrue();

      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      assertThat(tupleCaptor.getValue().getString(0)).isEqualTo(queryExecutionId);
      assertThat(tupleCaptor.getValue().getString(1)).isEqualTo(status.name());
      assertThat(tupleCaptor.getValue().getString(2)).isEqualTo(jobId);
    }
  }

  @Nested
  class TestUpdateJobStatus {

    @Test
    void shouldUpdateJobStatusSuccessfully() {
      String jobId = "job-123";
      AthenaJobStatus status = AthenaJobStatus.COMPLETED;

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobStatus(jobId, status).blockingGet();

      assertThat(result).isTrue();
    }
  }

  @Nested
  class TestUpdateJobCompleted {

    @Test
    void shouldUpdateJobCompletedSuccessfully() {
      String jobId = "job-123";
      String resultLocation = "s3://bucket/path";

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobCompleted(jobId, resultLocation).blockingGet();

      assertThat(result).isTrue();

      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(tupleCaptor.capture());
      assertThat(tupleCaptor.getValue().getString(0)).isEqualTo(resultLocation);
      assertThat(tupleCaptor.getValue().getString(1)).isEqualTo(jobId);
    }
  }

  @Nested
  class TestUpdateJobFailed {

    @Test
    void shouldUpdateJobFailedSuccessfully() {
      String jobId = "job-123";
      String errorMessage = "Query failed";

      when(writerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = athenaJobDao.updateJobFailed(jobId, errorMessage).blockingGet();

      assertThat(result).isTrue();
    }
  }

  @Nested
  class TestGetJobById {

    @Test
    void shouldGetJobByIdSuccessfully() {
      String jobId = "job-123";
      LocalDateTime now = LocalDateTime.now();

      when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
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
    void shouldReturnNullWhenJobNotFound() {
      String jobId = "job-123";

      when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.size()).thenReturn(0);

      AthenaJob job = athenaJobDao.getJobById(jobId).blockingGet();

      assertThat(job).isNull();
    }

    @Test
    void shouldHandleJobWithCompletedAt() {
      String jobId = "job-123";
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime completedAt = now.plusHours(1);

      when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
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

      when(readerPool.preparedQuery(any(String.class))).thenReturn(preparedQuery);
      RuntimeException error = new RuntimeException("Database error");
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.error(error));

      var testObserver = athenaJobDao.getJobById(jobId).test();
      testObserver.assertError(Throwable.class);
    }
  }
}

