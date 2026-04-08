# Dependency Injection & Integration Code Snippets

## 1. Update MainModule.java

Add these provider methods to your MainModule class:

```java
package org.dreamhorizon.pulseserver.module;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.dreamhorizon.pulseserver.client.chclient.TenantConnectionPoolManager;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.config.ClickhouseConfig;
import org.dreamhorizon.pulseserver.dao.interaction.TenantCredentialsDao;
import org.dreamhorizon.pulseserver.service.TenantService;
import org.dreamhorizon.pulseserver.service.interaction.PerformanceMetricService;
import org.dreamhorizon.pulseserver.service.interaction.PerformanceMetricServiceImpl;
import org.dreamhorizon.pulseserver.util.PasswordEncryptionUtil;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;

public class MainModule extends AbstractModule {
    // ... existing code ...

    /**
     * Provides PasswordEncryptionUtil singleton
     * Used for encrypting/decrypting tenant passwords
     */
    @Provides
    @Singleton
    public PasswordEncryptionUtil providePasswordEncryption() {
        String masterKey = System.getenv("ENCRYPTION_MASTER_KEY");
        if (masterKey == null || masterKey.isBlank()) {
            throw new RuntimeException(
                "ENCRYPTION_MASTER_KEY environment variable not set. "
                    + "Generate with: openssl rand -base64 32"
            );
        }
        return new PasswordEncryptionUtil(masterKey);
    }

    /**
     * Provides TenantConnectionPoolManager singleton
     * Manages per-tenant ClickHouse connection pools
     */
    @Provides
    @Singleton
    public TenantConnectionPoolManager provideTenantConnectionPoolManager(
        ClickhouseConfig clickhouseConfig) {
        return new TenantConnectionPoolManager(clickhouseConfig);
    }

    /**
     * Provides TenantCredentialsDao singleton
     * Manages tenant credentials in MySQL
     */
    @Provides
    @Singleton
    public TenantCredentialsDao provideTenantCredentialsDao(
        MysqlClient mysqlClient, PasswordEncryptionUtil encryptionUtil) {
        return new TenantCredentialsDao(mysqlClient, encryptionUtil);
    }

    /**
     * Provides TenantService singleton
     * Business logic for tenant management
     */
    @Provides
    @Singleton
    public TenantService provideTenantService(TenantCredentialsDao tenantCredentialsDao) {
        return new TenantService(tenantCredentialsDao);
    }

    /**
     * Provides PerformanceMetricService implementation
     * Tenant-aware query service for performance metrics
     */
    @Provides
    public PerformanceMetricService providePerformanceMetricService(
        ClickhouseQueryService clickhouseQueryService,
        TenantConnectionPoolManager poolManager,
        TenantCredentialsDao credentialsDao) {
        return new PerformanceMetricServiceImpl(clickhouseQueryService, poolManager, credentialsDao);
    }
}
```

---

## 2. Update MainVerticle.java

Add tenant pool initialization and cleanup:

