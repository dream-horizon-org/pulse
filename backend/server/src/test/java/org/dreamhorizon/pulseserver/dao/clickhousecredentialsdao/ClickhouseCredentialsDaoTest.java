package org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLException;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.PreparedQuery;
import io.vertx.rxjava3.sqlclient.Query;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.RowIterator;
import io.vertx.rxjava3.sqlclient.RowSet;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhousecredentialsdao.models.ClickhouseTenantCredentialAudit;
import org.dreamhorizon.pulseserver.service.tenant.TenantAuditAction;
import org.dreamhorizon.pulseserver.util.PasswordEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
@SuppressWarnings("unchecked")
class ClickhouseCredentialsDaoTest {

  @Mock
  MysqlClient mysqlClient;

  @Mock
  PasswordEncryptionUtil encryptionUtil;

  @Mock
  MySQLPool writerPool;

  @Mock
  MySQLPool readerPool;

  @Mock
  PreparedQuery<RowSet<Row>> preparedQuery;

  @Mock
  Query<RowSet<Row>> query;

  @Mock
  RowSet<Row> rowSet;

  ClickhouseCredentialsDao credentialsDao;

  @BeforeEach
  void setup() {
    credentialsDao = new ClickhouseCredentialsDao(mysqlClient, encryptionUtil);
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

  private Row createMockCredentialsRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("credential_id")).thenReturn(1L);
    when(mockRow.getString("tenant_id")).thenReturn("test_tenant");
    when(mockRow.getString("clickhouse_username")).thenReturn("tenant_test_tenant");
    when(mockRow.getString("clickhouse_password_encrypted")).thenReturn("encrypted_password");
    when(mockRow.getString("encryption_salt")).thenReturn("salt123");
    when(mockRow.getString("password_digest")).thenReturn("digest123");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(now);
    return mockRow;
  }

  private Row createMockAuditRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getLong("audit_id")).thenReturn(1L);
    when(mockRow.getString("tenant_id")).thenReturn("test_tenant");
    when(mockRow.getString("action")).thenReturn("CREDENTIALS_CREATED");
    when(mockRow.getString("performed_by")).thenReturn("admin@example.com");
    when(mockRow.getString("details")).thenReturn("{\"action\":\"test\"}");
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    return mockRow;
  }

  @Nested
  class TestSaveTenantCredentials {

    @Test
    void shouldSaveCredentialsSuccessfully() {
      PasswordEncryptionUtil.EncryptedPassword encrypted = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("encrypted_pass")
          .salt("salt123")
          .digest("digest123")
          .build();

      when(encryptionUtil.encryptPassword(anyString())).thenReturn(encrypted);
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ClickhouseCredentials result = credentialsDao.saveTenantCredentials("test_tenant", "plain_password").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("tenant_test_tenant", result.getClickhouseUsername());
      assertTrue(result.getIsActive());
    }

    @Test
    void shouldThrowExceptionOnEncryptionError() {
      when(encryptionUtil.encryptPassword(anyString()))
          .thenThrow(new RuntimeException("Encryption failed"));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.saveTenantCredentials("test_tenant", "password").blockingGet());
      assertTrue(ex.getMessage().contains("Encryption failed"));
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      PasswordEncryptionUtil.EncryptedPassword encrypted = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("encrypted_pass")
          .salt("salt123")
          .digest("digest123")
          .build();

      when(encryptionUtil.encryptPassword(anyString())).thenReturn(encrypted);
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 400, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.saveTenantCredentials("test_tenant", "password").blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetCredentialsByTenantId {

    @Test
    void shouldGetCredentialsSuccessfully() {
      setupReaderPreparedQuery();
      Row credRow = createMockCredentialsRow();

      when(encryptionUtil.decryptPassword(anyString())).thenReturn("decrypted_password");

      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(credRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ClickhouseCredentials result = credentialsDao.getCredentialsByTenantId("test_tenant").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("tenant_test_tenant", result.getClickhouseUsername());
      assertEquals("decrypted_password", result.getClickhousePassword());
    }

    @Test
    void shouldThrowExceptionWhenCredentialsNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.getCredentialsByTenantId("non_existent").blockingGet());
      assertTrue(ex.getMessage().contains("No credentials found"));
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.getCredentialsByTenantId("test_tenant").blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetCredentialsByTenantIdIncludingInactive {

    @Test
    void shouldGetCredentialsIncludingInactive() {
      setupReaderPreparedQuery();
      Row credRow = createMockCredentialsRow();
      when(credRow.getBoolean("is_active")).thenReturn(false);

      when(encryptionUtil.decryptPassword(anyString())).thenReturn("decrypted_password");

      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(credRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ClickhouseCredentials result = credentialsDao.getCredentialsByTenantIdIncludingInactive("test_tenant").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
    }

    @Test
    void shouldReturnEmptyWhenNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      ClickhouseCredentials result = credentialsDao.getCredentialsByTenantIdIncludingInactive("non_existent").blockingGet();

      // Maybe.empty() returns null
      assertEquals(null, result);
    }
  }

  @Nested
  class TestGetAllActiveTenantCredentials {

    @Test
    void shouldGetAllActiveCredentials() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);

      Row credRow = createMockCredentialsRow();
      when(encryptionUtil.decryptPassword(anyString())).thenReturn("decrypted_password");

      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(credRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<ClickhouseCredentials> result = credentialsDao.getAllActiveTenantCredentials().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("test_tenant", result.get(0).getTenantId());
    }

    @Test
    void shouldReturnEmptyListWhenNoCredentials() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);

      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<ClickhouseCredentials> result = credentialsDao.getAllActiveTenantCredentials().toList().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);
      when(query.rxExecute())
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.getAllActiveTenantCredentials().toList().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestUpdateTenantCredentials {

    @Test
    void shouldUpdateCredentialsSuccessfully() {
      PasswordEncryptionUtil.EncryptedPassword encrypted = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("new_encrypted_pass")
          .salt("new_salt")
          .digest("new_digest")
          .build();

      when(encryptionUtil.encryptPassword(anyString())).thenReturn(encrypted);
      when(encryptionUtil.decryptPassword(anyString())).thenReturn("new_password");

      setupWriterPreparedQuery();
      setupReaderPool();
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);

      // Update returns 1 row affected
      RowSet<Row> updateRowSet = mock(RowSet.class);
      when(updateRowSet.rowCount()).thenReturn(1);

      // Get returns credentials
      Row credRow = createMockCredentialsRow();
      RowSet<Row> getRowSet = mock(RowSet.class);
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(credRow));
      when(getRowSet.size()).thenReturn(1);
      when(getRowSet.iterator()).thenReturn(iterator);

      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(updateRowSet))
          .thenReturn(Single.just(getRowSet));

      ClickhouseCredentials result = credentialsDao.updateTenantCredentials("test_tenant", "new_password").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
    }

    @Test
    void shouldThrowExceptionWhenCredentialsNotFound() {
      PasswordEncryptionUtil.EncryptedPassword encrypted = PasswordEncryptionUtil.EncryptedPassword.builder()
          .encryptedPassword("encrypted")
          .salt("salt")
          .digest("digest")
          .build();

      when(encryptionUtil.encryptPassword(anyString())).thenReturn(encrypted);
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.updateTenantCredentials("non_existent", "password").blockingGet());
      assertTrue(ex.getMessage().contains("No credentials found"));
    }

    @Test
    void shouldThrowExceptionOnEncryptionError() {
      when(encryptionUtil.encryptPassword(anyString()))
          .thenThrow(new RuntimeException("Encryption failed"));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.updateTenantCredentials("test_tenant", "password").blockingGet());
      assertTrue(ex.getMessage().contains("Encryption failed"));
    }
  }

  @Nested
  class TestDeactivateTenantCredentials {

    @Test
    void shouldDeactivateCredentialsSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      credentialsDao.deactivateTenantCredentials("test_tenant").blockingAwait();
    }

    @Test
    void shouldCompleteEvenWhenCredentialsNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      // Should still complete (just logs a warning)
      credentialsDao.deactivateTenantCredentials("non_existent").blockingAwait();
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.deactivateTenantCredentials("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestReactivateTenantCredentials {

    @Test
    void shouldReactivateCredentialsSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      credentialsDao.reactivateTenantCredentials("test_tenant").blockingAwait();
    }

    @Test
    void shouldThrowExceptionWhenCredentialsNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.reactivateTenantCredentials("non_existent").blockingAwait());
      assertTrue(ex.getMessage().contains("No credentials found"));
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.reactivateTenantCredentials("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestInsertAuditLog {

    @Test
    void shouldInsertAuditLogSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      when(preparedQuery.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      JsonObject details = new JsonObject().put("action", "test");
      credentialsDao.insertAuditLog("test_tenant", TenantAuditAction.CREDENTIALS_CREATED, "admin@example.com", details).blockingAwait();

      Tuple tuple = tupleCaptor.getValue();
      assertEquals("test_tenant", tuple.getString(0));
      assertEquals("CREDENTIALS_CREATED", tuple.getString(1));
      assertEquals("admin@example.com", tuple.getString(2));
    }

    @Test
    void shouldHandleNullDetails() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      credentialsDao.insertAuditLog("test_tenant", TenantAuditAction.CREDENTIALS_UPDATED, "admin@example.com", null).blockingAwait();
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.insertAuditLog("test_tenant", TenantAuditAction.CREDENTIALS_CREATED, "admin", null).blockingAwait());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAuditLogsByTenantId {

    @Test
    void shouldGetAuditLogsSuccessfully() {
      setupReaderPreparedQuery();
      Row auditRow = createMockAuditRow();

      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(auditRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ClickhouseTenantCredentialAudit> result = credentialsDao.getAuditLogsByTenantId("test_tenant").toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("test_tenant", result.get(0).getTenantId());
      assertEquals("CREDENTIALS_CREATED", result.get(0).getAction());
    }

    @Test
    void shouldReturnEmptyListWhenNoAuditLogs() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ClickhouseTenantCredentialAudit> result = credentialsDao.getAuditLogsByTenantId("test_tenant").toList().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.getAuditLogsByTenantId("test_tenant").toList().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetRecentAuditLogs {

    @Test
    void shouldGetRecentAuditLogsSuccessfully() {
      setupReaderPreparedQuery();
      Row auditRow1 = createMockAuditRow();
      Row auditRow2 = createMockAuditRow();
      when(auditRow2.getLong("audit_id")).thenReturn(2L);

      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(auditRow1, auditRow2));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ClickhouseTenantCredentialAudit> result = credentialsDao.getRecentAuditLogs(10).toList().blockingGet();

      assertNotNull(result);
      assertEquals(2, result.size());
    }

    @Test
    void shouldReturnEmptyListWhenNoAuditLogs() {
      setupReaderPreparedQuery();
      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      List<ClickhouseTenantCredentialAudit> result = credentialsDao.getRecentAuditLogs(10).toList().blockingGet();

      assertNotNull(result);
      assertTrue(result.isEmpty());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> credentialsDao.getRecentAuditLogs(10).toList().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }
}
