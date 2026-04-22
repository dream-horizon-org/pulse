package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
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
class FunnelDefinitionDaoTest {

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

  FunnelDefinitionDao dao;

  @BeforeEach
  void setup() {
    TenantContext.setTenantId(PROJECT);
    dao = new FunnelDefinitionDao(mysqlClient);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private void setupWriter() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupReader() {
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private RowIterator<Row> iter(List<Row> rows) {
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

  private void setupForEach(List<Row> rows) {
    when(rowSet.iterator()).thenReturn(iter(rows));
    doAnswer(inv -> {
      Consumer<Row> c = inv.getArgument(0);
      rows.forEach(c);
      return null;
    }).when(rowSet).forEach(any());
  }

  private Row funnelRow(boolean withTotalCount) {
    Row row = mock(Row.class);
    when(row.getLong("id")).thenReturn(1L);
    when(row.getString("project_id")).thenReturn(PROJECT);
    when(row.getString("name")).thenReturn("fn1");
    when(row.getString("description")).thenReturn("desc");
    when(row.getString("funnel_type")).thenReturn("AUTO");
    when(row.getString("step_order_type")).thenReturn("STRICT");
    when(row.getValue("steps_json")).thenReturn("[]");
    when(row.getLong("window_seconds")).thenReturn(3600L);
    when(row.getString("mode")).thenReturn("BASIC");
    when(row.getValue("filters_json")).thenReturn(null);
    when(row.getInteger("date_range")).thenReturn(7);
    LocalDateTime now = LocalDateTime.of(2025, 6, 15, 10, 0);
    when(row.getLocalDateTime("start_time")).thenReturn(now);
    when(row.getLocalDateTime("end_time")).thenReturn(null);
    when(row.getLocalDateTime("expiry")).thenReturn(now);
    when(row.getLocalDateTime("created_at")).thenReturn(now);
    when(row.getLocalDateTime("updated_at")).thenReturn(now);
    when(row.getString("created_by")).thenReturn("user");
    when(row.getString("latest_job_status")).thenReturn("SUCCEEDED");
    when(row.getColumnIndex("total_count")).thenReturn(withTotalCount ? 18 : -1);
    if (withTotalCount) {
      when(row.getLong("total_count")).thenReturn(5L);
    }
    return row;
  }

  private FunnelDefinitionRow buildInputRow() {
    return FunnelDefinitionRow.builder()
        .projectId(PROJECT)
        .name("fn1")
        .description("desc")
        .funnelType("AUTO")
        .stepOrderType("STRICT")
        .stepsJson("[]")
        .windowSeconds(3600L)
        .mode("BASIC")
        .filtersJson(null)
        .dateRangeDays(7)
        .startTime(Instant.parse("2025-06-15T10:00:00Z"))
        .endTime(null)
        .expiry(Instant.parse("2025-07-15T10:00:00Z"))
        .createdBy("user")
        .build();
  }

  @Nested
  class Insert {
    @Test
    void shouldInsertAndReturnId() {
      setupWriter();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Long result = dao.insert(buildInputRow()).blockingGet();
      assertEquals(42L, result);
    }
  }

  @Nested
  class Update {
    @Test
    void shouldReturnRowCount() {
      setupWriter();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = dao.update(1L, PROJECT, buildInputRow()).blockingGet();
      assertEquals(1, result);
    }
  }

  @Nested
  class Delete {
    @Test
    void shouldReturnRowCount() {
      setupWriter();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Integer result = dao.delete(PROJECT, 1L).blockingGet();
      assertEquals(1, result);
    }
  }

  @Nested
  class FindByProjectAndId {
    @Test
    void shouldReturnRow() {
      setupReader();
      when(rowSet.iterator()).thenReturn(iter(Collections.singletonList(funnelRow(false))));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      FunnelDefinitionRow result = dao.findByProjectAndId(PROJECT, 1L).blockingGet();
      assertNotNull(result);
      assertEquals("fn1", result.getName());
      assertEquals("[]", result.getStepsJson());
      assertEquals(null, result.getFiltersJson());
    }

    @Test
    void shouldReturnEmpty() {
      setupReader();
      when(rowSet.iterator()).thenReturn(iter(Collections.emptyList()));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      FunnelDefinitionRow result = dao.findByProjectAndId(PROJECT, 1L).blockingGet();
      assertEquals(null, result);
    }
  }

  @Nested
  class FindById {
    @Test
    void shouldReturnRow() {
      setupReader();
      when(rowSet.iterator()).thenReturn(iter(Collections.singletonList(funnelRow(false))));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      FunnelDefinitionRow result = dao.findById(1L).blockingGet();
      assertNotNull(result);
    }

    @Test
    void shouldReturnEmpty() {
      setupReader();
      when(rowSet.iterator()).thenReturn(iter(Collections.emptyList()));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      FunnelDefinitionRow result = dao.findById(1L).blockingGet();
      assertEquals(null, result);
    }
  }

  @Nested
  class ListAllAuto {
    @Test
    void shouldReturnList() {
      setupReader();
      setupForEach(Collections.singletonList(funnelRow(false)));
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      List<FunnelDefinitionRow> result = dao.listAllAuto().blockingGet();
      assertEquals(1, result.size());
    }

    @Test
    void shouldReturnEmptyList() {
      setupReader();
      setupForEach(Collections.emptyList());
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      assertTrue(dao.listAllAuto().blockingGet().isEmpty());
    }
  }

  @Nested
  class ListByProject {
    @Test
    void shouldListWithAllParams() {
      setupReader();
      setupForEach(Collections.singletonList(funnelRow(true)));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      FunnelDefinitionListParams params = FunnelDefinitionListParams.builder()
          .statuses(Arrays.asList("ACTIVE", "IN_PROGRESS"))
          .funnelType("AUTO")
          .nameLikePrefix("sign%")
          .ftsBooleanQuery("signup")
          .useFullTextSearch(true)
          .updatedAfter(Instant.parse("2025-01-01T00:00:00Z"))
          .updatedBefore(Instant.parse("2025-12-31T00:00:00Z"))
          .createdBy("user@test.com")
          .limit(10)
          .offset(0)
          .build();

      List<FunnelDefinitionRow> result = dao.listByProject(PROJECT, params).blockingGet();
      assertEquals(1, result.size());
      assertEquals(5L, result.get(0).getTotalCount());
    }

    @Test
    void shouldListWithMinimalParams() {
      setupReader();
      setupForEach(Collections.emptyList());
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      FunnelDefinitionListParams params = FunnelDefinitionListParams.builder()
          .statuses(Collections.emptyList())
          .funnelType("")
          .ftsBooleanQuery("")
          .useFullTextSearch(false)
          .createdBy("")
          .limit(10)
          .offset(0)
          .build();

      assertTrue(dao.listByProject(PROJECT, params).blockingGet().isEmpty());
    }

    @Test
    void shouldListWithNullParams() {
      setupReader();
      setupForEach(Collections.emptyList());
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      FunnelDefinitionListParams params = FunnelDefinitionListParams.builder()
          .statuses(null)
          .funnelType(null)
          .nameLikePrefix(null)
          .ftsBooleanQuery(null)
          .useFullTextSearch(true)
          .updatedAfter(null)
          .updatedBefore(null)
          .createdBy(null)
          .limit(5)
          .offset(10)
          .build();

      assertTrue(dao.listByProject(PROJECT, params).blockingGet().isEmpty());
    }
  }

  @Nested
  class ListDistinctCreatedBy {
    @Test
    void shouldReturnCreators() {
      setupReader();
      Row r1 = mock(Row.class);
      when(r1.getString("created_by")).thenReturn("user1");
      Row r2 = mock(Row.class);
      when(r2.getString("created_by")).thenReturn(null);
      setupForEach(Arrays.asList(r1, r2));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<String> result = dao.listDistinctCreatedBy(PROJECT).blockingGet();
      assertEquals(1, result.size());
      assertEquals("user1", result.get(0));
    }
  }
}
