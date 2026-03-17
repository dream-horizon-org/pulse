package org.dreamhorizon.pulseserver.dao.eventdefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.error.EventDefinitionNotFoundException;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventAttributeDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinition;
import org.dreamhorizon.pulseserver.service.eventdefinition.models.EventDefinitionPage;
import org.dreamhorizon.pulseserver.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(
    strictness = org.mockito.quality.Strictness.LENIENT)
@SuppressWarnings("unchecked")
class EventDefinitionDaoTest {

  private static final String TEST_TENANT = "test-tenant";
  private static final String TEST_USER = "user@test.com";
  private static final LocalDateTime NOW = LocalDateTime.of(2025, 6, 15, 10, 0, 0);

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

  EventDefinitionDao dao;

  @BeforeEach
  void setup() {
    TenantContext.setTenantId(TEST_TENANT);
    dao = new EventDefinitionDao(mysqlClient);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private void setupReaderPool() {
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
  }

  private void setupWriterPool() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
  }

  private void setupReaderPreparedQuery() {
    setupReaderPool();
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupWriterPreparedQuery() {
    setupWriterPool();
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private RowIterator<Row> createMockRowIterator(List<Row> rows) {
    RowIterator<Row> iterator = mock(RowIterator.class);
    if (rows.isEmpty()) {
      when(iterator.hasNext()).thenReturn(false);
    } else {
      final int[] index = {0};
      when(iterator.hasNext()).thenAnswer(inv -> index[0] < rows.size());
      when(iterator.next()).thenAnswer(inv -> {
        if (index[0] < rows.size()) {
          return rows.get(index[0]++);
        }
        throw new java.util.NoSuchElementException();
      });
    }
    return iterator;
  }

  private void setupRowSetWithForEach(RowSet<Row> rs, List<Row> rows) {
    RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>(rows));
    when(rs.iterator()).thenReturn(iterator);
    doAnswer(inv -> {
      Consumer<Row> consumer = inv.getArgument(0);
      rows.forEach(consumer);
      return null;
    }).when(rs).forEach(any());
  }

  private Row createMockEventDefinitionRow(Long id, String eventName) {
    Row mockRow = mock(Row.class);
    when(mockRow.getLong("id")).thenReturn(id);
    when(mockRow.getString("event_name")).thenReturn(eventName);
    when(mockRow.getString("display_name")).thenReturn("Display " + eventName);
    when(mockRow.getString("description")).thenReturn("Desc for " + eventName);
    when(mockRow.getString("category")).thenReturn("user_action");
    when(mockRow.getBoolean("is_archived")).thenReturn(false);
    when(mockRow.getString("created_by")).thenReturn(TEST_USER);
    when(mockRow.getString("updated_by")).thenReturn(TEST_USER);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(NOW);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(NOW);
    return mockRow;
  }

  private Row createMockAttributeRow(
      Long id, Long eventDefId, String name) {
    Row mockRow = mock(Row.class);
    when(mockRow.getLong("id")).thenReturn(id);
    when(mockRow.getLong("event_definition_id")).thenReturn(eventDefId);
    when(mockRow.getString("attribute_name")).thenReturn(name);
    when(mockRow.getString("description")).thenReturn("Desc " + name);
    when(mockRow.getString("data_type")).thenReturn("string");
    when(mockRow.getBoolean("is_required")).thenReturn(true);
    when(mockRow.getBoolean("is_archived")).thenReturn(false);
    return mockRow;
  }

  private Row createMockCountRow(long count) {
    Row mockRow = mock(Row.class);
    when(mockRow.getValue(0)).thenReturn(count);
    return mockRow;
  }

  private EventDefinition buildEventDefinition(
      Long id, String eventName, List<EventAttributeDefinition> attrs) {
    return EventDefinition.builder()
        .id(id)
        .eventName(eventName)
        .displayName("Display " + eventName)
        .description("Description")
        .category("user_action")
        .createdBy(TEST_USER)
        .updatedBy(TEST_USER)
        .attributes(attrs)
        .build();
  }

  private EventAttributeDefinition buildAttribute(String name) {
    return EventAttributeDefinition.builder()
        .attributeName(name)
        .description("Desc " + name)
        .dataType("string")
        .required(true)
        .build();
  }

  private EventAttributeDefinition buildAttributeNoDataType(String name) {
    return EventAttributeDefinition.builder()
        .attributeName(name)
        .description("Desc " + name)
        .dataType(null)
        .required(false)
        .build();
  }

  @Nested
  class TestQueryEventDefinitions {

    @Test
    void shouldQueryWithNoFilters() {
      setupReaderPool();
      PreparedQuery<RowSet<Row>> countPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> listPq = mock(PreparedQuery.class);

      when(readerPool.preparedQuery(anyString()))
          .thenReturn(countPq)
          .thenReturn(listPq);

      RowSet<Row> countRs = mock(RowSet.class);
      Row countRow = createMockCountRow(2L);
      RowIterator<Row> countIter =
          createMockRowIterator(Collections.singletonList(countRow));
      when(countRs.iterator()).thenReturn(countIter);
      when(countPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(countRs));

      RowSet<Row> listRs = mock(RowSet.class);
      Row row1 = createMockEventDefinitionRow(1L, "event_1");
      Row row2 = createMockEventDefinitionRow(2L, "event_2");
      setupRowSetWithForEach(listRs, Arrays.asList(row1, row2));
      when(listPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(listRs));

      EventDefinitionPage result =
          dao.queryEventDefinitions(null, null, 10, 0).blockingGet();

      assertNotNull(result);
      assertEquals(2L, result.getTotalCount());
      assertEquals(2, result.getDefinitions().size());
      assertEquals("event_1", result.getDefinitions().get(0).getEventName());
    }

    @Test
    void shouldQueryWithSearchFilter() {
      setupReaderPool();
      PreparedQuery<RowSet<Row>> countPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> listPq = mock(PreparedQuery.class);

      when(readerPool.preparedQuery(anyString()))
          .thenReturn(countPq)
          .thenReturn(listPq);

      RowSet<Row> countRs = mock(RowSet.class);
      Row countRow = createMockCountRow(1L);
      RowIterator<Row> countIter =
          createMockRowIterator(Collections.singletonList(countRow));
      when(countRs.iterator()).thenReturn(countIter);
      when(countPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(countRs));

      RowSet<Row> listRs = mock(RowSet.class);
      Row row1 = createMockEventDefinitionRow(1L, "user_login");
      setupRowSetWithForEach(listRs, Collections.singletonList(row1));
      when(listPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(listRs));

      EventDefinitionPage result =
          dao.queryEventDefinitions("user", null, 10, 0).blockingGet();

      assertNotNull(result);
      assertEquals(1L, result.getTotalCount());
      assertEquals(1, result.getDefinitions().size());
    }

    @Test
    void shouldQueryWithCategoryFilter() {
      setupReaderPool();
      PreparedQuery<RowSet<Row>> countPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> listPq = mock(PreparedQuery.class);

      when(readerPool.preparedQuery(anyString()))
          .thenReturn(countPq)
          .thenReturn(listPq);

      RowSet<Row> countRs = mock(RowSet.class);
      Row countRow = createMockCountRow(1L);
      RowIterator<Row> countIter =
          createMockRowIterator(Collections.singletonList(countRow));
      when(countRs.iterator()).thenReturn(countIter);
      when(countPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(countRs));

      RowSet<Row> listRs = mock(RowSet.class);
      Row row1 = createMockEventDefinitionRow(1L, "event_1");
      setupRowSetWithForEach(listRs, Collections.singletonList(row1));
      when(listPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(listRs));

      EventDefinitionPage result = dao.queryEventDefinitions(
          null, "user_action", 10, 0).blockingGet();

      assertNotNull(result);
      assertEquals(1L, result.getTotalCount());
    }

    @Test
    void shouldQueryWithBothSearchAndCategory() {
      setupReaderPool();
      PreparedQuery<RowSet<Row>> countPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> listPq = mock(PreparedQuery.class);

      when(readerPool.preparedQuery(anyString()))
          .thenReturn(countPq)
          .thenReturn(listPq);

      RowSet<Row> countRs = mock(RowSet.class);
      Row countRow = createMockCountRow(1L);
      RowIterator<Row> countIter =
          createMockRowIterator(Collections.singletonList(countRow));
      when(countRs.iterator()).thenReturn(countIter);
      when(countPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(countRs));

      RowSet<Row> listRs = mock(RowSet.class);
      Row row1 = createMockEventDefinitionRow(1L, "user_login");
      setupRowSetWithForEach(listRs, Collections.singletonList(row1));
      when(listPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(listRs));

      EventDefinitionPage result = dao.queryEventDefinitions(
          "login", "user_action", 10, 0).blockingGet();

      assertNotNull(result);
      assertEquals(1L, result.getTotalCount());
    }

    @Test
    void shouldReturnEmptyPageWhenNoResults() {
      setupReaderPool();
      PreparedQuery<RowSet<Row>> countPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> listPq = mock(PreparedQuery.class);

      when(readerPool.preparedQuery(anyString()))
          .thenReturn(countPq)
          .thenReturn(listPq);

      RowSet<Row> countRs = mock(RowSet.class);
      Row countRow = createMockCountRow(0L);
      RowIterator<Row> countIter =
          createMockRowIterator(Collections.singletonList(countRow));
      when(countRs.iterator()).thenReturn(countIter);
      when(countPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(countRs));

      RowSet<Row> listRs = mock(RowSet.class);
      setupRowSetWithForEach(listRs, Collections.emptyList());
      when(listPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(listRs));

      EventDefinitionPage result =
          dao.queryEventDefinitions(null, null, 10, 0).blockingGet();

      assertNotNull(result);
      assertEquals(0L, result.getTotalCount());
      assertTrue(result.getDefinitions().isEmpty());
    }

    @Test
    void shouldReturnZeroCountWhenNoRowsReturned() {
      setupReaderPool();
      PreparedQuery<RowSet<Row>> countPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> listPq = mock(PreparedQuery.class);

      when(readerPool.preparedQuery(anyString()))
          .thenReturn(countPq)
          .thenReturn(listPq);

      RowSet<Row> countRs = mock(RowSet.class);
      RowIterator<Row> emptyIter =
          createMockRowIterator(Collections.emptyList());
      when(countRs.iterator()).thenReturn(emptyIter);
      when(countPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(countRs));

      RowSet<Row> listRs = mock(RowSet.class);
      setupRowSetWithForEach(listRs, Collections.emptyList());
      when(listPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(listRs));

      EventDefinitionPage result =
          dao.queryEventDefinitions(null, null, 10, 0).blockingGet();

      assertEquals(0L, result.getTotalCount());
    }

    @Test
    void shouldHandleCountValueAsNonNumber() {
      setupReaderPool();
      PreparedQuery<RowSet<Row>> countPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> listPq = mock(PreparedQuery.class);

      when(readerPool.preparedQuery(anyString()))
          .thenReturn(countPq)
          .thenReturn(listPq);

      RowSet<Row> countRs = mock(RowSet.class);
      Row countRow = mock(Row.class);
      when(countRow.getValue(0)).thenReturn("not_a_number");
      RowIterator<Row> countIter =
          createMockRowIterator(Collections.singletonList(countRow));
      when(countRs.iterator()).thenReturn(countIter);
      when(countPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(countRs));

      RowSet<Row> listRs = mock(RowSet.class);
      setupRowSetWithForEach(listRs, Collections.emptyList());
      when(listPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(listRs));

      EventDefinitionPage result =
          dao.queryEventDefinitions(null, null, 10, 0).blockingGet();

      assertEquals(0L, result.getTotalCount());
    }

    @Test
    void shouldHandleNullCountValue() {
      setupReaderPool();
      PreparedQuery<RowSet<Row>> countPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> listPq = mock(PreparedQuery.class);

      when(readerPool.preparedQuery(anyString()))
          .thenReturn(countPq)
          .thenReturn(listPq);

      RowSet<Row> countRs = mock(RowSet.class);
      Row countRow = mock(Row.class);
      when(countRow.getValue(0)).thenReturn(null);
      RowIterator<Row> countIter =
          createMockRowIterator(Collections.singletonList(countRow));
      when(countRs.iterator()).thenReturn(countIter);
      when(countPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(countRs));

      RowSet<Row> listRs = mock(RowSet.class);
      setupRowSetWithForEach(listRs, Collections.emptyList());
      when(listPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(listRs));

      EventDefinitionPage result =
          dao.queryEventDefinitions(null, null, 10, 0).blockingGet();

      assertEquals(0L, result.getTotalCount());
    }

    @Test
    void shouldPropagateErrorFromCountQuery() {
      setupReaderPool();
      PreparedQuery<RowSet<Row>> countPq = mock(PreparedQuery.class);
      when(readerPool.preparedQuery(anyString())).thenReturn(countPq);
      when(countPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB Error")));

      assertThrows(RuntimeException.class, () ->
          dao.queryEventDefinitions(null, null, 10, 0).blockingGet());
    }

    @Test
    void shouldTreatBlankSearchAsNoSearch() {
      setupReaderPool();
      PreparedQuery<RowSet<Row>> countPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> listPq = mock(PreparedQuery.class);

      when(readerPool.preparedQuery(anyString()))
          .thenReturn(countPq)
          .thenReturn(listPq);

      RowSet<Row> countRs = mock(RowSet.class);
      Row countRow = createMockCountRow(0L);
      RowIterator<Row> countIter =
          createMockRowIterator(Collections.singletonList(countRow));
      when(countRs.iterator()).thenReturn(countIter);
      when(countPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(countRs));

      RowSet<Row> listRs = mock(RowSet.class);
      setupRowSetWithForEach(listRs, Collections.emptyList());
      when(listPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(listRs));

      EventDefinitionPage result =
          dao.queryEventDefinitions("  ", "  ", 10, 0).blockingGet();

      assertNotNull(result);
    }
  }

  @Nested
  class TestGetDistinctCategories {

    @Test
    void shouldReturnCategories() {
      setupReaderPreparedQuery();
      Row r1 = mock(Row.class);
      when(r1.getString("category")).thenReturn("user_action");
      Row r2 = mock(Row.class);
      when(r2.getString("category")).thenReturn("navigation");

      setupRowSetWithForEach(rowSet, Arrays.asList(r1, r2));
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet));

      List<String> result = dao.getDistinctCategories().blockingGet();

      assertEquals(2, result.size());
      assertEquals("user_action", result.get(0));
      assertEquals("navigation", result.get(1));
    }

    @Test
    void shouldReturnEmptyListWhenNoCategories() {
      setupReaderPreparedQuery();
      setupRowSetWithForEach(rowSet, Collections.emptyList());
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet));

