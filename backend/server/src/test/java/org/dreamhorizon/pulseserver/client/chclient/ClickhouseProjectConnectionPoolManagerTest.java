package org.dreamhorizon.pulseserver.client.chclient;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for ClickhouseProjectConnectionPoolManager.
 * Note: Methods requiring ConnectionPool (getPoolForProject, getAdminPool, closeAllPools)
 * are not unit-tested here due to R2DBC/Reactor type mocking difficulties.
 * PoolStatistics is tested as it is a public value type.
 */
class ClickhouseProjectConnectionPoolManagerTest {

  @Nested
  class PoolStatistics {

    @Test
    void shouldHoldProjectIdAndConnectionInfo() {
      var stats = new ClickhouseProjectConnectionPoolManager.PoolStatistics(
          "proj_123", 5, 10, true);

      assertThat(stats.projectId).isEqualTo("proj_123");
      assertThat(stats.activeConnections).isEqualTo(5);
      assertThat(stats.maxConnections).isEqualTo(10);
      assertThat(stats.isActive).isTrue();
    }

    @Test
    void shouldRepresentInactivePool() {
      var stats = new ClickhouseProjectConnectionPoolManager.PoolStatistics(
          "proj_456", 0, 0, false);

      assertThat(stats.projectId).isEqualTo("proj_456");
      assertThat(stats.activeConnections).isEqualTo(0);
      assertThat(stats.maxConnections).isEqualTo(0);
      assertThat(stats.isActive).isFalse();
    }

    @Test
    void shouldRepresentNonExistentProject() {
      var stats = new ClickhouseProjectConnectionPoolManager.PoolStatistics(
          "unknown", 0, 0, false);

      assertThat(stats.projectId).isEqualTo("unknown");
      assertThat(stats.isActive).isFalse();
    }
  }
}
