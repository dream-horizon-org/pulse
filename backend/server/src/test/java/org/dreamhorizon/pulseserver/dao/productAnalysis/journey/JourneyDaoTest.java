package org.dreamhorizon.pulseserver.dao.productAnalysis.journey;

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
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;
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
class JourneyDaoTest {

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

  JourneyDao dao;

  @BeforeEach
  void setup() {
    TenantContext.setTenantId(PROJECT);
    dao = new JourneyDao(mysqlClient);
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
    RowIterator<Row> it = iter(rows);
    when(rowSet.iterator()).thenReturn(it);
    doAnswer(
            inv -> {
              Consumer<Row> c = inv.getArgument(0);
              rows.forEach(c);
              return null;
            })
        .when(rowSet)
        .forEach(any());
  }

  private Row journeyRow(boolean withTotalCount) {
    Row row = mock(Row.class);
    when(row.getLong("id")).thenReturn(1L);
    when(row.getString("project_id")).thenReturn(PROJECT);
    when(row.getString("name")).thenReturn("j1");
    when(row.getString("description")).thenReturn("d");
    when(row.getString("anchor_event")).thenReturn("open");
    when(row.getString("direction")).thenReturn("START");
    when(row.getInteger("depth")).thenReturn(5);
    when(row.getString("mode")).thenReturn("UNIQUE_USERS");
    when(row.getValue("filters_json")).thenReturn(null);
    when(row.getInteger("date_range")).thenReturn(7);
    LocalDateTime now = LocalDateTime.of(2025, 6, 15, 10, 0);
    when(row.getLocalDateTime("start_time")).thenReturn(now);
    when(row.getLocalDateTime("end_time")).thenReturn(null);
    when(row.getString("journey_type")).thenReturn("AUTO");
    when(row.getLocalDateTime("expiry")).thenReturn(now);
    when(row.getLocalDateTime("created_at")).thenReturn(now);
    when(row.getLocalDateTime("updated_at")).thenReturn(now);
    when(row.getString("created_by")).thenReturn("user");
    when(row.getString("latest_job_status")).thenReturn("SUCCEEDED");
    when(row.getColumnIndex("total_count")).thenReturn(withTotalCount ? 18 : -1);
    if (withTotalCount) {
      when(row.getLong("total_count")).thenReturn(3L);
    }
    return row;
  }

  private JourneyRow buildInputRow() {
    return JourneyRow.builder()
        .id(0L)
        .projectId(PROJECT)
        .name("j1")
        .description("d")
        .anchorEvent("open")
        .direction("START")
        .depth(5)
        .mode("UNIQUE_USERS")
        .filtersJson(null)
        .startTime(Instant.parse("2025-06-15T10:00:00Z"))
        .endTime(null)
        .journeyType("AUTO")
        .expiry(Instant.parse("2025-07-15T10:00:00Z"))
        .dateRangeDays(7)
        .createdBy("user")
        .createdAt(null)
        .updatedAt(null)
        .latestJobStatus(null)
        .totalCount(0L)
        .build();
  }

  @Nested
  class Insert {
    @Test
    void shouldInsertAndReturnId() {
      setupWriter();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(99L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Long result = dao.insert(buildInputRow()).blockingGet();
      assertEquals(99L, result);
    }
  }

  @Nested
  class Update {
    @Test
    void shouldReturnRowCount() {
      setupWriter();
      when(rowSet.rowCount()).thenReturn(1L);
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
      when(rowSet.rowCount()).thenReturn(1L);
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
      RowIterator<Row> it = iter(Collections.singletonList(journeyRow(false)));
      when(rowSet.iterator()).thenReturn(it);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      JourneyRow result = dao.findByProjectAndId(PROJECT, 1L).blockingGet();
      assertNotNull(result);
      assertEquals("j1", result.getName());
    }

    @Test
    void shouldReturnEmpty() {
      setupReader();
      RowIterator<Row> it = iter(Collections.emptyList());
      when(rowSet.iterator()).thenReturn(it);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertEquals(null, dao.findByProjectAndId(PROJECT, 1L).blockingGet());
    }
  }

  @Nested
  class FindById {
    @Test
    void shouldReturnRow() {
      setupReader();
      RowIterator<Row> it = iter(Collections.singletonList(journeyRow(false)));
      when(rowSet.iterator()).thenReturn(it);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertNotNull(dao.findById(1L).blockingGet());
    }

    @Test
    void shouldReturnEmpty() {
      setupReader();
      RowIterator<Row> it = iter(Collections.emptyList());
      when(rowSet.iterator()).thenReturn(it);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      assertEquals(null, dao.findById(1L).blockingGet());
    }
  }

  @Nested
  class ListAllAuto {
    @Test
    void shouldReturnList() {
      setupReader();
      setupForEach(Collections.singletonList(journeyRow(false)));
      when(preparedQuery.rxExecute()).thenReturn(Single.just(rowSet));

      List<JourneyRow> result = dao.listAllAuto().blockingGet();
      assertEquals(1, result.size());
    }
  }

  @Nested
  class ListByProject {
    @Test
    void shouldListWithFilters() {
      setupReader();
      setupForEach(Collections.singletonList(journeyRow(true)));
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      JourneyListParams params =
          JourneyListParams.builder()
              .statuses(Arrays.asList("ACTIVE", "IN_PROGRESS"))
              .journeyType("AUTO")
              .nameLikePrefix("pre%")
              .ftsBooleanQuery("+foo")
              .useFullTextSearch(true)
              .updatedAfter(Instant.parse("2025-01-01T00:00:00Z"))
              .updatedBefore(Instant.parse("2025-12-31T00:00:00Z"))
              .createdBy("user@test.com")
              .limit(10)
              .offset(0)
              .build();

      List<JourneyRow> result = dao.listByProject(PROJECT, params).blockingGet();
      assertEquals(1, result.size());
      assertEquals(3L, result.get(0).getTotalCount());
    }

    @Test
    void shouldListWithMinimalParams() {
      setupReader();
      setupForEach(Collections.emptyList());
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      JourneyListParams params =
          JourneyListParams.builder()
              .statuses(Collections.emptyList())
              .journeyType("")
              .ftsBooleanQuery("")
              .useFullTextSearch(false)
              .createdBy("")
              .limit(10)
              .offset(0)
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
