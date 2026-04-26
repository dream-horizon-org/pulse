package org.dreamhorizon.pulseserver.dao.cronjobhistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Transaction;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
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
class CronJobHistoryDaoTest {

  private static final String STALE_MSG = "stale_in_progress_reclaimed";

  @Mock
  private MysqlClient mysqlClient;

  @Mock
  private MySQLPool writerPool;

  @Mock
  private SqlConnection sqlConnection;

  @Mock
  private Transaction transaction;

  private CronJobHistoryDao dao;

  @BeforeEach
  void setUp() {
    dao = new CronJobHistoryDao(mysqlClient);
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
  }

  @Nested
  class EnqueueOrDeduplicate {

    @Test
    void shouldReturnNewJobIdWhenInsertSucceeds() {
      PreparedQuery<RowSet<Row>> failStale = org.mockito.Mockito.mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insert = org.mockito.Mockito.mock(PreparedQuery.class);
      RowSet<Row> staleRows = org.mockito.Mockito.mock(RowSet.class);
      RowSet<Row> insertRows = org.mockito.Mockito.mock(RowSet.class);

      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.begin()).thenReturn(Single.just(transaction));
      when(sqlConnection.preparedQuery(argThat(s -> s != null && s.contains("UPDATE cron_jobs_history"))))
          .thenReturn(failStale);
      when(sqlConnection.preparedQuery(argThat(s -> s != null && s.contains("INSERT INTO cron_jobs_history"))))
          .thenReturn(insert);
      when(failStale.rxExecute(any(Tuple.class))).thenReturn(Single.just(staleRows));
      when(staleRows.rowCount()).thenReturn(2);
      when(insert.rxExecute(any(Tuple.class))).thenReturn(Single.just(insertRows));
      when(insertRows.rowCount()).thenReturn(1);
      when(insertRows.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(77L);
      when(transaction.rxCommit()).thenReturn(Completable.complete());

      CronJobEnqueueResult result =
          dao.enqueueOrDeduplicate("API_KEYS_TO_REDIS", Instant.now().minus(1, ChronoUnit.HOURS), STALE_MSG)
              .blockingGet();

      assertThat(result.getJobId()).isEqualTo(77L);
      assertThat(result.isDeduplicated()).isFalse();
      verify(transaction).rxCommit();
      verify(sqlConnection).close();
    }

    @Test
    void shouldReturnDeduplicatedWhenInsertSkippedAndActiveRowExists() {
      PreparedQuery<RowSet<Row>> failStale = org.mockito.Mockito.mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insert = org.mockito.Mockito.mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> select = org.mockito.Mockito.mock(PreparedQuery.class);
      RowSet<Row> staleRows = org.mockito.Mockito.mock(RowSet.class);
      RowSet<Row> insertRows = org.mockito.Mockito.mock(RowSet.class);
      RowSet<Row> selectRows = org.mockito.Mockito.mock(RowSet.class);
      RowIterator<Row> it = org.mockito.Mockito.mock(RowIterator.class);
      Row row = org.mockito.Mockito.mock(Row.class);

      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.begin()).thenReturn(Single.just(transaction));
      when(sqlConnection.preparedQuery(argThat(s -> s != null && s.contains("UPDATE cron_jobs_history"))))
          .thenReturn(failStale);
      when(sqlConnection.preparedQuery(argThat(s -> s != null && s.contains("INSERT INTO cron_jobs_history"))))
          .thenReturn(insert);
      when(sqlConnection.preparedQuery(argThat(s -> s != null && s.contains("SELECT id FROM cron_jobs_history"))))
          .thenReturn(select);
      when(failStale.rxExecute(any(Tuple.class))).thenReturn(Single.just(staleRows));
      when(staleRows.rowCount()).thenReturn(0);
      when(insert.rxExecute(any(Tuple.class))).thenReturn(Single.just(insertRows));
      when(insertRows.rowCount()).thenReturn(0);
      when(select.rxExecute(any(Tuple.class))).thenReturn(Single.just(selectRows));
      when(selectRows.iterator()).thenReturn(it);
      when(it.hasNext()).thenReturn(true);
      when(it.next()).thenReturn(row);
      when(row.getLong("id")).thenReturn(42L);
      when(transaction.rxCommit()).thenReturn(Completable.complete());

      CronJobEnqueueResult result =
          dao.enqueueOrDeduplicate("USAGE_CREDITS_TO_REDIS", Instant.now().minus(1, ChronoUnit.HOURS), STALE_MSG)
              .blockingGet();

