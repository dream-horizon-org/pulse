package org.dreamhorizon.pulseserver.dao.productAnalysis.revenueevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.productAnalysis.revenueevent.models.RevenueEventRow;
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
@SuppressWarnings("unchecked")
class RevenueEventDaoTest {

  private static final String PROJECT = "test-project";
  private static final String ID = "550e8400-e29b-41d4-a716-446655440000";

  @Mock
  MysqlClient mysqlClient;

  @Mock
  MySQLPool readerPool;

  @Mock
  MySQLPool writerPool;

  @Mock
  PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  RowSet<Row> rowSet;

  RevenueEventDao dao;

  @BeforeEach
  void setup() {
    dao = new RevenueEventDao(mysqlClient);
  }

  private void setupReaderPreparedQuery() {
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupWriterPreparedQuery() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupRowSetForEach(List<Row> rows) {
    RowIterator<Row> iter = org.mockito.Mockito.mock(RowIterator.class);
    if (rows.isEmpty()) {
      when(iter.hasNext()).thenReturn(false);
    } else {
      final int[] i = {0};
      when(iter.hasNext()).thenAnswer(inv -> i[0] < rows.size());
      when(iter.next()).thenAnswer(inv -> rows.get(i[0]++));
    }
    when(rowSet.iterator()).thenReturn(iter);
    org.mockito.Mockito.doAnswer(
        inv -> {
          Consumer<Row> c = inv.getArgument(0);
          rows.forEach(c);
          return null;
        })
      .when(rowSet)
      .forEach(any());
  }

  private Row revenueRow() {
    Row r = org.mockito.Mockito.mock(Row.class);
    when(r.getString("id")).thenReturn(ID);
    when(r.getString("project_id")).thenReturn(PROJECT);
    when(r.getString("event_name")).thenReturn("order_placed");
    when(r.getString("value_attribute")).thenReturn("order_amount");
    when(r.getString("currency")).thenReturn("INR");
    when(r.getString("currency_attribute")).thenReturn(null);
    when(r.getInteger("conversion_window_hours")).thenReturn(24);
    when(r.getString("configured_by")).thenReturn("pm@example.com");
    when(r.getLocalDateTime("configured_at")).thenReturn(LocalDateTime.of(2026, 1, 15, 10, 0));
    when(r.getLocalDateTime("updated_at")).thenReturn(LocalDateTime.of(2026, 1, 15, 10, 0));
    return r;
  }

  @Nested
  class Insert {

    @Test
    void shouldInsertRevenueEvent() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RevenueEventRow row =
        RevenueEventRow.builder()
          .id(ID)
          .projectId(PROJECT)
          .eventName("order_placed")
          .valueAttribute("order_amount")
          .currency("INR")
          .currencyAttribute(null)
          .conversionWindowHours(24)
          .configuredBy("pm@example.com")
          .build();

      dao.insert(row).test().assertComplete();

      ArgumentCaptor<Tuple> captor = ArgumentCaptor.forClass(Tuple.class);
      verify(preparedQuery).rxExecute(captor.capture());
      assertThat(captor.getValue().getString(0)).isEqualTo(ID);
      assertThat(captor.getValue().getString(2)).isEqualTo("order_placed");
    }
  }

  @Nested
  class ListByProject {

    @Test
    void shouldReturnMappedRows() {
      setupReaderPreparedQuery();
      setupRowSetForEach(List.of(revenueRow()));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao
        .listByProject(PROJECT)
        .test()
        .assertValue(
          rows -> {
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getEventName()).isEqualTo("order_placed");
            assertThat(rows.get(0).getCurrency()).isEqualTo("INR");
            return true;
          });
    }
  }

  @Nested
  class FindByProjectAndId {

    @Test
    void shouldReturnRowWhenFound() {
      setupReaderPreparedQuery();
      Row row = revenueRow();
      RowIterator<Row> iter = org.mockito.Mockito.mock(RowIterator.class);
      when(iter.hasNext()).thenReturn(true, false);
      when(iter.next()).thenReturn(row);
      when(rowSet.iterator()).thenReturn(iter);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao
        .findByProjectAndId(PROJECT, ID)
        .test()
        .assertValue(found -> found.getId().equals(ID) && found.getEventName().equals("order_placed"));
    }

    @Test
    void shouldCompleteEmptyWhenNotFound() {
      setupReaderPreparedQuery();
      RowIterator<Row> iter = org.mockito.Mockito.mock(RowIterator.class);
      when(iter.hasNext()).thenReturn(false);
      when(rowSet.iterator()).thenReturn(iter);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao.findByProjectAndId(PROJECT, ID).test().assertNoValues().assertComplete();
    }
  }

  @Nested
  class Delete {

    @Test
    void shouldReturnDeletedRowCount() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao.delete(PROJECT, ID).test().assertValue(1);
    }
  }
}
