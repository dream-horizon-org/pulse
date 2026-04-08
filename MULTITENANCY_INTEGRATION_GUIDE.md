# Multi-Tenancy Integration Guide

## Quick Start

### Step 1: Run Database Migrations

```bash
# Run MySQL migration to create tenant tables
cd /Users/abhishekkumar/Desktop/pulse/backend/server
mvn flyway:migrate -Dflyway.url=jdbc:mysql://localhost:3306/pulse_db \
                   -Dflyway.user=pulse_user \
                   -Dflyway.password=pulse_password
```

### Step 2: Generate Encryption Master Key

```bash
# Generate 256-bit random key and Base64 encode it
openssl rand -base64 32
# Output: abcd1234efgh5678ijkl9012mnop3456qrst7890uvwx==
```

### Step 3: Set Environment Variables

```bash
export ENCRYPTION_MASTER_KEY="abcd1234efgh5678ijkl9012mnop3456qrst7890uvwx=="
export CLICKHOUSE_HOST=localhost
export CLICKHOUSE_PORT=8123
export CLICKHOUSE_USER=default
export CLICKHOUSE_PASSWORD=
```

### Step 4: Start Application

The application will automatically:
1. Initialize admin connection pool
2. Load active tenants from MySQL
3. Initialize per-tenant connection pools

```bash
cd /Users/abhishekkumar/Desktop/pulse/backend/server
mvn spring-boot:run
```

### Step 5: Register First Tenant

```bash
# Create ClickHouse user
cd /Users/abhishekkumar/Desktop/pulse/backend/ingestion
chmod +x clickhouse-tenant-setup.sh
./clickhouse-tenant-setup.sh tenant_abc "securePassword123"

# Output:
# ✓ ClickHouse user 'tenant_abc' created successfully
# - Tenant ID: tenant_abc
# - Username: tenant_abc
```

### Step 6: Register Credentials in Pulse

```bash
# Call API to save credentials (TODO: implement endpoint)
curl -X POST http://localhost:8080/api/v1/tenants/tenant_abc/credentials \
  -H "Authorization: Bearer <ADMIN_JWT>" \
  -H "Content-Type: application/json" \
  -d '{"password": "securePassword123"}'
```

### Step 7: Test Multi-Tenant Query

```bash
# Generate JWT token with tenant_abc and user claims (TODO: use actual JWT)
JWT_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "X-Tenant-Id: tenant_abc" \
  -H "Content-Type: application/json" \
  -d '{
    "dataType": "TRACES",
    "timeRange": {
      "start": "2024-01-01T00:00:00",
      "end": "2024-01-02T00:00:00"
    },
    "select": [
      {"name": "SpanName"},
      {"name": "Duration"}
    ],
    "groupBy": ["ServiceName"],
    "limit": 100
  }'
```

## File Structure

```
backend/server/src/main/java/org/dreamhorizon/pulseserver/
├── util/
│   └── PasswordEncryptionUtil.java          # AES-256 encryption
├── dto/
│   └── TenantContextDto.java                # Tenant context DTO
├── model/
│   └── TenantCredentials.java               # Credentials model
├── dao/interaction/
│   └── TenantCredentialsDao.java            # Credentials DAO
├── client/chclient/
│   └── TenantConnectionPoolManager.java     # Connection pool manager
├── service/
│   └── TenantService.java                   # Tenant business logic
├── service/interaction/
│   ├── PerformanceMetricService.java        # Service interface
│   └── PerformanceMetricServiceImpl.java     # Service implementation
└── resources/performance/
    └── PerformanceMetricDistribution.java   # REST controller

backend/server/src/main/resources/
└── db/migration/
    └── V1_1__add_tenant_multitenancy.sql    # MySQL migration

backend/ingestion/
├── clickhouse-tenant-rls-setup.sql          # ClickHouse setup
└── clickhouse-tenant-setup.sh               # Tenant registration script
```

## Configuration Files to Update

### 1. MainModule.java (Dependency Injection)

```java
@Provides
@Singleton
PasswordEncryptionUtil providePasswordEncryption(ClickhouseConfig config) {
    String masterKey = System.getenv("ENCRYPTION_MASTER_KEY");
    if (masterKey == null || masterKey.isBlank()) {
        throw new RuntimeException("ENCRYPTION_MASTER_KEY not set");
    }
    return new PasswordEncryptionUtil(masterKey);
}

@Provides
@Singleton
TenantConnectionPoolManager provideTenantConnectionPoolManager(ClickhouseConfig config) {
    return new TenantConnectionPoolManager(config);
}

@Provides
@Singleton
TenantCredentialsDao provideTenantCredentialsDao(
    MysqlClient mysqlClient, PasswordEncryptionUtil encryptionUtil) {
    return new TenantCredentialsDao(mysqlClient, encryptionUtil);
}

@Provides
@Singleton
TenantService provideTenantService(TenantCredentialsDao dao) {
    return new TenantService(dao);
}

@Provides
PerformanceMetricService providePerformanceMetricService(
    ClickhouseQueryService queryService,
    TenantConnectionPoolManager poolManager,
    TenantCredentialsDao credentialsDao) {
    return new PerformanceMetricServiceImpl(queryService, poolManager, credentialsDao);
}
```

