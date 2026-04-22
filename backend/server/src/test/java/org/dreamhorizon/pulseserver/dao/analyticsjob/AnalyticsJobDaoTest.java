package org.dreamhorizon.pulseserver.dao.analyticsjob;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
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
class AnalyticsJobDaoTest {

  private static final String TEST_TENANT = "test-project";

  @Mock
  MysqlClient mysqlClient;

  @Mock
  MySQLPool writerPool;

  @Mock
  PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  RowSet<Row> rowSet;

  AnalyticsJobDao dao;

  @BeforeEach
  void setup() {
    TenantContext.setTenantId(TEST_TENANT);
    dao = new AnalyticsJobDao(mysqlClient);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private void setupWriterPreparedQuery() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private RowIterator<Row> iteratorOf(List<Row> rows) {
    RowIterator<Row> it = mock(RowIterator.class);
    if (rows.isEmpty()) {
      when(it.hasNext()).thenReturn(false);
    } else {
      final int[] i = {0};
      when(it.hasNext()).thenAnswer(inv -> i[0] < rows.size());
      when(it.next()).thenAnswer(inv -> rows.get(i[0]++));
    }
    return it;
  }

  private Row createJobRow() {
    Row row = mock(Row.class);
    when(row.getLong("id")).thenReturn(1L);
    when(row.getString("job_type")).thenReturn("FUNNEL");
    when(row.getLong("reference_id")).thenReturn(10L);
    when(row.getString("job_id")).thenReturn("emr-job-123");
    when(row.getString("status")).thenReturn("RUNNING");
    when(row.getString("error_message")).thenReturn(null);
    LocalDateTime now = LocalDateTime.of(2025, 6, 15, 10, 0);
    when(row.getLocalDateTime("started_at")).thenReturn(now);
    when(row.getLocalDateTime("completed_at")).thenReturn(null);
    when(row.getLocalDateTime("created_at")).thenReturn(now);
    return row;
  }

  @Nested
  class InsertJob {
    @Test
    void shouldInsertAndReturnId() {
      setupWriterPreparedQuery();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(99L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Long result = dao.insertJob(
          AnalyticsJobType.FUNNEL, 10L, "emr-1", AnalyticsJobStatus.PENDING).blockingGet();

      assertEquals(99L, result);
    }

    @Test
    void shouldPropagateError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB")));

      assertThrows(RuntimeException.class, () ->
          dao.insertJob(AnalyticsJobType.JOURNEY, 1L, null, AnalyticsJobStatus.PENDING)
              .blockingGet());
    }
  }

  @Nested
  class UpdateJobIdAndStatus {
    @Test
    void shouldReturnRowCount() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = dao.updateJobIdAndStatus(5L, "emr-2", AnalyticsJobStatus.RUNNING)
          .blockingGet();

      assertEquals(1, result);
    }

    @Test
    void shouldPropagateError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("err")));

      assertThrows(RuntimeException.class, () ->
          dao.updateJobIdAndStatus(5L, "emr-2", AnalyticsJobStatus.RUNNING).blockingGet());
    }
  }

  @Nested
  class UpdateJobStatus {
    @Test
    void shouldReturnRowCount() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      LocalDateTime now = LocalDateTime.of(2025, 1, 1, 0, 0);
      Integer result = dao.updateJobStatus(
          1L, AnalyticsJobStatus.SUCCEEDED, null, now, now).blockingGet();

      assertEquals(1, result);
    }

    @Test
    void shouldUpdateJobStatusByJobId() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(2);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      LocalDateTime now = LocalDateTime.of(2025, 1, 1, 0, 0);
      Integer result = dao.updateJobStatusByJobId(
          "emr-1", AnalyticsJobStatus.FAILED, "err", now, now).blockingGet();

      assertEquals(2, result);
    }

    @Test
    void shouldPropagateError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("err")));

      assertThrows(RuntimeException.class, () ->
          dao.updateJobStatusByJobId("x", AnalyticsJobStatus.FAILED, null, null, null)
              .blockingGet());
    }
  }

  @Nested
  class GetJobById {
    @Test
    void shouldReturnEntity() {
      setupWriterPreparedQuery();
      when(rowSet.iterator()).thenReturn(iteratorOf(Collections.singletonList(createJobRow())));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      AnalyticsJobEntity result = dao.getJobById(1L).blockingGet();

      assertNotNull(result);
      assertEquals(1L, result.getId());
      assertEquals(AnalyticsJobType.FUNNEL, result.getJobType());
      assertEquals(10L, result.getReferenceId());
      assertEquals("emr-job-123", result.getJobId());
      assertEquals(AnalyticsJobStatus.RUNNING, result.getStatus());
    }

    @Test
    void shouldReturnEmptyWhenNoRow() {
      setupWriterPreparedQuery();
      when(rowSet.iterator()).thenReturn(iteratorOf(Collections.emptyList()));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertFalse(dao.getJobById(1L).isEmpty().blockingGet() == Boolean.FALSE);
    }
  }

  @Nested
  class GetJobByJobId {
    @Test
    void shouldReturnEntity() {
      setupWriterPreparedQuery();
      when(rowSet.iterator()).thenReturn(iteratorOf(Collections.singletonList(createJobRow())));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      AnalyticsJobEntity result = dao.getJobByJobId("emr-job-123").blockingGet();

      assertNotNull(result);
      assertEquals("emr-job-123", result.getJobId());
    }

    @Test
    void shouldReturnEmptyWhenNoRow() {
      setupWriterPreparedQuery();
      when(rowSet.iterator()).thenReturn(iteratorOf(Collections.emptyList()));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      AnalyticsJobEntity result = dao.getJobByJobId("missing").blockingGet();
      // Maybe.empty => null
      assertEquals(null, result);
    }
  }

  @Nested
  class GetLatestJobByReference {
    @Test
    void shouldReturnEntity() {
      setupWriterPreparedQuery();
      when(rowSet.iterator()).thenReturn(iteratorOf(Collections.singletonList(createJobRow())));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      AnalyticsJobEntity result = dao.getLatestJobByReference(
          AnalyticsJobType.FUNNEL, 10L).blockingGet();

      assertNotNull(result);
      assertEquals(10L, result.getReferenceId());
    }

    @Test
    void shouldReturnEmptyWhenNoRow() {
      setupWriterPreparedQuery();
      when(rowSet.iterator()).thenReturn(iteratorOf(Collections.emptyList()));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      AnalyticsJobEntity result = dao.getLatestJobByReference(
          AnalyticsJobType.JOURNEY, 99L).blockingGet();
      assertEquals(null, result);
    }
  }

  @Nested
  class GetLatestJobByType {
    @Test
    void shouldReturnEntity() {
      setupWriterPreparedQuery();
      when(rowSet.iterator()).thenReturn(iteratorOf(Collections.singletonList(createJobRow())));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      AnalyticsJobEntity result = dao.getLatestJobByType(AnalyticsJobType.FUNNELS_DAILY)
          .blockingGet();

      assertNotNull(result);
    }

    @Test
    void shouldReturnEmptyWhenNoRow() {
      setupWriterPreparedQuery();
      when(rowSet.iterator()).thenReturn(iteratorOf(Collections.emptyList()));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      AnalyticsJobEntity result = dao.getLatestJobByType(AnalyticsJobType.EVENTS_INCREMENTAL)
          .blockingGet();
      assertEquals(null, result);
    }
  }
}
