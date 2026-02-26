package org.dreamhorizon.pulseserver.client.chclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import io.r2dbc.pool.ConnectionPool;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ClickhouseTenantConnectionPoolManagerTest {

  @Mock
  private ClickhouseConfig clickhouseConfig;

  private ClickhouseTenantConnectionPoolManager poolManager;

  @Nested
  class TestConstructor {

    @Test
    void shouldThrowExceptionWhenConfigIsInvalid() {
      // Constructor now calls initializeAdminPool(), so invalid config should throw
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("invalid://url");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForNullUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn(null);
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForNullUsername() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("r2dbc:clickhouse://localhost:8123");
      when(clickhouseConfig.getUsername()).thenReturn(null);
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForNullPassword() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("r2dbc:clickhouse://localhost:8123");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn(null);

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForEmptyUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }
  }

  @Nested
  class TestPoolStatistics {

    @Test
    void shouldCreatePoolStatisticsWithAllFields() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "tenant_1", 5, 10, true
          );

      assertEquals("tenant_1", stats.tenantId);
      assertEquals(5, stats.activeConnections);
      assertEquals(10, stats.maxConnections);
      assertTrue(stats.isActive);
    }

    @Test
    void shouldCreatePoolStatisticsWithInactivePool() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "tenant_2", 0, 10, false
          );

      assertEquals("tenant_2", stats.tenantId);
      assertEquals(0, stats.activeConnections);
      assertEquals(10, stats.maxConnections);
      assertFalse(stats.isActive);
    }

    @Test
    void shouldHandleZeroConnections() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "empty_tenant", 0, 0, false
          );

      assertEquals(0, stats.activeConnections);
      assertEquals(0, stats.maxConnections);
      assertFalse(stats.isActive);
    }

    @Test
    void shouldHandleNullTenantId() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              null, 0, 0, false
          );

      assertEquals(null, stats.tenantId);
    }

    @Test
    void shouldAccessAllPublicFields() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "test_tenant", 3, 15, true
          );

      // Verify all public fields are accessible
      assertEquals("test_tenant", stats.tenantId);
      assertEquals(3, stats.activeConnections);
      assertEquals(15, stats.maxConnections);
      assertTrue(stats.isActive);
    }

    @Test
    void shouldCreatePoolStatisticsWithVariousValues() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats1 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t1", 0, 0, false);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats2 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t2", 5, 10, true);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats3 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t3", 10, 10, true);

      assertEquals("t1", stats1.tenantId);
      assertEquals("t2", stats2.tenantId);
      assertEquals("t3", stats3.tenantId);
      
      assertEquals(0, stats1.activeConnections);
      assertEquals(5, stats2.activeConnections);
      assertEquals(10, stats3.activeConnections);
      
      assertFalse(stats1.isActive);
      assertTrue(stats2.isActive);
      assertTrue(stats3.isActive);
    }
  }

  @Nested
  class TestGetPoolForTenantErrors {

    @Test
    void shouldThrowExceptionForInvalidConfig() {
      // When constructor fails, we can't test getPoolForTenant on the instance
      // So we test that constructor throws with invalid config
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("invalid://url");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }
  }

  @Nested
  class TestExceptionMessages {

    @Test
    void shouldThrowWithCannotInitializeMessage() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("invalid://url");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      RuntimeException ex = assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
      
      assertTrue(ex.getMessage().contains("Cannot initialize admin pool"));
    }

    @Test
    void shouldThrowWithMalformedUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("malformed-url-without-protocol");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowWithSpecialCharactersInUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("r2dbc://host:port/db?param=value&other=<>!@#");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }
  }

  @Nested
  class TestPoolStatisticsEdgeCases {

    @Test
    void shouldHandleNegativeValues() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "tenant", -1, -1, false
          );

      assertEquals(-1, stats.activeConnections);
      assertEquals(-1, stats.maxConnections);
    }

    @Test
    void shouldHandleMaxIntegerValues() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "tenant", Integer.MAX_VALUE, Integer.MAX_VALUE, true
          );

      assertEquals(Integer.MAX_VALUE, stats.activeConnections);
      assertEquals(Integer.MAX_VALUE, stats.maxConnections);
    }

    @Test
    void shouldHandleEmptyTenantId() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              "", 0, 0, false
          );

      assertEquals("", stats.tenantId);
    }

    @Test
    void shouldHandleLongTenantId() {
      String longTenantId = "a".repeat(1000);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              longTenantId, 5, 10, true
          );

      assertEquals(longTenantId, stats.tenantId);
    }

    @Test
    void shouldHandleSpecialCharactersInTenantId() {
      String specialTenantId = "tenant-with_special.chars@123";
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              specialTenantId, 5, 10, true
          );

      assertEquals(specialTenantId, stats.tenantId);
    }

    @Test
    void shouldHandleUnicodeTenantId() {
      String unicodeTenantId = "租户_テナント_tenant";
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(
              unicodeTenantId, 5, 10, true
          );

      assertEquals(unicodeTenantId, stats.tenantId);
    }
  }

  @Nested
  class TestConstructorEdgeCases {

    @Test
    void shouldThrowExceptionForWhitespaceUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("   ");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForInvalidProtocol() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("http://localhost:8123");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForIncompleteUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("r2dbc:");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForUrlWithInvalidHost() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("r2dbc:clickhouse://");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }
  }

  @Nested
  class TestWithValidPool {
    // These tests use a real ClickHouse R2DBC URL format
    // The pool creation will succeed even if ClickHouse is not running
    // because connection pools are lazy by default
    
    private ClickhouseTenantConnectionPoolManager validPoolManager;
    private ClickhouseConfig realConfig;

    @AfterEach
    void cleanup() {
      if (validPoolManager != null) {
        try {
          validPoolManager.closeAllPools();
        } catch (Exception e) {
          // Ignore cleanup errors
        }
      }
    }

    private ClickhouseTenantConnectionPoolManager createValidPoolManager() {
      realConfig = new ClickhouseConfig();
      realConfig.setR2dbcUrl("r2dbc:clickhouse:http://localhost:8123/default");
      realConfig.setUsername("default");
      realConfig.setPassword("");
      return new ClickhouseTenantConnectionPoolManager(realConfig);
    }

    @Test
    void shouldCreatePoolManagerSuccessfully() {
      validPoolManager = createValidPoolManager();
      assertNotNull(validPoolManager);
    }

    @Test
    void shouldGetAdminPoolSuccessfully() {
      validPoolManager = createValidPoolManager();
      ConnectionPool adminPool = validPoolManager.getAdminPool();
      assertNotNull(adminPool);
    }

    @Test
    void shouldCreateTenantPoolSuccessfully() {
      validPoolManager = createValidPoolManager();
      ConnectionPool tenantPool = validPoolManager.getPoolForTenant(
          "test_tenant", "test_user", "test_password");
      assertNotNull(tenantPool);
    }

    @Test
    void shouldReturnCachedPoolForSameTenant() {
      validPoolManager = createValidPoolManager();
      ConnectionPool pool1 = validPoolManager.getPoolForTenant(
          "cached_tenant", "user1", "pass1");
      ConnectionPool pool2 = validPoolManager.getPoolForTenant(
          "cached_tenant", "user1", "pass1");
      assertEquals(pool1, pool2);
    }

    @Test
    void shouldGetPoolStatisticsForExistingTenant() {
      validPoolManager = createValidPoolManager();
      validPoolManager.getPoolForTenant("stats_tenant", "user", "pass");
      
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          validPoolManager.getPoolStatistics("stats_tenant");
      
      assertNotNull(stats);
      assertTrue(stats.isActive);
      assertEquals("stats_tenant", stats.tenantId);
    }

    @Test
    void shouldGetPoolStatisticsForNonExistentTenant() {
      validPoolManager = createValidPoolManager();
      
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats = 
          validPoolManager.getPoolStatistics("non_existent");
      
      assertNotNull(stats);
      assertFalse(stats.isActive);
    }

    @Test
    void shouldClosePoolForTenant() {
      validPoolManager = createValidPoolManager();
      validPoolManager.getPoolForTenant("close_tenant", "user", "pass");
      
      assertTrue(validPoolManager.getPoolStatistics("close_tenant").isActive);
      
      validPoolManager.closePoolForTenant("close_tenant");
      
      assertFalse(validPoolManager.getPoolStatistics("close_tenant").isActive);
    }

    @Test
    void shouldClosePoolForNonExistentTenant() {
      validPoolManager = createValidPoolManager();
      // Should not throw
      validPoolManager.closePoolForTenant("non_existent_tenant");
    }

    @Test
    void shouldCloseAllPools() {
      validPoolManager = createValidPoolManager();
      validPoolManager.getPoolForTenant("tenant1", "user1", "pass1");
      validPoolManager.getPoolForTenant("tenant2", "user2", "pass2");
      
      validPoolManager.closeAllPools();
      
      assertFalse(validPoolManager.getPoolStatistics("tenant1").isActive);
      assertFalse(validPoolManager.getPoolStatistics("tenant2").isActive);
    }

    @Test
    void shouldThrowWhenAdminPoolIsClosed() {
      validPoolManager = createValidPoolManager();
      validPoolManager.closeAllPools();
      
      assertThrows(RuntimeException.class, () -> validPoolManager.getAdminPool());
    }
  }
}
