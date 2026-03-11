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

/**
 * Connection pool manager for per-project ClickHouse access.
 * Each project gets its own connection pool with project-specific credentials.
 * 
 * This enables database-enforced data isolation via row-level policies.
 */
@Slf4j
@Singleton
public class ClickhouseProjectConnectionPoolManager {
    
    private final ClickhouseConfig baseConfig;
    
    // Thread-safe cache with per-project locks
    private final ConcurrentHashMap<String, PoolWrapper> poolCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReadWriteLock> projectLocks = new ConcurrentHashMap<>();
    
    // Configuration constants
    private static final int MIN_POOL_SIZE = 5;
    private static final int MAX_POOL_SIZE = 10;
    private static final int ADMIN_POOL_SIZE = 2;
    private static final Duration MAX_IDLE_TIME = Duration.ofMinutes(5);
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);
    private static final String ADMIN_KEY = "ADMIN";
    
    @Inject
    public ClickhouseProjectConnectionPoolManager(ClickhouseConfig baseConfig) {
        this.baseConfig = baseConfig;
        initializeAdminPool();
    }
    
    /**
     * Initialize admin connection pool for DDL operations.
     */
    private void initializeAdminPool() {
        try {
            ConnectionPool pool = createPool(
                baseConfig.getUsername(),
                baseConfig.getPassword(),
                ADMIN_POOL_SIZE,
                ADMIN_POOL_SIZE
            );
            
            poolCache.put(ADMIN_KEY, new PoolWrapper(pool, true));
            log.info("Admin connection pool created: {} connections", ADMIN_POOL_SIZE);
        } catch (Exception e) {
            log.error("Failed to create admin pool", e);
            throw new RuntimeException("Cannot initialize admin pool", e);
        }
    }
    
    /**
     * Get or create connection pool for a project.
     * 
     * @param projectId Project ID
     * @param clickhouseUsername Project-specific ClickHouse username
     * @param clickhousePassword Project-specific password
     * @return ConnectionPool for the project
     */
    public ConnectionPool getPoolForProject(
            String projectId,
            String clickhouseUsername,
            String clickhousePassword) {
        
        PoolWrapper existing = poolCache.get(projectId);
        if (existing != null && !existing.isClosed) {
            log.debug("Using cached pool for project: {}", projectId);
            return existing.pool;
        }
        
        ReadWriteLock lock = projectLocks.computeIfAbsent(
            projectId, 
            k -> new ReentrantReadWriteLock()
        );
        
        lock.writeLock().lock();
        try {
            // Double-check after acquiring lock
            existing = poolCache.get(projectId);
            if (existing != null && !existing.isClosed) {
                return existing.pool;
            }
            
            // Create new pool
            ConnectionPool pool = createPool(
                clickhouseUsername,
                clickhousePassword,
                MIN_POOL_SIZE,
                MAX_POOL_SIZE
            );
            
            poolCache.put(projectId, new PoolWrapper(pool, false));
            log.info("Created connection pool for project: {} with {} max connections",
                projectId, MAX_POOL_SIZE);
            
            return pool;
            
        } catch (Exception e) {
            log.error("Failed to create pool for project: {}", projectId, e);
            throw new RuntimeException("Cannot create project connection pool", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * Create a connection pool with specified credentials.
     */
    private ConnectionPool createPool(
            String username,
            String password,
            int initSize,
            int maxSize) {
        
        try {
            ConnectionFactoryOptions options = ConnectionFactoryOptions.builder()
                .from(ConnectionFactoryOptions.parse(baseConfig.getR2dbcUrl()))
                .option(USER, username)
                .option(PASSWORD, password)
                .build();
            
            ConnectionFactory connectionFactory = ConnectionFactories.get(options);
            
            ConnectionPoolConfiguration poolConfig = ConnectionPoolConfiguration.builder(connectionFactory)
                .initialSize(initSize)
                .maxSize(maxSize)
                .maxIdleTime(MAX_IDLE_TIME)
                .maxAcquireTime(CONNECTION_TIMEOUT)
                .build();
            
            log.debug("Created connection pool: user={}, initSize={}, maxSize={}",
                username, initSize, maxSize);
            
            return new ConnectionPool(poolConfig);
            
        } catch (Exception e) {
            log.error("Failed to create connection pool", e);
            throw new RuntimeException("Connection pool creation failed", e);
        }
    }
    
    /**
     * Get admin connection pool for DDL operations.
     */
    public ConnectionPool getAdminPool() {
        PoolWrapper wrapper = poolCache.get(ADMIN_KEY);
        if (wrapper == null || wrapper.isClosed) {
            throw new RuntimeException("Admin pool not available");
        }
        return wrapper.pool;
    }
    
    /**
     * Close connection pool for a project.
     */
    public void closePoolForProject(String projectId) {
        ReadWriteLock lock = projectLocks.get(projectId);
        if (lock != null) {
            lock.writeLock().lock();
            try {
                PoolWrapper wrapper = poolCache.remove(projectId);
                if (wrapper != null) {
                    wrapper.pool.dispose();
                    wrapper.isClosed = true;
                    log.info("Closed connection pool for project: {}", projectId);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
    
    /**
     * Close all connection pools.
     */
    public void closeAllPools() {
        poolCache.forEach((projectId, wrapper) -> {
            if (!wrapper.isClosed) {
                wrapper.pool.dispose();
                wrapper.isClosed = true;
            }
        });
        poolCache.clear();
        projectLocks.clear();
        log.info("Closed all connection pools");
    }
    
    /**
     * Get pool statistics for a project.
     */
    public PoolStatistics getPoolStatistics(String projectId) {
        PoolWrapper wrapper = poolCache.get(projectId);
        if (wrapper == null) {
            return new PoolStatistics(projectId, 0, 0, false);
        }
        return new PoolStatistics(projectId, poolCache.size(), MAX_POOL_SIZE, !wrapper.isClosed);
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
        public final String projectId;
        public final int activeConnections;
        public final int maxConnections;
        public final boolean isActive;
        
        public PoolStatistics(String projectId, int activeConnections, int maxConnections, boolean isActive) {
            this.projectId = projectId;
            this.activeConnections = activeConnections;
            this.maxConnections = maxConnections;
            this.isActive = isActive;
        }
    }
}