      assertThat(result.getJobId()).isEqualTo(42L);
      assertThat(result.isDeduplicated()).isTrue();
      verify(transaction).rxCommit();
      verify(sqlConnection).close();
    }

    @Test
    void shouldErrorWhenInsertSkippedButNoActiveRow() {
      PreparedQuery<RowSet<Row>> failStale = org.mockito.Mockito.mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insert = org.mockito.Mockito.mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> select = org.mockito.Mockito.mock(PreparedQuery.class);
      RowSet<Row> staleRows = org.mockito.Mockito.mock(RowSet.class);
      RowSet<Row> insertRows = org.mockito.Mockito.mock(RowSet.class);
      RowSet<Row> selectRows = org.mockito.Mockito.mock(RowSet.class);
      RowIterator<Row> it = org.mockito.Mockito.mock(RowIterator.class);

      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.begin()).thenReturn(Single.just(transaction));
      when(sqlConnection.preparedQuery(argThat(s -> s != null && s.contains("UPDATE cron_jobs_history"))))
          .thenReturn(failStale);
      when(sqlConnection.preparedQuery(argThat(s -> s != null && s.contains("INSERT INTO cron_jobs_history"))))
          .thenReturn(insert);
      when(sqlConnection.preparedQuery(argThat(s -> s != null && s.contains("SELECT id FROM cron_jobs_history"))))
          .thenReturn(select);
      when(failStale.rxExecute(any(Tuple.class))).thenReturn(Single.just(staleRows));
      when(insert.rxExecute(any(Tuple.class))).thenReturn(Single.just(insertRows));
      when(staleRows.rowCount()).thenReturn(0);
      when(insertRows.rowCount()).thenReturn(0);
      when(select.rxExecute(any(Tuple.class))).thenReturn(Single.just(selectRows));
      when(selectRows.iterator()).thenReturn(it);
      when(it.hasNext()).thenReturn(false);
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      assertThatThrownBy(
              () ->
                  dao.enqueueOrDeduplicate("X", Instant.now().minus(1, ChronoUnit.HOURS), STALE_MSG).blockingGet())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Cron enqueue race");

      verify(transaction).rxRollback();
      verify(sqlConnection).close();
    }

    @Test
    void shouldRollbackWhenInsertThrows() {
      PreparedQuery<RowSet<Row>> failStale = org.mockito.Mockito.mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insert = org.mockito.Mockito.mock(PreparedQuery.class);
      RowSet<Row> staleRows = org.mockito.Mockito.mock(RowSet.class);

      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.begin()).thenReturn(Single.just(transaction));
      when(sqlConnection.preparedQuery(argThat(s -> s != null && s.contains("UPDATE cron_jobs_history"))))
          .thenReturn(failStale);
      when(sqlConnection.preparedQuery(argThat(s -> s != null && s.contains("INSERT INTO cron_jobs_history"))))
          .thenReturn(insert);
      when(failStale.rxExecute(any(Tuple.class))).thenReturn(Single.just(staleRows));
      when(staleRows.rowCount()).thenReturn(0);
      when(insert.rxExecute(any(Tuple.class))).thenReturn(Single.error(new RuntimeException("db down")));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      assertThatThrownBy(
              () -> dao.enqueueOrDeduplicate("Y", Instant.now().minus(1, ChronoUnit.HOURS), STALE_MSG).blockingGet())
          .hasMessageContaining("db down");

      verify(transaction).rxRollback();
      verify(sqlConnection).close();
    }
  }

  @Nested
  class MarkCompletedAndFailed {

    @Mock
    private PreparedQuery<RowSet<Row>> preparedQuery;

    @Test
    void shouldMarkCompleted() {
      when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(org.mockito.Mockito.mock(RowSet.class)));

      dao.markCompleted(5L).blockingAwait();

      verify(writerPool).preparedQuery(org.mockito.Mockito.contains("COMPLETED"));
      verify(preparedQuery)
          .rxExecute(
              argThat(
                  t ->
                      t.size() == 1
                          && t.getLong(0) == 5L));
    }

    @Test
    void shouldMarkFailedWithTruncatedMessage() {
      when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(org.mockito.Mockito.mock(RowSet.class)));

      String longMsg = "e".repeat(9000);
      dao.markFailed(3L, longMsg).blockingAwait();

      verify(preparedQuery).rxExecute(argThat(t -> t.getString(0).length() == 8000));
    }

    @Test
    void shouldMarkFailedWithNullMessage() {
      when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(org.mockito.Mockito.mock(RowSet.class)));

      dao.markFailed(2L, null).blockingAwait();

      verify(preparedQuery).rxExecute(argThat(t -> t.getString(0) == null));
    }
  }
}
