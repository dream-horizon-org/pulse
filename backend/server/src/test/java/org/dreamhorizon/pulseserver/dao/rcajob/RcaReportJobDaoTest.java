package org.dreamhorizon.pulseserver.dao.rcajob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("unchecked")
class RcaReportJobDaoTest {

  private static final String JOB_ID = "rca-job-1";
  private static final String PROJECT = "p1";
  private static final String INTERACTION = "checkout";
  private static final LocalDate DATE = LocalDate.of(2025, 5, 1);

  @Mock
  private MysqlClient mysqlClient;

  @Mock
  private MySQLPool readerPool;

  @Mock
  private MySQLPool writerPool;

  @Mock
  private PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  private RowSet<Row> rowSet;

  private RcaReportJobDao dao;

  @BeforeEach
  void setUp() {
    dao = new RcaReportJobDao(mysqlClient);
  }

  private void setupReader() {
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupWriter() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private Row mockFullRow(RcaJobStatus status) {
    Row row = org.mockito.Mockito.mock(Row.class);
    when(row.getString(0)).thenReturn(JOB_ID);
    when(row.getString(1)).thenReturn(PROJECT);
    when(row.getString(2)).thenReturn(INTERACTION);
    when(row.getLocalDate(3)).thenReturn(DATE);
    when(row.getString(4)).thenReturn(status.name());
    when(row.getString(5)).thenReturn(null);
    when(row.getLocalDateTime(6)).thenReturn(LocalDateTime.of(2025, 5, 1, 10, 0, 0));
    when(row.getLocalDateTime(7)).thenReturn(LocalDateTime.of(2025, 5, 1, 10, 1, 0));
    when(row.getLocalDateTime(8)).thenReturn(null);
    when(row.getString(9)).thenReturn("user-1");
    when(row.getString(10)).thenReturn(null);
    when(row.getInteger(11)).thenReturn(2);
    return row;
  }

  @Nested
  class CreateJob {

    @Test
    void shouldInsertAndReturnPendingJob() {
      setupWriter();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RcaReportJob created = dao.createJob(JOB_ID, PROJECT, INTERACTION, DATE, "user-1").blockingGet();

      assertThat(created.jobId()).isEqualTo(JOB_ID);
      assertThat(created.projectId()).isEqualTo(PROJECT);
      assertThat(created.interactionName()).isEqualTo(INTERACTION);
      assertThat(created.date()).isEqualTo(DATE);
      assertThat(created.status()).isEqualTo(RcaJobStatus.PENDING);
      assertThat(created.createdBy()).isEqualTo("user-1");
      assertThat(created.version()).isEqualTo(1);
      verify(writerPool).preparedQuery(org.mockito.Mockito.contains("INSERT INTO rca_report_jobs"));
    }
  }

  @Nested
  class GetJobById {

    @Test
    void shouldReturnEmptyWhenNoRow() {
      setupReader();
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(iterator.hasNext()).thenReturn(false);
      when(rowSet.iterator()).thenReturn(iterator);
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RcaReportJob job = dao.getJobById(JOB_ID).blockingGet();

      assertThat(job).isNull();
    }

    @Test
    void shouldMapRowWhenPresent() {
      setupReader();
      Row row = mockFullRow(RcaJobStatus.PROCESSING);
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(iterator.hasNext()).thenReturn(true, false);
      when(iterator.next()).thenReturn(row);
      when(rowSet.iterator()).thenReturn(iterator);
      when(rowSet.size()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RcaReportJob job = dao.getJobById(JOB_ID).blockingGet();

      assertThat(job).isNotNull();
      assertThat(job.status()).isEqualTo(RcaJobStatus.PROCESSING);
      assertThat(job.createdAt())
          .isEqualTo(LocalDateTime.of(2025, 5, 1, 10, 0, 0).toInstant(ZoneOffset.UTC));
    }
  }

  @Nested
  class GetActiveJobByKey {

    @Test
    void shouldReturnEmptyWhenNoActiveJob() {
      setupReader();
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(iterator.hasNext()).thenReturn(false);
      when(rowSet.iterator()).thenReturn(iterator);
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RcaReportJob job =
          dao.getActiveJobByKey(PROJECT, INTERACTION, DATE).blockingGet();

      assertThat(job).isNull();
    }

    @Test
    void shouldReturnJobWhenRowPresent() {
      setupReader();
      Row row = mockFullRow(RcaJobStatus.PENDING);
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(iterator.hasNext()).thenReturn(true, false);
      when(iterator.next()).thenReturn(row);
      when(rowSet.iterator()).thenReturn(iterator);
      when(rowSet.size()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RcaReportJob job =
          dao.getActiveJobByKey(PROJECT, INTERACTION, DATE).blockingGet();

      assertThat(job).isNotNull();
      assertThat(job.status()).isEqualTo(RcaJobStatus.PENDING);
      verify(readerPool)
          .preparedQuery(org.mockito.Mockito.contains("status IN ('PENDING', 'PROCESSING')"));
    }
  }

  @Nested
  class Updates {

    @Test
    void shouldUpdateStatus() {
      setupWriter();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao.updateStatus(JOB_ID, RcaJobStatus.PROCESSING).blockingAwait();

      verify(writerPool).preparedQuery(org.mockito.Mockito.contains("UPDATE rca_report_jobs SET"));
    }

    @Test
    void shouldMarkCompleted() {
      setupWriter();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao.markCompleted(JOB_ID, PROJECT, INTERACTION, DATE).blockingAwait();

      verify(writerPool).preparedQuery(org.mockito.Mockito.contains("DELETE FROM rca_report_jobs"));
      verify(writerPool).preparedQuery(org.mockito.Mockito.contains("SET status = 'COMPLETED'"));
    }

    @Test
    void shouldMarkFailed() {
      setupWriter();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao.markFailed(JOB_ID, PROJECT, INTERACTION, DATE, "timeout").blockingAwait();

      verify(writerPool).preparedQuery(org.mockito.Mockito.contains("DELETE FROM rca_report_jobs"));
      verify(writerPool).preparedQuery(org.mockito.Mockito.contains("SET status = 'FAILED'"));
    }
  }

  @Nested
  class ListStaleJobIds {

    @Test
    void shouldCollectJobIds() {
      setupReader();
      Row r1 = org.mockito.Mockito.mock(Row.class);
      when(r1.getString(0)).thenReturn("a");
      Row r2 = org.mockito.Mockito.mock(Row.class);
      when(r2.getString(0)).thenReturn("b");
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(iterator.hasNext()).thenReturn(true, true, false);
      when(iterator.next()).thenReturn(r1, r2);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<String> ids = dao.listStaleJobIds().blockingGet();

      assertThat(ids).containsExactly("a", "b");
      verify(readerPool).preparedQuery(org.mockito.Mockito.contains("INTERVAL 2 HOUR"));
    }
  }
}
