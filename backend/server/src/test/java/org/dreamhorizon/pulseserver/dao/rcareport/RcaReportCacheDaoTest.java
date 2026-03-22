package org.dreamhorizon.pulseserver.dao.rcareport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.dreamhorizon.pulseserver.dao.rcareport.models.RcaReportCacheHit;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
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
  private RootCauseConfig rootCauseConfig;

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
    when(rootCauseConfig.getCacheTtlHours()).thenReturn(24);
    dao = new RcaReportCacheDao(mysqlClient, rootCauseConfig);
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

      RcaReportCacheHit hit =
          dao.get(PROJECT, INTERACTION, DATE).blockingGet();

      assertThat(hit).isNull();
    }

    @Test
    void shouldReturnReportBodyWhenRowPresent() {
      setupReader();
      Row row = org.mockito.Mockito.mock(Row.class);
      when(row.getString(0)).thenReturn("{\"report\":1}");
      when(row.getLocalDateTime(1)).thenReturn(LocalDateTime.of(2025, 5, 1, 12, 0, 0));
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(iterator.hasNext()).thenReturn(true, false);
      when(iterator.next()).thenReturn(row);
      when(rowSet.iterator()).thenReturn(iterator);
      when(rowSet.size()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RcaReportCacheHit hit =
          dao.get(PROJECT, INTERACTION, DATE).blockingGet();

      assertThat(hit.reportBody()).isEqualTo("{\"report\":1}");
      assertThat(hit.cachedAt())
          .isEqualTo(LocalDateTime.of(2025, 5, 1, 12, 0, 0).toInstant(ZoneOffset.UTC));
    }

    @Test
    void shouldReturnEmptyWhenCachedBodyIsBlank() {
      setupReader();
      Row row = org.mockito.Mockito.mock(Row.class);
      when(row.getString(0)).thenReturn("   ");
      when(row.getLocalDateTime(1)).thenReturn(LocalDateTime.of(2025, 1, 1, 0, 0));
      RowIterator<Row> iterator = org.mockito.Mockito.mock(RowIterator.class);
      when(iterator.hasNext()).thenReturn(true, false);
      when(iterator.next()).thenReturn(row);
      when(rowSet.iterator()).thenReturn(iterator);
      when(rowSet.size()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RcaReportCacheHit hit = dao.get(PROJECT, INTERACTION, DATE).blockingGet();

      assertThat(hit).isNull();
    }

    @Test
    void shouldPropagateErrorWhenReaderFails() {
      setupReader();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("db down")));

      TestObserver<RcaReportCacheHit> observer = dao.get(PROJECT, INTERACTION, DATE).test();

      observer.assertError(RuntimeException.class);
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

    @Test
    void shouldCompleteWithoutWriteWhenReportBodyNull() {
      setupWriter();

      dao.put(PROJECT, INTERACTION, DATE, null).blockingAwait();

      verify(writerPool, org.mockito.Mockito.never()).preparedQuery(anyString());
    }

    @Test
    void shouldPropagateErrorWhenWriterFails() {
      setupWriter();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("write failed")));

      dao.put(PROJECT, INTERACTION, DATE, "{\"x\":1}").test().assertError(RuntimeException.class);

      verify(writerPool).preparedQuery(org.mockito.Mockito.contains("INSERT INTO rca_report_cache"));
    }
  }
}
