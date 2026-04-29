package org.dreamhorizon.pulseserver.dao.userapikey;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.userapikey.models.UserApiKey;
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
class UserApiKeyDaoTest {

  @Mock
  MysqlClient mysqlClient;

  @Mock
  MySQLPool writerPool;

  @Mock
  MySQLPool readerPool;

  @Mock
  PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  RowSet<Row> rowSet;

  @Mock
  Row row;

  UserApiKeyDao userApiKeyDao;

  @BeforeEach
  void setup() {
    userApiKeyDao = new UserApiKeyDao(mysqlClient);
  }

  private void setupWriterPreparedQuery() {
    when(mysqlClient.getWriterPool()).thenReturn(writerPool);
    when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
  }

  private void setupReaderPreparedQuery() {
    when(mysqlClient.getReaderPool()).thenReturn(readerPool);
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

  private Row createMockUserApiKeyRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("id")).thenReturn(1L);
    when(mockRow.getString("user_id")).thenReturn("user-1");
    when(mockRow.getString("display_name")).thenReturn("My key");
    when(mockRow.getString("api_key_hash")).thenReturn("hash");
    when(mockRow.getString("key_prefix")).thenReturn("pulse_mcp_abc");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("revoked_at")).thenReturn(null);
    when(mockRow.getString("revoked_by")).thenReturn(null);
    return mockRow;
  }

  @Nested
  class CreateApiKey {

    @Test
    void shouldCreateApiKeySuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.property(MySQLClient.LAST_INSERTED_ID)).thenReturn(42L);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      UserApiKey result = userApiKeyDao.createApiKey("user-1", "Label", "digest", "prefix").blockingGet();

      assertNotNull(result);
      assertEquals(42L, result.getId());
      assertEquals("user-1", result.getUserId());
      assertEquals("Label", result.getDisplayName());
      assertEquals("digest", result.getApiKeyHash());
      assertEquals("prefix", result.getKeyPrefix());
      assertTrue(result.getIsActive());
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class,
          () -> userApiKeyDao.createApiKey("u", "d", "h", "p").blockingGet());
    }
  }

  @Nested
  class FindActiveByHash {

    @Test
    void shouldReturnKeyWhenFound() {
      setupReaderPreparedQuery();
      Row keyRow = createMockUserApiKeyRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(keyRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      UserApiKey result = userApiKeyDao.findActiveByHash("digest").blockingGet();

      assertNotNull(result);
      assertEquals(1L, result.getId());
      assertEquals("user-1", result.getUserId());
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      UserApiKey result = userApiKeyDao.findActiveByHash("missing").blockingGet();

      assertNull(result);
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> userApiKeyDao.findActiveByHash("h").blockingGet());
    }
  }

  @Nested
  class FindActiveByUser {

    @Test
    void shouldListKeys() {
      setupReaderPreparedQuery();
      Row keyRow = createMockUserApiKeyRow();
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(keyRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<UserApiKey> result = userApiKeyDao.findActiveByUser("user-1").blockingGet();

      assertEquals(1, result.size());
      assertEquals("user-1", result.get(0).getUserId());
    }

    @Test
    void shouldReturnEmptyListWhenNoRows() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<UserApiKey> result = userApiKeyDao.findActiveByUser("user-empty").blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> userApiKeyDao.findActiveByUser("u").blockingGet());
    }
  }

  @Nested
  class Revoke {

    @Test
    void shouldRevokeSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      userApiKeyDao.revoke(1L, "user-1", "user-1").blockingAwait();
    }

    @Test
    void shouldErrorWhenKeyNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      RuntimeException ex = assertThrows(RuntimeException.class,
          () -> userApiKeyDao.revoke(99L, "user-1", "user-1").blockingAwait());

      assertTrue(ex.getMessage().contains("API key not found"));
    }

    @Test
    void shouldThrowOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      assertThrows(RuntimeException.class, () -> userApiKeyDao.revoke(1L, "u", "u").blockingAwait());
    }
  }

  @Nested
  class MapRowNullTimestamps {

    @Test
    void shouldMapNullCreatedAtAndRevokedAt() {
      setupReaderPreparedQuery();
      Row mockRow = mock(Row.class);
      when(mockRow.getLong("id")).thenReturn(5L);
      when(mockRow.getString("user_id")).thenReturn("u");
      when(mockRow.getString("display_name")).thenReturn("d");
      when(mockRow.getString("api_key_hash")).thenReturn("h");
      when(mockRow.getString("key_prefix")).thenReturn("p");
      when(mockRow.getBoolean("is_active")).thenReturn(true);
      when(mockRow.getLocalDateTime("created_at")).thenReturn(null);
      when(mockRow.getLocalDateTime("revoked_at")).thenReturn(null);
      when(mockRow.getString("revoked_by")).thenReturn(null);
      RowIterator<Row> iterator = createMockRowIterator(Collections.singletonList(mockRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      UserApiKey result = userApiKeyDao.findActiveByHash("h").blockingGet();

      assertNotNull(result);
      assertEquals(5L, result.getId());
      assertTrue(result.getCreatedAt() == null);
      assertTrue(result.getRevokedAt() == null);
    }
  }
}
