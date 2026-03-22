package org.dreamhorizon.pulseserver.dao.rcareport;

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
class RcaReportCacheDaoTest {

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

  private RcaReportCacheDao dao;

  @BeforeEach
  void setUp() {
    dao = new RcaReportCacheDao(mysqlClient);
  }

  private void setupReader() {
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupWriter() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  @Nested
  class Get {

    @Test
    void shouldReturnEmptyWhenNoRow() {
      setupReader();
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(iterator.hasNext()).thenReturn(false);
      when(rowSet.iterator()).thenReturn(iterator);
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      String body =
          dao.get(PROJECT, INTERACTION, DATE).blockingGet();

      assertThat(body).isNull();
    }

    @Test
    void shouldReturnReportBodyWhenRowPresent() {
      setupReader();
      Row row = org.mockito.Mockito.mock(Row.class);
      when(row.getString(0)).thenReturn("{\"report\":1}");
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(iterator.hasNext()).thenReturn(true, false);
      when(iterator.next()).thenReturn(row);
      when(rowSet.iterator()).thenReturn(iterator);
      when(rowSet.size()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      String body =
          dao.get(PROJECT, INTERACTION, DATE).blockingGet();

      assertThat(body).isEqualTo("{\"report\":1}");
    }
  }

  @Nested
  class Put {

    @Test
    void shouldSkipWriteWhenBodyBlank() {
      setupWriter();

      dao.put(PROJECT, INTERACTION, DATE, "   ").blockingAwait();

      verify(writerPool, org.mockito.Mockito.never()).preparedQuery(anyString());
    }

    @Test
    void shouldUpsertWhenBodyPresent() {
      setupWriter();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(rowSet.size()).thenReturn(0);

      dao.put(PROJECT, INTERACTION, DATE, "{\"x\":1}").blockingAwait();

      verify(writerPool).preparedQuery(org.mockito.Mockito.contains("INSERT INTO rca_report_cache"));
    }
  }
}