```java
package org.dreamhorizon.pulseserver.verticle;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.vertx.rxjava3.core.AbstractVerticle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.TenantConnectionPoolManager;
import org.dreamhorizon.pulseserver.dao.interaction.TenantCredentialsDao;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class MainVerticle extends AbstractVerticle {

    private final TenantConnectionPoolManager tenantConnectionPoolManager;
    private final TenantCredentialsDao tenantCredentialsDao;
    
    // ... other injected dependencies ...

    @Override
    public Completable rxStart() {
        log.info("Starting Pulse Server - initializing tenant connection pools");

        return Completable.fromAction(
                () -> {
                    // Step 1: Initialize admin pool upfront
                    initializeAdminPool();

                    // Step 2: Load active tenants and initialize their pools
                    loadAndInitializeTenantPools();
                })
            .andThen(setupHttpServer())
            .doOnError(error -> log.error("Failed to start Pulse Server", error));
    }

    /**
     * Initialize admin connection pool for system operations
     */
    private void initializeAdminPool() {
        try {
            tenantConnectionPoolManager.initializeAdminPool();
            log.info("✓ Admin connection pool initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize admin pool", e);
            throw new RuntimeException("Cannot initialize admin pool", e);
        }
    }

    /**
     * Load all active tenants from MySQL and pre-initialize their connection pools
     * This ensures pools are ready when requests arrive
     */
    private void loadAndInitializeTenantPools() {
        tenantCredentialsDao
            .getAllActiveTenantCredentials()
            .subscribe(
                credentials -> {
                    try {
                        log.info(
                            "Pre-initializing connection pool for tenant: {}",
                            credentials.getTenantId());

                        // Create/get pool for this tenant
                        tenantConnectionPoolManager.getPoolForTenant(
                            credentials.getTenantId(),
                            credentials.getClickhouseUsername(),
                            credentials.getClickhousePassword());

                        log.info(
                            "✓ Pool initialized for tenant: {}", credentials.getTenantId());
                    } catch (Exception e) {
                        log.error(
                            "Failed to initialize pool for tenant: {}",
                            credentials.getTenantId(),
                            e);
                        // Continue with other tenants on error
                    }
                },
                error -> log.error("Error loading tenant credentials during startup", error),
                () -> log.info("✓ All active tenant pools initialized successfully"));
    }

    /**
     * Setup HTTP server
     * This is your existing method - just shown for context
     */
    private Completable setupHttpServer() {
        log.info("Setting up HTTP server...");
        // Your existing HTTP server setup code
        return Completable.complete();
    }

    @Override
    public Completable rxStop() {
        log.info("Stopping Pulse Server - closing all tenant connection pools");

        try {
            // Close all tenant connection pools on shutdown
            tenantConnectionPoolManager.closeAllPools();
            log.info("✓ All connection pools closed successfully");
        } catch (Exception e) {
            log.error("Error closing connection pools", e);
        }

        return Completable.complete();
    }
}
```

---

## 3. Update application.yml

Add encryption and tenant configuration:

```yaml
# application.yml

# New encryption configuration for tenant passwords
encryption:
  master-key: "${ENCRYPTION_MASTER_KEY:}"  # Must be set via environment variable
  algorithm: "AES"
  keysize: 256

# Update ClickHouse configuration
clickhouse:
  r2dbc-url: "r2dbc:clickhouse://${CLICKHOUSE_HOST:localhost}:${CLICKHOUSE_PORT:8123}/"
  username: "${CLICKHOUSE_USER:default}"
  password: "${CLICKHOUSE_PASSWORD:}"
  initsize: 10     # Admin pool size
  maxsize: 50      # Admin pool max

# MySQL configuration (for pulse_db)
mysql:
  host: "${MYSQL_HOST:localhost}"
  port: ${MYSQL_PORT:3306}
  database: "${MYSQL_DATABASE:pulse_db}"
  username: "${MYSQL_USER:pulse_user}"
  password: "${MYSQL_PASSWORD:pulse_password}"

# Multi-tenant configuration
multitenancy:
  enabled: true
  pool-config:
    min-size-per-tenant: 2      # Min connections per tenant pool
    max-size-per-tenant: 5      # Max connections per tenant pool
    max-idle-time-minutes: 5    # Idle timeout
    connection-timeout-seconds: 10
```

---

## 4. Sample pom.xml Dependencies

Add these dependencies if not already present:

```xml
<!-- JWT Support -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>

<!-- R2DBC for ClickHouse -->
<dependency>
    <groupId>com.clickhouse</groupId>
    <artifactId>clickhouse-r2dbc</artifactId>
    <version>0.5.0</version>
</dependency>

<!-- MySQL R2DBC -->
<dependency>
    <groupId>dev.miku</groupId>
    <artifactId>r2dbc-mysql</artifactId>
    <version>0.8.2.RELEASE</version>
</dependency>

<!-- RxJava -->
<dependency>
    <groupId>io.reactivex.rxjava3</groupId>
    <artifactId>rxjava</artifactId>
    <version>3.1.6</version>
</dependency>

<!-- Vertx RxJava -->
<dependency>
    <groupId>io.vertx</groupId>
    <artifactId>vertx-rx-java3</artifactId>
    <version>4.3.7</version>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.30</version>
    <scope>provided</scope>
</dependency>

<!-- Logging -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.7</version>
</dependency>
```

