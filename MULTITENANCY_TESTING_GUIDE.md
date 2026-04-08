# Multi-Tenancy Integration Testing Guide

## Overview

This guide covers end-to-end testing of the multi-tenancy implementation. It includes unit tests, integration tests, and manual testing procedures.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Unit Testing](#unit-testing)
3. [Integration Testing](#integration-testing)
4. [End-to-End Testing](#end-to-end-testing)
5. [Manual Testing](#manual-testing)
6. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Dependencies

```xml
<!-- Test Dependencies -->
<dependency>
    <groupId>junit</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>5.9.x</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.x</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>1.19.x</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>mysql</artifactId>
    <version>1.19.x</version>
    <scope>test</scope>
</dependency>
```

### Test Environment Setup

```bash
# Create test databases
docker run -d \
  --name mysql-test \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=pulse_test \
  -p 3307:3306 \
  mysql:8.0

docker run -d \
  --name clickhouse-test \
  -p 9001:9000 \
  -p 8124:8123 \
  clickhouse/clickhouse-server:latest
```

---

## Unit Testing

### 1. PasswordEncryptionUtil Tests

```java
package com.pulse.multitenancy.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

public class PasswordEncryptionUtilTest {

    private PasswordEncryptionUtil encryptionUtil;
    private static final String TEST_PASSWORD = "MySecurePassword123!@#";
    private static final String MASTER_KEY = "0123456789abcdef0123456789abcdef"; // 32 hex chars = 128 bits

    @BeforeEach
    public void setUp() {
        encryptionUtil = new PasswordEncryptionUtil(MASTER_KEY);
    }

    @Test
    public void testEncryptionAndDecryption() {
        // Encrypt
        String encrypted = encryptionUtil.encryptPassword(TEST_PASSWORD);
        assertNotNull(encrypted);
        assertNotEquals(TEST_PASSWORD, encrypted);

        // Decrypt
        String decrypted = encryptionUtil.decryptPassword(encrypted);
        assertEquals(TEST_PASSWORD, decrypted);
    }

    @Test
    public void testDifferentEncryptionsProduceDifferentCiphertexts() {
        String encrypted1 = encryptionUtil.encryptPassword(TEST_PASSWORD);
        String encrypted2 = encryptionUtil.encryptPassword(TEST_PASSWORD);
        
        // Should be different due to random IV
        assertNotEquals(encrypted1, encrypted2);
        
        // But both should decrypt to same value
        assertEquals(encryptionUtil.decryptPassword(encrypted1), 
                     encryptionUtil.decryptPassword(encrypted2));
    }

    @Test
    public void testInvalidCiphertextThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            encryptionUtil.decryptPassword("invalid-base64-!!!!");
        });
    }

    @Test
    public void testEmptyPasswordHandling() {
        String encrypted = encryptionUtil.encryptPassword("");
        String decrypted = encryptionUtil.decryptPassword(encrypted);
        assertEquals("", decrypted);
    }
}
```

### 2. TenantContextDto Tests

```java
package com.pulse.multitenancy.dto;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TenantContextDtoTest {

    @Test
    public void testTenantContextCreation() {
        String tenantId = "acme_corp";
        String username = "tenant_acme_corp";
        String password = "secure_password";

        TenantContextDto context = TenantContextDto.builder()
                .tenantId(tenantId)
                .clickhouseUsername(username)
                .clickhousePassword(password)
                .build();

        assertEquals(tenantId, context.getTenantId());
        assertEquals(username, context.getClickhouseUsername());
        assertEquals(password, context.getClickhousePassword());
    }

    @Test
    public void testTenantContextEquality() {
        TenantContextDto context1 = TenantContextDto.builder()
                .tenantId("tenant1")
                .clickhouseUsername("user1")
                .clickhousePassword("pass1")
                .build();

        TenantContextDto context2 = TenantContextDto.builder()
                .tenantId("tenant1")
                .clickhouseUsername("user1")
                .clickhousePassword("pass1")
                .build();

        assertEquals(context1, context2);
    }
}
```

---

## Integration Testing

### 1. TenantCredentialsDao Integration Tests

```java
package com.pulse.multitenancy.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.container.Testcontainers;
import org.testcontainers.containers.MySQLContainer;
import org.junit.jupiter.api.ClassRule;
import io.reactivex.rxjava3.core.Single;
import com.pulse.multitenancy.dto.TenantCredentials;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
public class TenantCredentialsDaoIntegrationTest {

    @ClassRule
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("pulse_test")
            .withUsername("test")
            .withPassword("test");

    private TenantCredentialsDao dao;

    @BeforeEach
    public void setUp() {
        // Initialize DAO with test container connection
        // dao = new TenantCredentialsDao(getTestConnection());
    }

    @Test
    public void testCreateCredentials() throws Exception {
        TenantCredentials credentials = TenantCredentials.builder()
                .tenantId("test_acme")
                .clickhouseUsername("tenant_test_acme")
                .clickhousePassword("encrypted_pass_123")
                .encryptionSalt("salt_12345")
                .passwordDigest("digest_hash")
                .isActive(true)
                .build();

        String credentialId = dao.createOrUpdateCredentials(credentials).blockingGet();
        assertNotNull(credentialId);
    }

    @Test
    public void testGetCredentialsByTenantId() throws Exception {
        // First create
        TenantCredentials original = TenantCredentials.builder()
                .tenantId("test_acme_2")
                .clickhouseUsername("tenant_test_acme_2")
                .clickhousePassword("password_456")
                .isActive(true)
                .build();
        
        dao.createOrUpdateCredentials(original).blockingGet();

        // Then retrieve
        TenantCredentials retrieved = dao.getCredentialsByTenantId("test_acme_2")
                .blockingGet();

        assertNotNull(retrieved);
        assertEquals("test_acme_2", retrieved.getTenantId());
        assertEquals("tenant_test_acme_2", retrieved.getClickhouseUsername());
    }

    @Test
    public void testDeactivateTenant() throws Exception {
        // Create tenant
        TenantCredentials credentials = TenantCredentials.builder()
                .tenantId("test_deactivate")
                .clickhouseUsername("tenant_deactivate")
                .clickhousePassword("password")
                .isActive(true)
                .build();
        
        dao.createOrUpdateCredentials(credentials).blockingGet();

        // Deactivate
        dao.deactivateTenant("test_deactivate").blockingGet();

        // Verify deactivation
        TenantCredentials deactivated = dao.getCredentialsByTenantId("test_deactivate")
                .blockingGet();
        assertFalse(deactivated.isActive());
    }

    @Test
    public void testGetAllActiveTenantCredentials() throws Exception {
        // Create multiple active tenants
        for (int i = 1; i <= 3; i++) {
            TenantCredentials credentials = TenantCredentials.builder()
                    .tenantId("test_active_" + i)
                    .clickhouseUsername("tenant_active_" + i)
                    .clickhousePassword("password_" + i)
                    .isActive(true)
                    .build();
            dao.createOrUpdateCredentials(credentials).blockingGet();
        }

        // Create one inactive
        TenantCredentials inactive = TenantCredentials.builder()
                .tenantId("test_inactive")
                .clickhouseUsername("tenant_inactive")
                .clickhousePassword("password_inactive")
                .isActive(false)
                .build();
        dao.createOrUpdateCredentials(inactive).blockingGet();

        // Get all active
        java.util.List<TenantCredentials> active = dao.getAllActiveTenantCredentials()
                .toList()
                .blockingGet();

        assertEquals(3, active.stream()
                .filter(c -> c.getTenantId().startsWith("test_active"))
                .count());
    }

    @Test
    public void testLogCredentialAudit() throws Exception {
        // Log audit entry
        dao.logCredentialAudit(
                "test_audit",
                "CREATED",
                "Admin user",
                "127.0.0.1"
        ).blockingGet();

        // Verify in database
        // Query should show audit entry created
    }
}
```

### 2. TenantService Integration Tests

```java
package com.pulse.multitenancy.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.pulse.multitenancy.util.TenantContextDto;
import static org.junit.jupiter.api.Assertions.*;

public class TenantServiceIntegrationTest {

    private TenantService tenantService;

    @BeforeEach
    public void setUp() {
        // Initialize with test dependencies
    }

    @Test
    public void testCreateTenant() throws Exception {
        tenantService.createTenant("corp_123", "Corporation 123")
                .blockingAwait();

        TenantContextDto context = tenantService.getTenantContext("corp_123")
                .blockingGet();

        assertNotNull(context);
        assertEquals("corp_123", context.getTenantId());
    }

    @Test
    public void testGetNonexistentTenantThrowsException() throws Exception {
        assertThrows(Exception.class, () -> {
            tenantService.getTenantContext("nonexistent")
                    .blockingGet();
        });
    }

    @Test
    public void testDeactivateTenant() throws Exception {
        // Create
        tenantService.createTenant("corp_to_deactivate", "Corp")
                .blockingAwait();

        // Deactivate
        tenantService.deactivateTenant("corp_to_deactivate")
                .blockingAwait();

        // Verify no longer active
        assertThrows(Exception.class, () -> {
            tenantService.getTenantContext("corp_to_deactivate")
                    .blockingGet();
        });
    }
}
```

---

## End-to-End Testing

### 1. Multi-Tenant Query Execution Test

```java
package com.pulse.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.pulse.service.ClickhouseQueryService;
import com.pulse.model.QueryConfiguration;
import static org.junit.jupiter.api.Assertions.*;

public class MultiTenantQueryExecutionE2ETest {

    private ClickhouseQueryService queryService;

    @BeforeEach
    public void setUp() {
        // Initialize with test environment
    }

    @Test
    public void testTenantIsolationInQueryExecution() throws Exception {
        // Setup: Create two tenants with different data
        setupTestTenant("tenant_1", "tenant_user_1", "password_1");
        setupTestTenant("tenant_2", "tenant_user_2", "password_2");

        // Create sample data for tenant 1
        insertSampleMetrics("tenant_1", 100);
        
        // Create sample data for tenant 2
        insertSampleMetrics("tenant_2", 200);

        // Query as tenant 1
        QueryConfiguration query1 = QueryConfiguration.builder()
                .query("SELECT count() FROM otel_metrics")
                .tenantId("tenant_1")
                .build();

        Long count1 = queryService.executeQuery(query1)
                .blockingGet();

        // Query as tenant 2
        QueryConfiguration query2 = QueryConfiguration.builder()
                .query("SELECT count() FROM otel_metrics")
                .tenantId("tenant_2")
                .build();

        Long count2 = queryService.executeQuery(query2)
                .blockingGet();

        // Verify isolation - counts should reflect only tenant's data
        assertEquals(100, count1);
        assertEquals(200, count2);
        assertNotEquals(count1, count2);
    }

    @Test
    public void testTenantQueryExecutionWithDifferentUsers() throws Exception {
        setupTestTenant("corp_a", "ch_user_a", "pass_a");
        setupTestTenant("corp_b", "ch_user_b", "pass_b");

        QueryConfiguration config = QueryConfiguration.builder()
                .query("SELECT * FROM otel_metrics WHERE 1=1 LIMIT 10")
                .tenantId("corp_a")
                .build();

        var results = queryService.executeQuery(config)
                .blockingGet();

        assertNotNull(results);
    }
}
```

### 2. Credential Encryption E2E Test

```java
package com.pulse.e2e;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.pulse.multitenancy.util.PasswordEncryptionUtil;
import com.pulse.multitenancy.dao.TenantCredentialsDao;
import com.pulse.multitenancy.dto.TenantCredentials;
import static org.junit.jupiter.api.Assertions.*;

public class CredentialEncryptionE2ETest {

    private PasswordEncryptionUtil encryptionUtil;
    private TenantCredentialsDao credentialsDao;

    @BeforeEach
    public void setUp() {
        // Initialize with test container MySQL
    }

    @Test
    public void testCredentialEncryptionStorageAndRetrieval() throws Exception {
        String originalPassword = "ClickHousePassword123!@#";
        String tenantId = "test_encryption";

        // Encrypt password
        String encrypted = encryptionUtil.encryptPassword(originalPassword);

        // Store encrypted credentials
        TenantCredentials credentials = TenantCredentials.builder()
                .tenantId(tenantId)
                .clickhouseUsername("ch_user_encrypted")
                .clickhousePassword(encrypted)
                .encryptionSalt(encryptionUtil.getSalt())
                .passwordDigest(encryptionUtil.getDigest(originalPassword))
                .isActive(true)
                .build();

        credentialsDao.createOrUpdateCredentials(credentials).blockingGet();

        // Retrieve and decrypt
        TenantCredentials retrieved = credentialsDao.getCredentialsByTenantId(tenantId)
                .blockingGet();

        assertNotNull(retrieved);
        String decrypted = encryptionUtil.decryptPassword(retrieved.getClickhousePassword());
        assertEquals(originalPassword, decrypted);
    }
}
```

---

## Manual Testing

### 1. Quick Start Test

```bash
# Start services
docker-compose up -d

# Set environment variables
export ENCRYPTION_MASTER_KEY=$(openssl rand -hex 32)
export MYSQL_WRITER_HOST=localhost
export MYSQL_READER_HOST=localhost
export CLICKHOUSE_HOST=localhost

# Run migrations
mysql -h localhost -u pulse_user -p < migration.sql

# Create test tenant
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "test_corp",
    "name": "Test Corporation",
    "clickhouseUsername": "ch_test_corp",
    "clickhousePassword": "secure_password"
  }'

# Query as tenant
curl -X GET http://localhost:8080/api/v1/metrics \
  -H "X-Tenant-Id: test_corp"
```

### 2. Isolation Verification Test

```bash
# Create two tenants
TENANT_1_ID="acme_corp"
TENANT_2_ID="beta_corp"

# Create tenant 1
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\": \"${TENANT_1_ID}\", \"name\": \"ACME Corp\"}"

# Create tenant 2
curl -X POST http://localhost:8080/api/v1/tenants \
  -H "Content-Type: application/json" \
  -d "{\"tenantId\": \"${TENANT_2_ID}\", \"name\": \"Beta Corp\"}"

# Insert sample data as tenant 1
curl -X POST http://localhost:8080/api/v1/metrics \
  -H "X-Tenant-Id: ${TENANT_1_ID}" \
  -H "Content-Type: application/json" \
  -d '{"metric": "cpu_usage", "value": 45}'

# Insert sample data as tenant 2
curl -X POST http://localhost:8080/api/v1/metrics \
  -H "X-Tenant-Id: ${TENANT_2_ID}" \
  -H "Content-Type: application/json" \
  -d '{"metric": "memory_usage", "value": 60}'

# Query as tenant 1 - should only see tenant 1 data
curl -X GET http://localhost:8080/api/v1/metrics \
  -H "X-Tenant-Id: ${TENANT_1_ID}"

# Query as tenant 2 - should only see tenant 2 data
curl -X GET http://localhost:8080/api/v1/metrics \
  -H "X-Tenant-Id: ${TENANT_2_ID}"
```

### 3. Connection Pool Test

```bash
# Monitor connection pools
curl -X GET http://localhost:8080/actuator/health/multitenancy

# Should show:
# {
#   "status": "UP",
#   "details": {
#     "adminPool": "healthy",
#     "tenantPools": {
#       "acme_corp": "initialized",
#       "beta_corp": "initialized"
#     }
#   }
# }
```

### 4. Encryption Key Verification

```bash
# Verify encryption key is set
echo $ENCRYPTION_MASTER_KEY

# Should output 64 hex characters (256-bit key in hex format)
# Example: 0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef

# Verify key length
echo -n $ENCRYPTION_MASTER_KEY | wc -c
# Should output 64
```

---

## Troubleshooting

### Issue: "Tenant not found" Error

**Symptoms:**
```
ERROR: Tenant 'acme_corp' not found in credentials cache
```

**Solution:**
1. Verify tenant was created in MySQL:
   ```bash
   mysql -h localhost -u pulse_user -p pulse -e \
     "SELECT * FROM tenants WHERE tenant_id='acme_corp';"
   ```

2. Verify credentials were stored:
   ```bash
   mysql -h localhost -u pulse_user -p pulse -e \
     "SELECT * FROM clickhouse_tenant_credentials WHERE tenant_id='acme_corp';"
   ```

3. Restart application to reload tenant cache:
   ```bash
   docker-compose restart pulse-server
   ```

### Issue: "Failed to decrypt password" Error

**Symptoms:**
```
ERROR: Failed to decrypt credentials for tenant 'acme_corp'
```

**Solution:**
1. Verify ENCRYPTION_MASTER_KEY is set correctly:
   ```bash
   printenv ENCRYPTION_MASTER_KEY
   ```

2. Check key length (should be 64 hex chars = 256-bit):
   ```bash
   echo -n $ENCRYPTION_MASTER_KEY | wc -c
   ```

3. Verify key matches the key used during encryption:
   ```bash
   # Regenerate credentials with current key
   mysql -h localhost -u pulse_user -p pulse -e \
     "DELETE FROM clickhouse_tenant_credentials WHERE tenant_id='acme_corp';"
   
   # Re-create tenant
   curl -X POST http://localhost:8080/api/v1/tenants ...
   ```

### Issue: "ClickHouse user not found" Error

**Symptoms:**
```
ERROR: Authentication failed for user 'tenant_acme_corp'
```

**Solution:**
1. Verify ClickHouse user was created:
   ```bash
   clickhouse-client -h localhost -u default -q \
     "SHOW USERS;"
   ```

2. Create user manually if missing:
   ```bash
   clickhouse-client -h localhost -u default -q \
     "CREATE USER IF NOT EXISTS tenant_acme_corp IDENTIFIED BY 'password';"
   ```

3. Grant required permissions:
   ```bash
   clickhouse-client -h localhost -u default -q \
     "GRANT SELECT ON otel_metrics TO tenant_acme_corp;"
   ```

### Issue: "Connection pool timeout" Error

**Symptoms:**
```
ERROR: Timeout waiting for connection from pool 'acme_corp'
```

**Solution:**
1. Check pool configuration:
   ```bash
   grep -A5 "pool" multitenancy-default.conf
   ```

2. Verify database connectivity:
   ```bash
   clickhouse-client -h localhost \
     -u tenant_acme_corp \
     -p 'password' \
     -q "SELECT 1;"
   ```

3. Increase pool size if needed:
   ```bash
   # In multitenancy-default.conf
   pool {
       tenantMaxPoolSize = 10  # Increase from 5
   }
   ```

### Issue: "X-Tenant-Id header missing" Error

**Symptoms:**
```
ERROR: X-Tenant-Id header not found in request
```

**Solution:**
1. Ensure header is included in requests:
   ```bash
   curl -H "X-Tenant-Id: acme_corp" http://localhost:8080/api/v1/metrics
   ```

2. Check request routing in PerformanceMetricDistribution:
   ```java
   String tenantId = request.headers().get("X-Tenant-Id");
   if (tenantId == null || tenantId.isEmpty()) {
       throw new IllegalArgumentException("X-Tenant-Id header is required");
   }
   ```

---

## Running Tests

```bash
# Run all unit tests
mvn test

# Run integration tests only
mvn test -Dgroups=integration

# Run specific test class
mvn test -Dtest=TenantCredentialsDaoIntegrationTest

# Run with coverage
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

---

## Performance Testing

### Load Test Configuration

```bash
# Using Apache JMeter
jmeter -n -t multitenancy-load-test.jmx \
  -l results.jtl \
  -j jmeter.log \
  -Dhost=localhost \
  -Dport=8080 \
  -Dthreads=50 \
  -Drampup=30 \
  -Dduration=300
```

### Expected Performance Metrics

- **Latency (p95)**: < 500ms per request
- **Throughput**: > 1000 req/sec per tenant
- **Error Rate**: < 0.1%
- **Connection Pool Utilization**: < 80%
- **Memory Usage**: < 500MB per 5 active tenants

---

## Documentation References

- [MULTITENANCY_SETUP_GUIDE.md](MULTITENANCY_SETUP_GUIDE.md) - Setup instructions
- [MULTITENANCY_IMPLEMENTATION_STATUS.md](MULTITENANCY_IMPLEMENTATION_STATUS.md) - Implementation details
- [MULTITENANCY_INTEGRATION_GUIDE.md](MULTITENANCY_INTEGRATION_GUIDE.md) - API integration guide
