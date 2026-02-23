package org.dreamhorizon.pulseserver.dao.tenantdao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
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
import org.dreamhorizon.pulseserver.dao.tenantdao.models.Tenant;
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
class TenantDaoTest {

  @Mock
  MysqlClient mysqlClient;

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

  @Mock
  Row row;

  TenantDao tenantDao;

  @BeforeEach
  void setup() {
    tenantDao = new TenantDao(mysqlClient);
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

  private Row createMockTenantRow() {
    Row mockRow = mock(Row.class);
    LocalDateTime now = LocalDateTime.now();
    when(mockRow.getString("tenant_id")).thenReturn("test_tenant");
    when(mockRow.getString("name")).thenReturn("Test Tenant");
    when(mockRow.getString("description")).thenReturn("Test Description");
    when(mockRow.getBoolean("is_active")).thenReturn(true);
    when(mockRow.getLocalDateTime("created_at")).thenReturn(now);
    when(mockRow.getLocalDateTime("updated_at")).thenReturn(now);
    when(mockRow.getString("gcp_tenant_id")).thenReturn("gcp-test-123");
    when(mockRow.getString("domain_name")).thenReturn("test.example.com");
    return mockRow;
  }

  @Nested
  class TestCreateTenant {

    @Test
    void shouldCreateTenantSuccessfully() {
      Tenant tenant = Tenant.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .description("Test Description")
          .gcpTenantId("gcp-test-123")
          .domainName("test.example.com")
          .build();

      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);
      when(preparedQuery.rxExecute(tupleCaptor.capture())).thenReturn(Single.just(rowSet));

      Tenant result = tenantDao.createTenant(tenant).blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("Test Tenant", result.getName());
      assertTrue(result.getIsActive());

      Tuple tuple = tupleCaptor.getValue();
      assertEquals("test_tenant", tuple.getString(0));
      assertEquals("Test Tenant", tuple.getString(1));
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      Tenant tenant = Tenant.builder()
          .tenantId("test_tenant")
          .name("Test Tenant")
          .build();

      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 400, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantDao.createTenant(tenant).blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetTenantById {

    @Test
    void shouldGetTenantSuccessfully() {
      setupReaderPreparedQuery();
      Row tenantRow = createMockTenantRow();

      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(tenantRow));
      when(rowSet.size()).thenReturn(1);
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Tenant result = tenantDao.getTenantById("test_tenant").blockingGet();

      assertNotNull(result);
      assertEquals("test_tenant", result.getTenantId());
      assertEquals("Test Tenant", result.getName());
    }

    @Test
    void shouldReturnEmptyWhenTenantNotFound() {
      setupReaderPreparedQuery();
      when(rowSet.size()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Tenant result = tenantDao.getTenantById("non_existent").blockingGet();

      // Maybe.empty() returns null when blockingGet is called
      assertEquals(null, result);
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantDao.getTenantById("test_tenant").blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAllActiveTenants {

    @Test
    void shouldGetAllActiveTenantsSuccessfully() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);

      Row tenantRow = createMockTenantRow();
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(tenantRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<Tenant> result = tenantDao.getAllActiveTenants().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
      assertEquals("test_tenant", result.get(0).getTenantId());
    }

    @Test
    void shouldReturnEmptyListWhenNoTenants() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);

      RowIterator<Row> iterator = createMockRowIterator(new ArrayList<>());
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<Tenant> result = tenantDao.getAllActiveTenants().toList().blockingGet();

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
          () -> tenantDao.getAllActiveTenants().toList().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestGetAllTenants {

    @Test
    void shouldGetAllTenantsSuccessfully() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);

      Row tenantRow = createMockTenantRow();
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(tenantRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(query.rxExecute()).thenReturn(Single.just(rowSet));

      List<Tenant> result = tenantDao.getAllTenants().toList().blockingGet();

      assertNotNull(result);
      assertEquals(1, result.size());
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupReaderPool();
      when(readerPool.query(anyString())).thenReturn(query);
      when(query.rxExecute())
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantDao.getAllTenants().toList().blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestUpdateTenant {

    @Test
    void shouldUpdateTenantSuccessfully() {
      setupWriterPool();
      when(writerPool.preparedQuery(anyString())).thenReturn(preparedQuery);

      // First call for update
      RowSet<Row> updateRowSet = mock(RowSet.class);
      when(updateRowSet.rowCount()).thenReturn(1);

      // Second call for getTenantById
      Row tenantRow = createMockTenantRow();
      when(tenantRow.getString("name")).thenReturn("Updated Name");
      RowSet<Row> getRowSet = mock(RowSet.class);
      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(tenantRow));
      when(getRowSet.size()).thenReturn(1);
      when(getRowSet.iterator()).thenReturn(iterator);

      when(mysqlClient.getReaderPool()).thenReturn(readerPool);
      when(readerPool.preparedQuery(anyString())).thenReturn(preparedQuery);
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.just(updateRowSet))
          .thenReturn(Single.just(getRowSet));

      Tenant result = tenantDao.updateTenant("test_tenant", "Updated Name", "Updated Description").blockingGet();

      assertNotNull(result);
      assertEquals("Updated Name", result.getName());
    }

    @Test
    void shouldThrowExceptionWhenTenantNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantDao.updateTenant("non_existent", "Name", "Desc").blockingGet());
      assertTrue(ex.getMessage().contains("Tenant not found"));
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantDao.updateTenant("test_tenant", "Name", "Desc").blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestDeactivateTenant {

    @Test
    void shouldDeactivateTenantSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      // Should complete without exception
      tenantDao.deactivateTenant("test_tenant").blockingAwait();
    }

    @Test
    void shouldCompleteEvenWhenTenantNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      // Should still complete (just logs a warning)
      tenantDao.deactivateTenant("non_existent").blockingAwait();
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantDao.deactivateTenant("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestActivateTenant {

    @Test
    void shouldActivateTenantSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      // Should complete without exception
      tenantDao.activateTenant("test_tenant").blockingAwait();
    }

    @Test
    void shouldThrowExceptionWhenTenantNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantDao.activateTenant("non_existent").blockingAwait());
      assertTrue(ex.getMessage().contains("Tenant not found"));
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantDao.activateTenant("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestDeleteTenant {

    @Test
    void shouldDeleteTenantSuccessfully() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(1);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      // Should complete without exception
      tenantDao.deleteTenant("test_tenant").blockingAwait();
    }

    @Test
    void shouldCompleteEvenWhenTenantNotFound() {
      setupWriterPreparedQuery();
      when(rowSet.rowCount()).thenReturn(0);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      // Should still complete (just logs a warning)
      tenantDao.deleteTenant("non_existent").blockingAwait();
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupWriterPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantDao.deleteTenant("test_tenant").blockingAwait());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }

  @Nested
  class TestTenantExists {

    @Test
    void shouldReturnTrueWhenTenantExists() {
      setupReaderPreparedQuery();

      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(1L);

      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = tenantDao.tenantExists("test_tenant").blockingGet();

      assertTrue(result);
    }

    @Test
    void shouldReturnFalseWhenTenantDoesNotExist() {
      setupReaderPreparedQuery();

      Row countRow = mock(Row.class);
      when(countRow.getLong("count")).thenReturn(0L);

      RowIterator<Row> iterator = createMockRowIterator(Arrays.asList(countRow));
      when(rowSet.iterator()).thenReturn(iterator);
      when(preparedQuery.rxExecute(any(Tuple.class))).thenReturn(Single.just(rowSet));

      Boolean result = tenantDao.tenantExists("non_existent").blockingGet();

      assertFalse(result);
    }

    @Test
    void shouldThrowExceptionOnDatabaseError() {
      setupReaderPreparedQuery();
      when(preparedQuery.rxExecute(any(Tuple.class)))
          .thenReturn(Single.error(new MySQLException("DB Error", 500, "SQLSTATE")));

      Exception ex = assertThrows(RuntimeException.class,
          () -> tenantDao.tenantExists("test_tenant").blockingGet());
      assertTrue(ex.getMessage().contains("DB Error"));
    }
  }
}