---

## 5. Environment Variables Setup

Create `.env` file or export variables:

```bash
# .env file
ENCRYPTION_MASTER_KEY=<output_of_openssl_rand_base64_32>
CLICKHOUSE_HOST=localhost
CLICKHOUSE_PORT=8123
CLICKHOUSE_USER=default
CLICKHOUSE_PASSWORD=
CLICKHOUSE_R2DBC_URL=r2dbc:clickhouse://localhost:8123/
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=pulse_db
MYSQL_USER=pulse_user
MYSQL_PASSWORD=pulse_password
```

Load in shell:

```bash
# Load environment variables
set -a
source .env
set +a

# Start application
mvn spring-boot:run
```

---

## 6. Database Setup Commands

```bash
#!/bin/bash
# setup-multitenancy.sh

set -e

echo "=== Pulse Multi-Tenancy Setup ==="

# 1. Generate encryption key
echo ""
echo "1. Generating encryption master key..."
ENCRYPTION_KEY=$(openssl rand -base64 32)
echo "   Encryption Key: $ENCRYPTION_KEY"
echo "   Set as ENCRYPTION_MASTER_KEY environment variable"

# 2. Run MySQL migrations
echo ""
echo "2. Running MySQL migrations..."
cd backend/server
mvn flyway:migrate \
    -Dflyway.url="jdbc:mysql://localhost:3306/pulse_db" \
    -Dflyway.user="pulse_user" \
    -Dflyway.password="pulse_password"
echo "   ✓ Migrations completed"

# 3. Create default tenant in ClickHouse
echo ""
echo "3. Creating default tenant user in ClickHouse..."
curl -X POST "http://localhost:8123/" \
    -u "default:" \
    -d "CREATE USER IF NOT EXISTS 'default'@'%' IDENTIFIED BY 'default_password'"
echo "   ✓ Default user created"

# 4. Grant permissions
echo ""
echo "4. Granting permissions to default user..."
curl -X POST "http://localhost:8123/" \
    -u "default:" \
    -d "GRANT SELECT, INSERT ON otel.* TO 'default'@'%'"
echo "   ✓ Permissions granted"

echo ""
echo "=== Setup Complete ==="
echo "Next steps:"
echo "1. Set ENCRYPTION_MASTER_KEY=$ENCRYPTION_KEY"
echo "2. Verify MySQL: SELECT * FROM clickhouse_tenant_credentials;"
echo "3. Verify ClickHouse: SHOW USERS;"
echo "4. Start application: mvn spring-boot:run"
```

---

## 7. Unit Test Example

```java
package org.dreamhorizon.pulseserver.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordEncryptionUtilTest {

    private PasswordEncryptionUtil encryptionUtil;
    private static final String MASTER_KEY = "abcd1234efgh5678ijkl9012mnop3456qrst7890uvwx==";

    @BeforeEach
    void setUp() {
        encryptionUtil = new PasswordEncryptionUtil(MASTER_KEY);
    }

    @Test
    void testEncryptionAndDecryption() {
        String plainPassword = "mySecurePassword123!";

        // Encrypt
        PasswordEncryptionUtil.EncryptedPassword encrypted =
            encryptionUtil.encryptPassword(plainPassword);

        assertNotNull(encrypted.getEncryptedPassword());
        assertNotNull(encrypted.getSalt());
        assertNotNull(encrypted.getDigest());

        // Decrypt
        String decrypted = encryptionUtil.decryptPassword(encrypted.getEncryptedPassword());
        assertEquals(plainPassword, decrypted);
    }

    @Test
    void testPasswordVerification() {
        String plainPassword = "testPassword";
        PasswordEncryptionUtil.EncryptedPassword encrypted =
            encryptionUtil.encryptPassword(plainPassword);

        boolean verified =
            encryptionUtil.verifyPassword(
                plainPassword, encrypted.getSalt(), encrypted.getDigest());
        assertTrue(verified);

        boolean notVerified =
            encryptionUtil.verifyPassword(
                "wrongPassword", encrypted.getSalt(), encrypted.getDigest());
        assertFalse(notVerified);
    }

    @Test
    void testDifferentSaltsProduceDifferentEncryptions() {
        String plainPassword = "samePassword";

        PasswordEncryptionUtil.EncryptedPassword encrypted1 =
            encryptionUtil.encryptPassword(plainPassword);
        PasswordEncryptionUtil.EncryptedPassword encrypted2 =
            encryptionUtil.encryptPassword(plainPassword);

        assertNotEquals(
            encrypted1.getEncryptedPassword(),
            encrypted2.getEncryptedPassword());
        assertNotEquals(encrypted1.getSalt(), encrypted2.getSalt());
    }
}
```

