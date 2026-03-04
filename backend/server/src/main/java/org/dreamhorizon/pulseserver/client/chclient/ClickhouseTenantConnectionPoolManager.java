package org.dreamhorizon.pulseserver.client.chclient;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;

import static io.r2dbc.spi.ConnectionFactoryOptions.PASSWORD;
import static io.r2dbc.spi.ConnectionFactoryOptions.USER;


@Slf4j
@Singleton
public class ClickhouseTenantConnectionPoolManager {
  private final ClickhouseConfig baseConfig;

  // Thread-safe cache with per-tenant locks
  private final ConcurrentHashMap<String, PoolWrapper> poolCache = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, ReadWriteLock> tenantLocks = new ConcurrentHashMap<>();

  // Configuration constants
  private static final int MIN_POOL_SIZE = 5;
  private static final int MAX_POOL_SIZE = 10;
  private static final int ADMIN_POOL_SIZE = 2;
  private static final Duration MAX_IDLE_TIME = Duration.ofMinutes(5);
  private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);
  private static final String admin = "ADMIN";

  @Inject
  public ClickhouseTenantConnectionPoolManager(ClickhouseConfig baseConfig) {
    this.baseConfig = baseConfig;
    initializeAdminPool();
  }

  private void initializeAdminPool() {
    try {
      ConnectionPool pool =
          createPool(
              baseConfig.getUsername(),
              baseConfig.getPassword(),
              ADMIN_POOL_SIZE,
              ADMIN_POOL_SIZE);

      poolCache.put(admin, new PoolWrapper(pool, true));
      log.info("Admin connection pool created: {} connections", ADMIN_POOL_SIZE);
    } catch (Exception e) {
      log.error("Failed to create admin pool", e);
      throw new RuntimeException("Cannot initialize admin pool", e);
    }
  }

  public ConnectionPool getPoolForTenant(
      String tenantId, String clickhouseUsername, String clickhousePassword) {

    PoolWrapper existing = poolCache.get(tenantId);
    if (existing != null && !existing.isClosed) {
      log.debug("Using cached pool for tenant: {}", tenantId);
      return existing.pool;
    }

    ReadWriteLock lock =
        tenantLocks.computeIfAbsent(tenantId, k -> new ReentrantReadWriteLock());
    lock.writeLock().lock();
    try {
      // Double-check after acquiring lock
      existing = poolCache.get(tenantId);
      if (existing != null && !existing.isClosed) {
        return existing.pool;
      }

      // Create new pool
      ConnectionPool pool =
          createPool(clickhouseUsername, clickhousePassword, MIN_POOL_SIZE, MAX_POOL_SIZE);

      poolCache.put(tenantId, new PoolWrapper(pool, false));
      log.info(
          "Created new connection pool for tenant: {} with {} max connections",
          tenantId,
          MAX_POOL_SIZE);

      return pool;
    } catch (Exception e) {
      log.error("Failed to create pool for tenant: {}", tenantId, e);
      throw new RuntimeException("Cannot create tenant connection pool", e);
    } finally {
      lock.writeLock().unlock();
    }
  }

  private ConnectionPool createPool(
      String username, String password, int initSize, int maxSize) {
    try {
      ConnectionFactoryOptions options =
          ConnectionFactoryOptions.builder()
              .from(ConnectionFactoryOptions.parse(baseConfig.getR2dbcUrl()))
              .option(USER, username)
              .option(PASSWORD, password)
              .build();

      ConnectionFactory connectionFactory = ConnectionFactories.get(options);

      ConnectionPoolConfiguration poolConfig =
          ConnectionPoolConfiguration.builder(connectionFactory)
              .initialSize(initSize)
              .maxSize(maxSize)
              .maxIdleTime(MAX_IDLE_TIME)
              .maxAcquireTime(CONNECTION_TIMEOUT)
              .build();

      log.debug(
          "Created connection pool: user={}, initSize={}, maxSize={}",
          username,
          initSize,
          maxSize);
      return new ConnectionPool(poolConfig);
    } catch (Exception e) {
      log.error("Failed to create connection pool", e);
      throw new RuntimeException("Connection pool creation failed", e);
    }
  }

  public ConnectionPool getAdminPool() {
    PoolWrapper wrapper = poolCache.get(admin);
    if (wrapper == null || wrapper.isClosed) {
      throw new RuntimeException("Admin pool not available");
    }
    return wrapper.pool;
  }

  public void closePoolForTenant(String tenantId) {
    ReadWriteLock lock = tenantLocks.get(tenantId);
    if (lock != null) {
      lock.writeLock().lock();
      try {
        PoolWrapper wrapper = poolCache.remove(tenantId);
        if (wrapper != null) {
          wrapper.pool.dispose();
          wrapper.isClosed = true;
          log.info("Closed connection pool for tenant: {}", tenantId);
        }
      } finally {
        lock.writeLock().unlock();
      }
    }
  }

  public void closeAllPools() {
    poolCache.forEach(
        (tenantId, wrapper) -> {
          if (!wrapper.isClosed) {
            wrapper.pool.dispose();
            wrapper.isClosed = true;
          }
        });
    poolCache.clear();
    tenantLocks.clear();
    log.info("Closed all connection pools");
  }

  public PoolStatistics getPoolStatistics(String tenantId) {
    PoolWrapper wrapper = poolCache.get(tenantId);
    if (wrapper == null) {
      return new PoolStatistics(tenantId, 0, 0, false);
    }

    return new PoolStatistics(tenantId, poolCache.size(), MAX_POOL_SIZE, !wrapper.isClosed);
  }

  private static class PoolWrapper {
    ConnectionPool pool;
    volatile boolean isClosed;

    PoolWrapper(ConnectionPool pool, boolean isAdmin) {
      this.pool = pool;
      this.isClosed = false;
    }
  }

  public static class PoolStatistics {
    public final String tenantId;
    public final int activeConnections;
    public final int maxConnections;
    public final boolean isActive;

    public PoolStatistics(String tenantId, int activeConnections, int maxConnections, boolean isActive) {
      this.tenantId = tenantId;
      this.activeConnections = activeConnections;
      this.maxConnections = maxConnections;
      this.isActive = isActive;
    }
  }
}
