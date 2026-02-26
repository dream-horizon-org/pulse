package org.dreamhorizon.pulseserver.client.chclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ClickhouseTenantConnectionPoolManagerIntegrationTest {

  @Mock
  private ClickhouseConfig clickhouseConfig;

  @Nested
  class TestPoolStatisticsComprehensive {

    @Test
    void shouldCreatePoolStatisticsWithVariousValues() {
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats1 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t1", 0, 0, false);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats2 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t2", 5, 10, true);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats3 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics("t3", 10, 10, true);
      ClickhouseTenantConnectionPoolManager.PoolStatistics stats4 = 
          new ClickhouseTenantConnectionPoolManager.PoolStatistics(null, 0, 0, false);

      assertEquals("t1", stats1.tenantId);
      assertEquals("t2", stats2.tenantId);
      assertEquals("t3", stats3.tenantId);
      assertEquals(null, stats4.tenantId);
      
      assertEquals(0, stats1.activeConnections);
      assertEquals(5, stats2.activeConnections);
      assertEquals(10, stats3.activeConnections);
      
      assertEquals(0, stats1.maxConnections);
      assertEquals(10, stats2.maxConnections);
      assertEquals(10, stats3.maxConnections);
      
      assertFalse(stats1.isActive);
      assertTrue(stats2.isActive);
      assertTrue(stats3.isActive);
      assertFalse(stats4.isActive);
    }
  }

  @Nested
  class TestConstructorWithInvalidConfig {

    @Test
    void shouldThrowExceptionForMalformedUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("not-a-valid-url");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      // Constructor now calls initializeAdminPool(), so it should throw
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
    void shouldThrowExceptionForEmptyUrl() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }

    @Test
    void shouldThrowExceptionForNullCredentials() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("r2dbc:clickhouse://localhost:8123");
      when(clickhouseConfig.getUsername()).thenReturn(null);
      when(clickhouseConfig.getPassword()).thenReturn(null);

      assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
    }
  }

  @Nested
  class TestExceptionMessages {

    @Test
    void shouldHaveCorrectExceptionMessage() {
      when(clickhouseConfig.getR2dbcUrl()).thenReturn("invalid://url");
      when(clickhouseConfig.getUsername()).thenReturn("admin");
      when(clickhouseConfig.getPassword()).thenReturn("password");

      RuntimeException ex = assertThrows(RuntimeException.class, 
          () -> new ClickhouseTenantConnectionPoolManager(clickhouseConfig));
      
      assertTrue(ex.getMessage().contains("Cannot initialize admin pool"));
    }
  }
}