      List<String> result = dao.getDistinctCategories().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldPropagateError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB Error")));

      assertThrows(RuntimeException.class, () ->
          dao.getDistinctCategories().blockingGet());
    }
  }

  @Nested
  class TestGetEventDefinitionById {

    @Test
    void shouldReturnEventDefinition() {
      setupReaderPreparedQuery();
      Row row = createMockEventDefinitionRow(1L, "login_event");
      RowIterator<Row> iter =
          createMockRowIterator(Collections.singletonList(row));
      when(rowSet.iterator()).thenReturn(iter);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet));

      EventDefinition result =
          dao.getEventDefinitionById(1L).blockingGet();

      assertNotNull(result);
      assertEquals(1L, result.getId());
      assertEquals("login_event", result.getEventName());
      assertEquals("Display login_event", result.getDisplayName());
      assertEquals("user_action", result.getCategory());
    }

    @Test
    void shouldThrowNotFoundWhenNoResult() {
      setupReaderPreparedQuery();
      RowIterator<Row> iter =
          createMockRowIterator(Collections.emptyList());
      when(rowSet.iterator()).thenReturn(iter);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(RuntimeException.class, () ->
          dao.getEventDefinitionById(999L).blockingGet());
      assertTrue(ex.getCause() instanceof EventDefinitionNotFoundException
          || ex instanceof EventDefinitionNotFoundException);
    }

    @Test
    void shouldPropagateError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB Error")));

      assertThrows(RuntimeException.class, () ->
          dao.getEventDefinitionById(1L).blockingGet());
    }
  }

  @Nested
  class TestCreateEventDefinition {

    @Test
    void shouldCreateWithoutAttributes() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> insertPq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString())).thenReturn(insertPq);

      RowSet<Row> insertRs = mock(RowSet.class);
      when(insertRs.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);
      when(insertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(insertRs));

      EventDefinition def = buildEventDefinition(
          null, "new_event", null);

      Long result = dao.createEventDefinition(def).blockingGet();

      assertEquals(42L, result);
      verify(sqlConnection).close();
    }

    @Test
    void shouldCreateWithEmptyAttributes() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> insertPq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString())).thenReturn(insertPq);

      RowSet<Row> insertRs = mock(RowSet.class);
      when(insertRs.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(10L);
      when(insertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(insertRs));

      EventDefinition def = buildEventDefinition(
          null, "new_event", Collections.emptyList());

      Long result = dao.createEventDefinition(def).blockingGet();

      assertEquals(10L, result);
    }

    @Test
    void shouldCreateWithAttributes() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> insertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> attrPq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(insertPq)
          .thenReturn(attrPq);

      RowSet<Row> insertRs = mock(RowSet.class);
      when(insertRs.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(5L);
      when(insertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(insertRs));

      RowSet<Row> attrRs = mock(RowSet.class);
      when(attrPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(attrRs));

      List<EventAttributeDefinition> attrs =
          Collections.singletonList(buildAttribute("attr1"));
      EventDefinition def = buildEventDefinition(
          null, "event_with_attrs", attrs);

      Long result = dao.createEventDefinition(def).blockingGet();

      assertEquals(5L, result);
    }

    @Test
    void shouldCreateWithAttributeHavingNullDataType() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> insertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> attrPq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(insertPq)
          .thenReturn(attrPq);

      RowSet<Row> insertRs = mock(RowSet.class);
      when(insertRs.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(7L);
      when(insertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(insertRs));

      RowSet<Row> attrRs = mock(RowSet.class);
      when(attrPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(attrRs));

      List<EventAttributeDefinition> attrs =
          Collections.singletonList(buildAttributeNoDataType("attr_no_type"));
      EventDefinition def = buildEventDefinition(
          null, "event_null_dtype", attrs);

      Long result = dao.createEventDefinition(def).blockingGet();

      assertEquals(7L, result);
    }

    @Test
    void shouldPropagateError() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.error(new RuntimeException("Connection failed")));

      EventDefinition def = buildEventDefinition(
          null, "fail_event", null);

      assertThrows(RuntimeException.class, () ->
          dao.createEventDefinition(def).blockingGet());
    }
  }

  @Nested
  class TestUpsertEventDefinition {

    @Test
    void shouldReturnPositiveIdForInsert() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> upsertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> idPq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(upsertPq)
          .thenReturn(idPq);

      RowSet<Row> upsertRs = mock(RowSet.class);
      when(upsertRs.rowCount()).thenReturn(1);
      when(upsertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(upsertRs));

      RowSet<Row> idRs = mock(RowSet.class);
      Row idRow = mock(Row.class);
      when(idRow.getLong("id")).thenReturn(10L);
      RowIterator<Row> idIter =
          createMockRowIterator(Collections.singletonList(idRow));
      when(idRs.iterator()).thenReturn(idIter);
      when(idPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(idRs));

      EventDefinition def = buildEventDefinition(
          null, "upsert_event", null);

      Long result = dao.upsertEventDefinition(def).blockingGet();

      assertEquals(10L, result);
    }

    @Test
    void shouldReturnNegativeIdForUpdate() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> upsertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> idPq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(upsertPq)
          .thenReturn(idPq);

      RowSet<Row> upsertRs = mock(RowSet.class);
      when(upsertRs.rowCount()).thenReturn(2);
      when(upsertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(upsertRs));

      RowSet<Row> idRs = mock(RowSet.class);
      Row idRow = mock(Row.class);
      when(idRow.getLong("id")).thenReturn(10L);
      RowIterator<Row> idIter =
          createMockRowIterator(Collections.singletonList(idRow));
      when(idRs.iterator()).thenReturn(idIter);
      when(idPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(idRs));

      EventDefinition def = buildEventDefinition(
          null, "existing_event", null);

      Long result = dao.upsertEventDefinition(def).blockingGet();

      assertEquals(-10L, result);
    }

    @Test
    void shouldUpsertWithAttributes() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> upsertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> idPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> attrPq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(upsertPq)
          .thenReturn(idPq)
          .thenReturn(attrPq);

      RowSet<Row> upsertRs = mock(RowSet.class);
      when(upsertRs.rowCount()).thenReturn(1);
      when(upsertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(upsertRs));

      RowSet<Row> idRs = mock(RowSet.class);
      Row idRow = mock(Row.class);
      when(idRow.getLong("id")).thenReturn(20L);
      RowIterator<Row> idIter =
          createMockRowIterator(Collections.singletonList(idRow));
      when(idRs.iterator()).thenReturn(idIter);
      when(idPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(idRs));

      RowSet<Row> attrRs = mock(RowSet.class);
      when(attrPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(attrRs));

      List<EventAttributeDefinition> attrs =
          Collections.singletonList(buildAttribute("attr1"));
      EventDefinition def = buildEventDefinition(
          null, "upsert_with_attrs", attrs);

      Long result = dao.upsertEventDefinition(def).blockingGet();

      assertEquals(20L, result);
    }

    @Test
    void shouldUpsertWithNullAttributes() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> upsertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> idPq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(upsertPq)
          .thenReturn(idPq);

      RowSet<Row> upsertRs = mock(RowSet.class);
      when(upsertRs.rowCount()).thenReturn(1);
      when(upsertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(upsertRs));

      RowSet<Row> idRs = mock(RowSet.class);
      Row idRow = mock(Row.class);
      when(idRow.getLong("id")).thenReturn(30L);
      RowIterator<Row> idIter =
          createMockRowIterator(Collections.singletonList(idRow));
      when(idRs.iterator()).thenReturn(idIter);
      when(idPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(idRs));

      EventDefinition def = buildEventDefinition(
          null, "upsert_no_attrs", null);

      Long result = dao.upsertEventDefinition(def).blockingGet();

      assertEquals(30L, result);
    }

    @Test
    void shouldUpsertWithEmptyAttributes() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> upsertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> idPq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(upsertPq)
          .thenReturn(idPq);

      RowSet<Row> upsertRs = mock(RowSet.class);
      when(upsertRs.rowCount()).thenReturn(1);
      when(upsertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(upsertRs));

      RowSet<Row> idRs = mock(RowSet.class);
      Row idRow = mock(Row.class);
      when(idRow.getLong("id")).thenReturn(31L);
      RowIterator<Row> idIter =
          createMockRowIterator(Collections.singletonList(idRow));
      when(idRs.iterator()).thenReturn(idIter);
      when(idPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(idRs));

      EventDefinition def = buildEventDefinition(
          null, "upsert_empty_attrs", Collections.emptyList());

      Long result = dao.upsertEventDefinition(def).blockingGet();

      assertEquals(31L, result);
    }

    @Test
    void shouldThrowWhenEventNameNotFound() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> upsertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> idPq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(upsertPq)
          .thenReturn(idPq);

      RowSet<Row> upsertRs = mock(RowSet.class);
      when(upsertRs.rowCount()).thenReturn(1);
      when(upsertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(upsertRs));

      RowSet<Row> idRs = mock(RowSet.class);
      RowIterator<Row> emptyIter =
          createMockRowIterator(Collections.emptyList());
      when(idRs.iterator()).thenReturn(emptyIter);
      when(idPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(idRs));

      EventDefinition def = buildEventDefinition(
          null, "ghost_event", null);

      assertThrows(RuntimeException.class, () ->
          dao.upsertEventDefinition(def).blockingGet());
    }

    @Test
    void shouldPropagateError() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.error(new RuntimeException("Conn failed")));

      EventDefinition def = buildEventDefinition(
          null, "fail_event", null);

      assertThrows(RuntimeException.class, () ->
          dao.upsertEventDefinition(def).blockingGet());
    }

    @Test
    void shouldUpsertAttributeWithNullDataType() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> upsertPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> idPq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> attrPq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(upsertPq)
          .thenReturn(idPq)
          .thenReturn(attrPq);

      RowSet<Row> upsertRs = mock(RowSet.class);
      when(upsertRs.rowCount()).thenReturn(1);
      when(upsertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(upsertRs));

      RowSet<Row> idRs = mock(RowSet.class);
      Row idRow = mock(Row.class);
      when(idRow.getLong("id")).thenReturn(40L);
      RowIterator<Row> idIter =
          createMockRowIterator(Collections.singletonList(idRow));
      when(idRs.iterator()).thenReturn(idIter);
      when(idPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(idRs));

      RowSet<Row> attrRs = mock(RowSet.class);
      when(attrPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(attrRs));

      List<EventAttributeDefinition> attrs =
          Collections.singletonList(buildAttributeNoDataType("no_type"));
      EventDefinition def = buildEventDefinition(
          null, "upsert_null_dtype", attrs);

      Long result = dao.upsertEventDefinition(def).blockingGet();

      assertEquals(40L, result);
    }
  }

  @Nested
  class TestUpdateEventDefinition {

    @Test
    void shouldUpdateWithAttributes() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> updatePq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> deletePq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insertPq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(updatePq)
          .thenReturn(deletePq)
          .thenReturn(insertPq);

      RowSet<Row> updateRs = mock(RowSet.class);
      when(updatePq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(updateRs));

      doReturn(Single.just(transaction))
          .when(sqlConnection).begin();

      RowSet<Row> deleteRs = mock(RowSet.class);
      when(deletePq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(deleteRs));

      RowSet<Row> insertRs = mock(RowSet.class);
      when(insertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(insertRs));

      when(transaction.rxCommit()).thenReturn(Completable.complete());

      List<EventAttributeDefinition> attrs =
          Collections.singletonList(buildAttribute("attr1"));
      EventDefinition def = buildEventDefinition(
          1L, "updated_event", attrs);

      dao.updateEventDefinition(def).blockingAwait();
    }

    @Test
    void shouldUpdateWithNullAttributes() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> updatePq = mock(PreparedQuery.class);
      when(sqlConnection.preparedQuery(anyString())).thenReturn(updatePq);

      RowSet<Row> updateRs = mock(RowSet.class);
      when(updatePq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(updateRs));

      EventDefinition def = buildEventDefinition(
          1L, "updated_no_attrs", null);

      dao.updateEventDefinition(def).blockingAwait();
    }

    @Test
    void shouldPropagateError() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.error(new RuntimeException("Conn failed")));

      EventDefinition def = buildEventDefinition(
          1L, "fail_update", null);

      assertThrows(RuntimeException.class, () ->
          dao.updateEventDefinition(def).blockingAwait());
    }

    @Test
    void shouldRollbackOnAttributeInsertError() {
      setupWriterPool();
      when(writerPool.rxGetConnection())
          .thenReturn(Single.just(sqlConnection));

      PreparedQuery<RowSet<Row>> updatePq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> deletePq = mock(PreparedQuery.class);
      PreparedQuery<RowSet<Row>> insertPq = mock(PreparedQuery.class);

      when(sqlConnection.preparedQuery(anyString()))
          .thenReturn(updatePq)
          .thenReturn(deletePq)
          .thenReturn(insertPq);

      RowSet<Row> updateRs = mock(RowSet.class);
      when(updatePq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(updateRs));

      doReturn(Single.just(transaction))
          .when(sqlConnection).begin();

      RowSet<Row> deleteRs = mock(RowSet.class);
      when(deletePq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(deleteRs));

      when(insertPq.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(
              new RuntimeException("Insert attr failed")));
      when(transaction.rxRollback()).thenReturn(Completable.complete());

      List<EventAttributeDefinition> attrs =
          Collections.singletonList(buildAttribute("bad_attr"));
      EventDefinition def = buildEventDefinition(
          1L, "rollback_event", attrs);

      assertThrows(RuntimeException.class, () ->
          dao.updateEventDefinition(def).blockingAwait());
    }
  }

  @Nested
  class TestArchiveEventDefinition {

    @Test
    void shouldArchiveSuccessfully() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet));

      dao.archiveEventDefinition(1L, TEST_USER).blockingAwait();
    }

    @Test
    void shouldPropagateError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB Error")));

      assertThrows(RuntimeException.class, () ->
          dao.archiveEventDefinition(1L, TEST_USER).blockingAwait());
    }
  }

  @Nested
  class TestGetAttributesForEvent {

    @Test
    void shouldReturnAttributes() {
      setupReaderPreparedQuery();
      Row a1 = createMockAttributeRow(1L, 10L, "attr_a");
      Row a2 = createMockAttributeRow(2L, 10L, "attr_b");
      setupRowSetWithForEach(rowSet, Arrays.asList(a1, a2));
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet));

      List<EventAttributeDefinition> result =
          dao.getAttributesForEvent(10L).blockingGet();

      assertEquals(2, result.size());
      assertEquals("attr_a", result.get(0).getAttributeName());
      assertEquals("attr_b", result.get(1).getAttributeName());
      assertEquals(10L, result.get(0).getEventDefinitionId());
    }

    @Test
    void shouldReturnEmptyList() {
      setupReaderPreparedQuery();
      setupRowSetWithForEach(rowSet, Collections.emptyList());
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet));

      List<EventAttributeDefinition> result =
          dao.getAttributesForEvent(10L).blockingGet();

      assertTrue(result.isEmpty());
    }

    @Test
    void shouldPropagateError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB Error")));

      assertThrows(RuntimeException.class, () ->
          dao.getAttributesForEvent(10L).blockingGet());
    }
  }

  @Nested
  class TestGetAttributesForEvents {

    @Test
    void shouldReturnEmptyMapForEmptyIds() {
      Map<Long, List<EventAttributeDefinition>> result =
          dao.getAttributesForEvents(Collections.emptyList()).blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldReturnGroupedAttributes() {
      setupReaderPreparedQuery();
      Row a1 = createMockAttributeRow(1L, 10L, "attr_a");
      Row a2 = createMockAttributeRow(2L, 10L, "attr_b");
      Row a3 = createMockAttributeRow(3L, 20L, "attr_c");

      setupRowSetWithForEach(rowSet, Arrays.asList(a1, a2, a3));
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet));

      Map<Long, List<EventAttributeDefinition>> result =
          dao.getAttributesForEvents(Arrays.asList(10L, 20L)).blockingGet();

      assertEquals(2, result.size());
      assertEquals(2, result.get(10L).size());
      assertEquals(1, result.get(20L).size());
    }

    @Test
    void shouldPropagateError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new RuntimeException("DB Error")));

      assertThrows(RuntimeException.class, () ->
          dao.getAttributesForEvents(
              Collections.singletonList(1L)).blockingGet());
    }
  }
}