### 2. application.yml

```yaml
encryption:
  master-key: "${ENCRYPTION_MASTER_KEY:}"
  algorithm: "AES"
  keysize: 256

clickhouse:
  r2dbc-url: "r2dbc:clickhouse://${CLICKHOUSE_HOST:localhost}:${CLICKHOUSE_PORT:8123}/"
  username: "${CLICKHOUSE_USER:default}"
  password: "${CLICKHOUSE_PASSWORD:}"
  initsize: 10
  maxsize: 50

mysql:
  host: "${MYSQL_HOST:localhost}"
  port: ${MYSQL_PORT:3306}
  database: "${MYSQL_DATABASE:pulse_db}"
  username: "${MYSQL_USER:pulse_user}"
  password: "${MYSQL_PASSWORD:pulse_password}"
```

### 3. MainVerticle.java (Startup/Shutdown)

```java
@Override
public Completable rxStart() {
    log.info("Starting Pulse Server - initializing tenant connection pools");

    return Completable.fromAction(() -> {
        // Initialize admin pool
        tenantConnectionPoolManager.initializeAdminPool();
        log.info("Admin connection pool initialized");

        // Load active tenants and initialize pools
        loadAndInitializeTenantPools();
        log.info("Tenant connection pools initialized");
    })
    .andThen(setupHttpServer())
    .doOnError(error -> log.error("Failed to start Pulse Server", error));
}

private void loadAndInitializeTenantPools() {
    tenantCredentialsDao.getAllActiveTenantCredentials()
        .subscribe(
            credentials -> {
                try {
                    log.info("Pre-initializing pool for tenant: {}", credentials.getTenantId());
                    tenantConnectionPoolManager.getPoolForTenant(
                        credentials.getTenantId(),
                        credentials.getClickhouseUsername(),
                        credentials.getClickhousePassword()
                    );
                } catch (Exception e) {
                    log.error("Failed to initialize pool for tenant: {}", 
                        credentials.getTenantId(), e);
                }
            },
            error -> log.error("Error loading tenant credentials", error),
            () -> log.info("All tenant pools initialized")
        );
}

@Override
public Completable rxStop() {
    log.info("Stopping Pulse Server - closing tenant connection pools");
    tenantConnectionPoolManager.closeAllPools();
    return Completable.complete();
}
```

## Verification Checklist

- [ ] MySQL migration executed successfully
- [ ] Tenant tables created in MySQL
- [ ] Encryption master key set in environment
- [ ] Application starts without errors
- [ ] Admin connection pool initialized
- [ ] ClickHouse user created via script
- [ ] Credentials registered in Pulse
- [ ] Test query returns results for tenant
- [ ] Logs show tenant context extraction
- [ ] Multiple tenants can query independently

## Testing

### Unit Tests

```java
@Test
void testPasswordEncryption() {
    PasswordEncryptionUtil util = new PasswordEncryptionUtil(base64Key);
    PasswordEncryptionUtil.EncryptedPassword encrypted = util.encryptPassword("secret");
    
    String decrypted = util.decryptPassword(encrypted.getEncryptedPassword());
    assertEquals("secret", decrypted);
}

@Test
void testTenantPoolIsolation() {
    ConnectionPool pool1 = poolManager.getPoolForTenant("tenant1", "user1", "pass1");
    ConnectionPool pool2 = poolManager.getPoolForTenant("tenant2", "user2", "pass2");
    
    assertNotEquals(pool1, pool2);
    assertTrue(poolManager.getPoolStatistics("tenant1").isActive);
}
```

### Integration Tests

```bash
# Test with multiple concurrent tenants
for i in {1..5}; do
  curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
    -H "Authorization: Bearer $JWT" \
    -H "X-Tenant-Id: tenant_$i" \
    -H "Content-Type: application/json" \
    -d '{"dataType": "TRACES", ...}'
done
```

## Troubleshooting

### Connection Pool Issues

```bash
# Check pool statistics
SELECT credential_id, tenant_id, is_active FROM clickhouse_tenant_credentials;

# Verify ClickHouse user permissions
SHOW GRANTS FOR 'tenant_abc'@'%';

# Test connection directly
mysql -h localhost -u pulse_user -p pulse_db
SELECT * FROM clickhouse_tenant_credentials WHERE tenant_id = 'tenant_abc';
```

### Password Encryption Issues

```java
// Verify master key is correctly set
String masterKey = System.getenv("ENCRYPTION_MASTER_KEY");
System.out.println("Master key length: " + masterKey.length());
System.out.println("Master key (first 10 chars): " + masterKey.substring(0, 10));
```

### JWT Token Issues

```bash
# Decode JWT to verify claims
echo "eyJhbGc..." | base64 -d | jq .
# Should contain: { "sub": "user_id", "email": "...", "tenant_id": "tenant_abc" }
```

## Support & Questions

For issues or questions:
1. Check logs: `tail -f logs/pulse-server.log | grep tenant`
2. Review database: `SELECT * FROM clickhouse_credential_audit;`
3. Verify ClickHouse user: `SHOW USERS;`
4. Check connection pool: `poolManager.getPoolStatistics(tenantId)`
