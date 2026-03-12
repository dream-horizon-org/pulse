package org.dreamhorizon.pulseserver.dao.usagelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLException;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Query;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.usagelimit.models.ProjectUsageLimit;
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
class ProjectUsageLimitDaoTest {

  @Mock
  MysqlClient mysqlClient;

  @Mock
  MySQLPool writerPool;

  @Mock
  MySQLPool readerPool;

  @Mock
  SqlConnection sqlConnection;

  @Mock
  PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  Query<RowSet<Row>> query;

  @Mock
  RowSet<Row> rowSet;

  @Mock
  Row row;

  ProjectUsageLimitDao projectUsageLimitDao;

  @BeforeEach
  void setup() {
    projectUsageLimitDao = new ProjectUsageLimitDao(mysqlClient);
  }

  private void setupWriterPool() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
  }

  private void setupReaderPool() {
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
  }

  private void setupWriterPreparedQuery() {
    setupWriterPool();
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupReaderPreparedQuery() {
    setupReaderPool();
    when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private RowIterator<Row> createMockRowIterator(List<Row> rows) {
    RowIterator<Row> iterator = mock(RowIterator.class);
    if (rows.isEmpty()) {
      when(iterator.hasNext()).thenReturn(false);
    } else {
      final int[] index = {0};
      when(iterator.hasNext()).thenAnswer(invocation -> index[0] < rows.size());
      when(iterator.next()).thenAnswer(invocation -> {
        if (index[0] < rows.size()) {
          return rows.get(index[0]++);
        }
        throw new java.util.NoSuchElementException();
      });
    }
    return iterator;
  }

  private Row createMockUsageLimitRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("project_usage_limit_id")).thenReturn(1L);
    when(mockRow.getString("project_id")).thenReturn("proj-1");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getString("created_by")).thenReturn("user-1");
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("disabled_at")).thenReturn(null);
    when(mockRow.getString("disabled_by")).thenReturn(null);
    when(mockRow.getString("disabled_reason")).thenReturn(null);
    when(mockRow.getValue("usage_limits")).thenReturn(new JsonObject("{\"events\":100}"));
    return mockRow;
  }

  private Row createMockUsageLimitRowWithStringUsageLimits() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("project_usage_limit_id")).thenReturn(1L);
    when(mockRow.getString("project_id")).thenReturn("proj-1");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getString("created_by")).thenReturn("user-1");
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("disabled_at")).thenReturn(null);
    when(mockRow.getString("disabled_by")).thenReturn(null);
    when(mockRow.getString("disabled_reason")).thenReturn(null);
    when(mockRow.getValue("usage_limits")).thenReturn("plain-string");
    return mockRow;
  }

  private Row createMockUsageLimitRowWithNullUsageLimits() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("project_usage_limit_id")).thenReturn(1L);
    when(mockRow.getString("project_id")).thenReturn("proj-1");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getString("created_by")).thenReturn("user-1");
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("disabled_at")).thenReturn(null);
    when(mockRow.getString("disabled_by")).thenReturn(null);
    when(mockRow.getString("disabled_reason")).thenReturn(null);
    when(mockRow.getValue("usage_limits")).thenReturn(null);
    return mockRow;
  }

  @Nested
  class CreateUsageLimit {

    @Test
    void shouldCreateUsageLimitWithPoolSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectUsageLimit result = projectUsageLimitDao.createUsageLimit(
          "proj-1", "{\"events\":100}", "user-1").blockingGet();

      assertNotNull(result);
      assertEquals(42L, result.getProjectUsageLimitId());
      assertEquals("proj-1", result.getProjectId());
      assertEquals("{\"events\":100}", result.getUsageLimits());
      assertTrue(result.getIsActive());
    }

    @Test
    void shouldCreateUsageLimitWithConnectionSuccessfully() {
      when(sqlConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(99L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectUsageLimit result = projectUsageLimitDao.createUsageLimit(
          sqlConnection, "proj-2", "{}", "user-2").blockingGet();

      assertNotNull(result);
      assertEquals(99L, result.getProjectUsageLimitId());
      assertEquals("proj-2", result.getProjectId());
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () ->
          projectUsageLimitDao.createUsageLimit("proj-1", "{}", "u").blockingGet());
    }
  }

  @Nested
  class GetActiveLimitByProjectId {

    @Test
    void shouldGetActiveLimitSuccessfully() {
      setupReaderPreparedQuery();
      Row limitRow = createMockUsageLimitRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(limitRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectUsageLimit result = projectUsageLimitDao.getActiveLimitByProjectId("proj-1").blockingGet();

      assertNotNull(result);
      assertEquals(1L, result.getProjectUsageLimitId());
      assertEquals("proj-1", result.getProjectId());
      assertNotNull(result.getUsageLimits());
    }

    @Test
    void shouldMapUsageLimitsFromJsonObject() {
      setupReaderPreparedQuery();
      Row limitRow = createMockUsageLimitRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(limitRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectUsageLimit result = projectUsageLimitDao.getActiveLimitByProjectId("proj-1").blockingGet();

      assertNotNull(result);
      assertTrue(result.getUsageLimits().contains("events"));
    }

    @Test
    void shouldMapUsageLimitsFromNonJsonObject() {
      setupReaderPreparedQuery();
      Row limitRow = createMockUsageLimitRowWithStringUsageLimits();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(limitRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectUsageLimit result = projectUsageLimitDao.getActiveLimitByProjectId("proj-1").blockingGet();

      assertNotNull(result);
      assertEquals("plain-string", result.getUsageLimits());
    }

    @Test
    void shouldHandleNullUsageLimits() {
      setupReaderPreparedQuery();
      Row limitRow = createMockUsageLimitRowWithNullUsageLimits();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(limitRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectUsageLimit result = projectUsageLimitDao.getActiveLimitByProjectId("proj-1").blockingGet();

      assertNotNull(result);
      assertNull(result.getUsageLimits());
    }

    @Test
    void shouldReturnEmptyWhenLimitNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectUsageLimit result = projectUsageLimitDao.getActiveLimitByProjectId("proj-empty").blockingGet();

      assertNull(result);
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class,
          () -> projectUsageLimitDao.getActiveLimitByProjectId("proj-1").blockingGet());
    }
  }

  @Nested
  class GetLimitById {

    @Test
    void shouldGetLimitByIdSuccessfully() {
      setupReaderPreparedQuery();
      Row limitRow = createMockUsageLimitRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(limitRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectUsageLimit result = projectUsageLimitDao.getLimitById(1L).blockingGet();

      assertNotNull(result);
      assertEquals(1L, result.getProjectUsageLimitId());
    }

    @Test
    void shouldReturnEmptyWhenLimitNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectUsageLimit result = projectUsageLimitDao.getLimitById(999L).blockingGet();

      assertNull(result);
    }
  }

  @Nested
  class GetAllLimitsByProjectId {

    @Test
    void shouldGetAllLimitsSuccessfully() {
      setupReaderPreparedQuery();
      Row limitRow = createMockUsageLimitRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(limitRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ProjectUsageLimit> result = projectUsageLimitDao.getAllLimitsByProjectId("proj-1").toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoLimits() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ProjectUsageLimit> result = projectUsageLimitDao.getAllLimitsByProjectId("proj-empty").toList().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class GetLimitHistoryByProjectId {

    @Test
    void shouldGetLimitHistorySuccessfully() {
      setupReaderPreparedQuery();
      Row limitRow = createMockUsageLimitRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(limitRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ProjectUsageLimit> result =
          projectUsageLimitDao.getLimitHistoryByProjectId("proj-1").toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }
  }

  @Nested
  class SoftDeleteActiveLimit {

    @Test
    void shouldSoftDeleteSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      projectUsageLimitDao.softDeleteActiveLimit("proj-1", "admin", "Updated").blockingAwait();
    }

    @Test
    void shouldCompleteEvenWhenNoActiveLimitFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      projectUsageLimitDao.softDeleteActiveLimit("proj-empty", "admin", "N/A").blockingAwait();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () ->
          projectUsageLimitDao.softDeleteActiveLimit("proj-1", "a", "r").blockingAwait());
    }
  }

  @Nested
  class SoftDeleteActiveLimitsForProjects {

    @Test
    void shouldCompleteImmediatelyWhenProjectIdsNull() {
      projectUsageLimitDao.softDeleteActiveLimitsForProjects(null, "admin", "reason").blockingAwait();
    }

    @Test
    void shouldCompleteImmediatelyWhenProjectIdsEmpty() {
      projectUsageLimitDao.softDeleteActiveLimitsForProjects(Collections.emptyList(), "admin", "reason").blockingAwait();
    }

    @Test
    void shouldSoftDeleteForMultipleProjects() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(2);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      projectUsageLimitDao.softDeleteActiveLimitsForProjects(
          List.of("proj-1", "proj-2"), "admin", "Decommission").blockingAwait();
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () ->
          projectUsageLimitDao.softDeleteActiveLimitsForProjects(
              List.of("proj-1"), "admin", "reason").blockingAwait());
    }
  }

  @Nested
  class HasActiveLimit {

    @Test
    void shouldReturnTrueWhenActiveLimitExists() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(1L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = projectUsageLimitDao.hasActiveLimit("proj-1").blockingGet();

      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenNoActiveLimit() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(0L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = projectUsageLimitDao.hasActiveLimit("proj-empty").blockingGet();

      assertFalse(result);
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> projectUsageLimitDao.hasActiveLimit("proj-1").blockingGet());
    }
  }

  @Nested
  class GetAllActiveLimits {

    @Test
    void shouldGetAllActiveLimitsSuccessfully() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);

      Row limitRow = createMockUsageLimitRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(limitRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<ProjectUsageLimit> result = projectUsageLimitDao.getAllActiveLimits().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);
      when(query.rxExecute()).thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> projectUsageLimitDao.getAllActiveLimits().toList().blockingGet());
    }
  }

  @Nested
  class GetAllLimits {

    @Test
    void shouldGetAllLimitsSuccessfully() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);

      Row limitRow = createMockUsageLimitRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(limitRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<ProjectUsageLimit> result = projectUsageLimitDao.getAllLimits().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }
  }

  @Nested
  class UpdateUsageLimits {

    @Test
    void shouldUpdateUsageLimitsSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(2L);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(rowSet))
          .thenReturn(Single.just(rowSet));

      ProjectUsageLimit result = projectUsageLimitDao.updateUsageLimits(
          "proj-1", "{\"events\":200}", "admin", "Increased").blockingGet();

      assertNotNull(result);
      assertEquals(2L, result.getProjectUsageLimitId());
      assertEquals("{\"events\":200}", result.getUsageLimits());
    }
  }
}
