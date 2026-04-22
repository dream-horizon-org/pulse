package org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Transaction;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
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
class FunnelJourneyTagDaoTest {

  private static final String PROJECT = "test-project";

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

  @Mock
  SqlConnection sqlConnection;

  @Mock
  Transaction transaction;

  FunnelJourneyTagDao dao;

  @BeforeEach
  void setup() {
    TenantContext.setTenantId(PROJECT);
    dao = new FunnelJourneyTagDao(mysqlClient);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
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
    RowIterator<Row> iter = mock(RowIterator.class);
    if (rows.isEmpty()) {
      when(iter.hasNext()).thenReturn(false);
    } else {
      final int[] i = {0};
      when(iter.hasNext()).thenAnswer(inv -> i[0] < rows.size());
      when(iter.next()).thenAnswer(inv -> rows.get(i[0]++));
    }
    when(rowSet.iterator()).thenReturn(iter);
    doAnswer(inv -> {
      Consumer<Row> c = inv.getArgument(0);
      rows.forEach(c);
      return null;
    }).when(rowSet).forEach(any());
  }

  private Row tagRow(String tag) {
    Row r = mock(Row.class);
    when(r.getString("tag")).thenReturn(tag);
    return r;
  }

  private Row entityTagRow(Long entityId, String tag) {
    Row r = mock(Row.class);
    when(r.getLong("entity_id")).thenReturn(entityId);
    when(r.getString("tag")).thenReturn(tag);
    return r;
  }

  @Nested
  class ListDistinctTagsForProject {
    @Test
    void shouldReturnTags() {
      setupReaderPreparedQuery();
      setupRowSetForEach(Arrays.asList(tagRow("a"), tagRow("b"), tagRow(null)));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<String> result = dao.listDistinctTagsForProject(PROJECT).blockingGet();

      assertEquals(2, result.size());
      assertEquals("a", result.get(0));
    }

    @Test
    void shouldReturnEmpty() {
      setupReaderPreparedQuery();
      setupRowSetForEach(Collections.emptyList());
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertTrue(dao.listDistinctTagsForProject(PROJECT).blockingGet().isEmpty());
    }
  }

  @Nested
  class ListTagsForEntity {
    @Test
    void shouldReturnTags() {
      setupReaderPreparedQuery();
      setupRowSetForEach(Collections.singletonList(tagRow("x")));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<String> result = dao.listTagsForEntity(
          PROJECT, FunnelJourneyTagEntityType.FUNNEL, 1L).blockingGet();

      assertEquals(1, result.size());
      assertEquals("x", result.get(0));
    }
  }

  @Nested
  class ListTagsForEntities {
    @Test
    void shouldReturnEmptyMapForNullIds() {
      Map<Long, List<String>> result = dao.listTagsForEntities(
          PROJECT, FunnelJourneyTagEntityType.FUNNEL, null).blockingGet();
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnEmptyMapForEmptyIds() {
      Map<Long, List<String>> result = dao.listTagsForEntities(
          PROJECT, FunnelJourneyTagEntityType.JOURNEY, Collections.emptyList()).blockingGet();
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnMappedTags() {
      setupReaderPreparedQuery();
      setupRowSetForEach(Arrays.asList(
          entityTagRow(1L, "a"),
          entityTagRow(1L, "b"),
          entityTagRow(2L, "c"),
          entityTagRow(null, "ignored"),
          entityTagRow(3L, null)));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Map<Long, List<String>> result = dao.listTagsForEntities(
          PROJECT, FunnelJourneyTagEntityType.FUNNEL, Arrays.asList(1L, 2L, 3L)).blockingGet();

      assertEquals(2, result.size());
      assertEquals(2, result.get(1L).size());
      assertEquals(1, result.get(2L).size());
    }
  }

  @Nested
  class DeleteAllForEntity {
    @Test
    void shouldComplete() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      dao.deleteAllForEntity(PROJECT, FunnelJourneyTagEntityType.FUNNEL, 1L).blockingAwait();
    }
  }

  @Nested
  class ReplaceTags {
    @Test
    void shouldReplaceTagsInTransactionWhenTagsEmpty() {
      when(mysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxBegin()).thenReturn(Single.just(transaction));

      PreparedQuery<RowSet<Row>> deletePq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString())).thenReturn(deletePq);
      when(deletePq.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(transaction.rxCommit()).thenReturn(Completable.complete());

      dao.replaceTags(PROJECT, FunnelJourneyTagEntityType.FUNNEL, 1L,
          Collections.emptyList()).blockingAwait();
    }

    @Test
    void shouldReplaceTagsInTransactionWithInsert() {
      when(mysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxBegin()).thenReturn(Single.just(transaction));

      PreparedQuery<RowSet<Row>> deletePq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insertPq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(deletePq)
          .thenReturn(insertPq);
      when(deletePq.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(insertPq.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));
      when(transaction.rxCommit()).thenReturn(Completable.complete());

      dao.replaceTags(PROJECT, FunnelJourneyTagEntityType.JOURNEY, 1L,
          Arrays.asList("a", "b")).blockingAwait();
    }

    @Test
    void shouldRollbackOnError() {
      when(mysqlClient.getWriterPool()).thenReturn(writerPool);
      when(writerPool.rxGetConnection()).thenReturn(Single.just(sqlConnection));
      when(sqlConnection.rxBegin()).thenReturn(Single.just(transaction));

      PreparedQuery<RowSet<Row>> deletePq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString())).thenReturn(deletePq);
      when(deletePq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("boom")));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      assertThrows(RuntimeException.class, () ->
          dao.replaceTags(PROJECT, FunnelJourneyTagEntityType.FUNNEL, 1L,
              Collections.singletonList("tag")).blockingAwait());
    }
  }

  @Nested
  class QueriesCoverage {
    @Test
    void shouldBuildSelectTagsForEntitiesIn() {
      String sql = FunnelJourneyTagQueries.buildSelectTagsForEntitiesIn(3);
      assertNotNull(sql);
      assertTrue(sql.contains("?,?,?"));
    }

    @Test
    void shouldBuildBatchInsert() {
      String sql = FunnelJourneyTagQueries.buildBatchInsert(2);
      assertNotNull(sql);
      assertTrue(sql.contains("(?,?,?,?),(?,?,?,?)"));
    }

    @Test
    void shouldBuildBatchInsertForZero() {
      String sql = FunnelJourneyTagQueries.buildBatchInsert(0);
      assertNotNull(sql);
    }

    @Test
    void shouldBuildBatchInsertTuple() {
      Tuple t = FunnelJourneyTagQueries.batchInsertTuple(
          PROJECT, FunnelJourneyTagEntityType.FUNNEL, 1L,
          new ArrayList<>(Arrays.asList("a", "b")));
      assertNotNull(t);
      assertEquals(8, t.size());
    }

    @Test
    void shouldHaveEntityTypeValues() {
      assertEquals(2, FunnelJourneyTagEntityType.values().length);
      assertEquals(FunnelJourneyTagEntityType.FUNNEL,
          FunnelJourneyTagEntityType.valueOf("FUNNEL"));
    }
  }
}
