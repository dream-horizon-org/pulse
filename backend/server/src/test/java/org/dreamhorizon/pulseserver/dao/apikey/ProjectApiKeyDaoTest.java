package org.dreamhorizon.pulseserver.dao.apikey;

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
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.apikey.models.ProjectApiKey;
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
class ProjectApiKeyDaoTest {

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

  ProjectApiKeyDao projectApiKeyDao;

  @BeforeEach
  void setup() {
    projectApiKeyDao = new ProjectApiKeyDao(mysqlClient);
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

  private Row createMockApiKeyRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("project_api_key_id")).thenReturn(1L);
    when(mockRow.getString("project_id")).thenReturn("proj-1");
    when(mockRow.getString("display_name")).thenReturn("Key 1");
    when(mockRow.getString("api_key_encrypted")).thenReturn("enc");
    when(mockRow.getString("encryption_salt")).thenReturn("salt");
    when(mockRow.getString("api_key_digest")).thenReturn("digest");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("expires_at")).thenReturn(now.plusDays(30));
    when(mockRow.getLocalDateTime("grace_period_ends_at")).thenReturn(null);
    when(mockRow.getString("created_by")).thenReturn("user-1");
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("deactivated_at")).thenReturn(null);
    when(mockRow.getString("deactivated_by")).thenReturn(null);
    when(mockRow.getString("deactivation_reason")).thenReturn(null);
    return mockRow;
  }

  @Nested
  class CreateApiKey {

    @Test
    void shouldCreateApiKeyWithPoolSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectApiKey result = projectApiKeyDao.createApiKey(
          "proj-1", "Key 1", "enc", "salt", "digest",
          Instant.now().plusSeconds(86400), "user-1").blockingGet();

      assertNotNull(result);
      assertEquals(42L, result.getProjectApiKeyId());
      assertEquals("proj-1", result.getProjectId());
      assertEquals("Key 1", result.getDisplayName());
      assertTrue(result.getIsActive());
    }

    @Test
    void shouldCreateApiKeyWithConnectionSuccessfully() {
      when(sqlConnection.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(99L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectApiKey result = projectApiKeyDao.createApiKey(
          sqlConnection, "proj-2", "Key 2", "enc2", "salt2", "digest2",
          Instant.now().plusSeconds(86400), "user-2").blockingGet();

      assertNotNull(result);
      assertEquals(99L, result.getProjectApiKeyId());
      assertEquals("proj-2", result.getProjectId());
    }

    @Test
    void shouldCreateApiKeyWithNullExpiresAt() {
      setupWriterPreparedQuery();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(1L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectApiKey result = projectApiKeyDao.createApiKey(
          "proj-1", "Key", "enc", "salt", "digest", null, "user-1").blockingGet();

      assertNotNull(result);
      assertNull(result.getExpiresAt());
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () ->
          projectApiKeyDao.createApiKey("proj-1", "K", "e", "s", "d", Instant.now(), "u").blockingGet());
    }
  }

  @Nested
  class GetApiKeyById {

    @Test
    void shouldGetApiKeySuccessfully() {
      setupReaderPreparedQuery();
      Row apiKeyRow = createMockApiKeyRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(apiKeyRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectApiKey result = projectApiKeyDao.getApiKeyById(1L).blockingGet();

      assertNotNull(result);
      assertEquals(1L, result.getProjectApiKeyId());
      assertEquals("proj-1", result.getProjectId());
    }

    @Test
    void shouldReturnEmptyWhenApiKeyNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectApiKey result = projectApiKeyDao.getApiKeyById(999L).blockingGet();

      assertNull(result);
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> projectApiKeyDao.getApiKeyById(1L).blockingGet());
    }
  }

  @Nested
  class GetApiKeyByDigest {

    @Test
    void shouldGetApiKeyByDigestSuccessfully() {
      setupReaderPreparedQuery();
      Row apiKeyRow = createMockApiKeyRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(apiKeyRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectApiKey result = projectApiKeyDao.getApiKeyByDigest("digest").blockingGet();

      assertNotNull(result);
      assertEquals("digest", result.getApiKeyDigest());
    }

    @Test
    void shouldReturnEmptyWhenDigestNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ProjectApiKey result = projectApiKeyDao.getApiKeyByDigest("unknown").blockingGet();

      assertNull(result);
    }
  }

  @Nested
  class GetActiveApiKeysByProjectId {

    @Test
    void shouldGetActiveApiKeysSuccessfully() {
      setupReaderPreparedQuery();
      Row apiKeyRow = createMockApiKeyRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(apiKeyRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ProjectApiKey> result = projectApiKeyDao.getActiveApiKeysByProjectId("proj-1").toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("proj-1", result.get(0).getProjectId());
    }

    @Test
    void shouldReturnEmptyListWhenNoKeys() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ProjectApiKey> result = projectApiKeyDao.getActiveApiKeysByProjectId("proj-empty").toList().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }
  }

  @Nested
  class GetAllApiKeysByProjectId {

    @Test
    void shouldGetAllApiKeysSuccessfully() {
      setupReaderPreparedQuery();
      Row apiKeyRow = createMockApiKeyRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(apiKeyRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ProjectApiKey> result = projectApiKeyDao.getAllApiKeysByProjectId("proj-1").toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class,
          () -> projectApiKeyDao.getAllApiKeysByProjectId("proj-1").toList().blockingGet());
    }
  }

  @Nested
  class DeactivateApiKey {

    @Test
    void shouldDeactivateApiKeySuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      projectApiKeyDao.deactivateApiKey(1L, "proj-1", "admin", "Rotated", null).blockingAwait();
    }

    @Test
    void shouldThrowWhenApiKeyNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RuntimeException ex = assertThrows(RuntimeException.class, () ->
          projectApiKeyDao.deactivateApiKey(999L, "proj-1", "admin", "Rotated", null).blockingAwait());

      assertTrue(ex.getMessage().contains("API key not found"));
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () ->
          projectApiKeyDao.deactivateApiKey(1L, "proj-1", "a", "r", null).blockingAwait());
    }
  }

  @Nested
  class HasActiveApiKey {

    @Test
    void shouldReturnTrueWhenActiveKeyExists() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(1L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = projectApiKeyDao.hasActiveApiKey("proj-1").blockingGet();

      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenNoActiveKey() {
      setupReaderPreparedQuery();
      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(0L);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = projectApiKeyDao.hasActiveApiKey("proj-empty").blockingGet();

      assertFalse(result);
    }
  }

  @Nested
  class GetAllValidApiKeys {

    @Test
    void shouldGetAllValidApiKeysSuccessfully() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);

      Row apiKeyRow = createMockApiKeyRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(apiKeyRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<ProjectApiKey> result = projectApiKeyDao.getAllValidApiKeys().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);
      when(query.rxExecute()).thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> projectApiKeyDao.getAllValidApiKeys().toList().blockingGet());
    }
  }
}