---

## 8. Integration Test Example

```java
package org.dreamhorizon.pulseserver.service.interaction;

import io.reactivex.rxjava3.observers.TestObserver;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PerformanceMetricServiceIntegrationTest {

    @Autowired private PerformanceMetricService performanceMetricService;

    @Test
    void testTenantIsolatedQuery() {
        QueryRequest request = new QueryRequest();
        request.setDataType(QueryRequest.DataType.TRACES);
        request.setTenantId("tenant_abc");
        request.setUserId("user_123");
        request.setUserEmail("user@example.com");

        QueryRequest.TimeRange timeRange = new QueryRequest.TimeRange();
        timeRange.setStart("2024-01-01T00:00:00");
        timeRange.setEnd("2024-01-02T00:00:00");
        request.setTimeRange(timeRange);

        TestObserver<PerformanceMetricDistributionRes> observer =
            performanceMetricService.getMetricDistribution(request).test();

        observer.awaitDone(10, java.util.concurrent.TimeUnit.SECONDS);
        observer.assertComplete();
        observer.assertNoErrors();
    }

    @Test
    void testMultipleTenantConcurrency() {
        // Test that two tenants can query concurrently without interference
        QueryRequest request1 = createTenantRequest("tenant_abc");
        QueryRequest request2 = createTenantRequest("tenant_xyz");

        TestObserver<PerformanceMetricDistributionRes> observer1 =
            performanceMetricService.getMetricDistribution(request1).test();
        TestObserver<PerformanceMetricDistributionRes> observer2 =
            performanceMetricService.getMetricDistribution(request2).test();

        observer1.awaitDone(10, java.util.concurrent.TimeUnit.SECONDS);
        observer2.awaitDone(10, java.util.concurrent.TimeUnit.SECONDS);

        observer1.assertComplete();
        observer2.assertComplete();
        observer1.assertNoErrors();
        observer2.assertNoErrors();
    }

    private QueryRequest createTenantRequest(String tenantId) {
        QueryRequest request = new QueryRequest();
        request.setDataType(QueryRequest.DataType.TRACES);
        request.setTenantId(tenantId);
        request.setUserId("user_123");
        request.setUserEmail("user@example.com");

        QueryRequest.TimeRange timeRange = new QueryRequest.TimeRange();
        timeRange.setStart("2024-01-01T00:00:00");
        timeRange.setEnd("2024-01-02T00:00:00");
        request.setTimeRange(timeRange);

        return request;
    }
}
```

---

## Quick Integration Checklist

```
Before running application:
□ Update MainModule.java with provider methods
□ Update MainVerticle.java with pool initialization
□ Update application.yml with encryption config
□ Generate encryption master key
□ Set ENCRYPTION_MASTER_KEY environment variable
□ Run database migrations
□ Create ClickHouse default user
□ Verify all imports compile
□ Run unit tests
□ Start application and check logs

After application starts:
□ Verify "Admin connection pool initialized"
□ Verify "All tenant pools initialized"
□ Test sample query with tenant context
□ Check logs for any errors
□ Monitor connection pool statistics
```

---

This covers all the code needed to integrate multi-tenancy into your Pulse server!
