# Multi-Tenancy Implementation Checklist

## Phase 1: Code Integration ✅ COMPLETE

### Utility Classes
- [x] PasswordEncryptionUtil.java - AES-256 encryption
- [x] TenantContextDto.java - Tenant context carrier
- [x] TenantCredentials.java - Model for credentials

### Data Access Layer
- [x] TenantCredentialsDao.java - Database operations
  - [x] saveTenantCredentials()
  - [x] getCredentialsByTenantId()
  - [x] getAllActiveTenantCredentials()
  - [x] deactivateTenant()
  - [x] logCredentialAudit()

### Connection Management
- [x] TenantConnectionPoolManager.java
  - [x] initializeAdminPool()
  - [x] getPoolForTenant()
  - [x] closePoolForTenant()
  - [x] closeAllPools()
  - [x] getPoolStatistics()

### Business Logic
- [x] TenantService.java
  - [x] registerTenant()
  - [x] getTenantContextByTenantId()
  - [x] rotateTenantPassword()
  - [x] deactivateTenant()

### REST API
- [x] PerformanceMetricDistribution.java (updated)
  - [x] X-Tenant-Id header extraction
  - [x] JWT token parsing
  - [x] Tenant context enrichment
- [x] QueryRequest.java (updated)
  - [x] tenantId field
  - [x] userId field
  - [x] userEmail field

### Service Layer
- [x] PerformanceMetricServiceImpl.java
  - [x] Tenant credential fetching
  - [x] Dynamic query building
  - [x] Tenant pool management
  - [x] Query execution

### Database
- [x] V1_1__add_tenant_multitenancy.sql (MySQL migration)
- [x] clickhouse-tenant-rls-setup.sql (ClickHouse setup)
- [x] clickhouse-tenant-setup.sh (Setup script)

### Documentation
- [x] MULTITENANCY_IMPLEMENTATION.md
- [x] MULTITENANCY_INTEGRATION_GUIDE.md
- [x] MULTITENANCY_SUMMARY.md

---

## Phase 2: Dependency Injection Setup ⏳ TODO

### MainModule.java Updates
- [ ] Add PasswordEncryptionUtil provider
- [ ] Add TenantConnectionPoolManager provider
- [ ] Add TenantCredentialsDao provider
- [ ] Add TenantService provider
- [ ] Update PerformanceMetricService binding

```java
@Provides
@Singleton
PasswordEncryptionUtil providePasswordEncryption() {
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

---

## Phase 3: Application Startup/Shutdown ⏳ TODO

### MainVerticle.java Updates
- [ ] Inject TenantConnectionPoolManager
- [ ] Inject TenantCredentialsDao
- [ ] Implement rxStart() with pool initialization
- [ ] Implement rxStop() with pool cleanup

```java
@Override
public Completable rxStart() {
    // Initialize admin pool
    // Load and initialize tenant pools
    // Setup HTTP server
}

@Override
public Completable rxStop() {
    // Close all tenant pools
}
```

---

## Phase 4: Environment Configuration ⏳ TODO

### Environment Variables
- [ ] ENCRYPTION_MASTER_KEY (generate with openssl rand -base64 32)
- [ ] CLICKHOUSE_HOST
- [ ] CLICKHOUSE_PORT
- [ ] CLICKHOUSE_USER
- [ ] CLICKHOUSE_PASSWORD
- [ ] MYSQL_HOST
- [ ] MYSQL_PORT
- [ ] MYSQL_DATABASE
- [ ] MYSQL_USER
- [ ] MYSQL_PASSWORD

### application.yml
- [ ] Add encryption configuration
- [ ] Update clickhouse configuration
- [ ] Verify mysql configuration

---

## Phase 5: Database Setup ⏳ TODO

### MySQL
- [ ] Generate encryption master key
- [ ] Run Flyway migrations: `mvn flyway:migrate`
- [ ] Verify tables created:
  - [ ] tenants
  - [ ] clickhouse_tenant_credentials
  - [ ] clickhouse_credential_audit
- [ ] Verify columns added:
  - [ ] interaction.tenant_id
  - [ ] alerts.tenant_id
  - [ ] pulse_sdk_configs.tenant_id

```sql
-- Verify
SELECT * FROM information_schema.TABLES WHERE TABLE_NAME IN (
  'tenants', 'clickhouse_tenant_credentials', 'clickhouse_credential_audit'
);
```

### ClickHouse
- [ ] Run clickhouse-tenant-rls-setup.sql
- [ ] Verify default user permissions
- [ ] Test connection with default user

```bash
# Verify permissions
SHOW USERS;
SHOW GRANTS FOR 'default'@'%';
```

---

## Phase 6: Tenant Registration ⏳ TODO

### Create ClickHouse User
- [ ] Run setup script: `./clickhouse-tenant-setup.sh tenant_abc "password"`
- [ ] Verify user created: `SHOW USERS;`
- [ ] Verify permissions: `SHOW GRANTS FOR 'tenant_abc'@'%';`

### Register in Pulse (Implement Endpoint)
- [ ] Create REST endpoint: `POST /api/v1/tenants/{tenantId}/credentials`
- [ ] Validate tenant exists
- [ ] Save encrypted credentials
- [ ] Create audit log entry

```bash
curl -X POST http://localhost:8080/api/v1/tenants/tenant_abc/credentials \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"password": "securePassword123"}'
```

---

## Phase 7: JWT Configuration ⏳ TODO

### Update JWT Token Structure
- [ ] Ensure JWT includes: sub, email, tenant_id claims
- [ ] Update parseJwtToken() with correct signing key
- [ ] Test JWT parsing with sample token

```java
// Expected JWT claims:
{
  "sub": "user_id_123",
  "email": "user@example.com",
  "tenant_id": "tenant_abc",
  "iat": 1234567890,
  "exp": 1234571490
}
```

---

## Phase 8: Testing ⏳ TODO

### Unit Tests
- [ ] PasswordEncryptionUtil encryption/decryption
- [ ] TenantCredentials model serialization
- [ ] TenantConnectionPoolManager pool creation
- [ ] TenantService business logic

### Integration Tests
- [ ] Single tenant query execution
- [ ] Multiple concurrent tenant queries
- [ ] Connection pool isolation
- [ ] Credential encryption/decryption

### Load Tests
- [ ] 10 concurrent tenants
- [ ] 100 concurrent queries per tenant
- [ ] Connection pool stress test
- [ ] Memory usage monitoring

```bash
# Example test with curl
for i in {1..5}; do
  curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
    -H "Authorization: Bearer $JWT" \
    -H "X-Tenant-Id: tenant_$i" \
    -H "Content-Type: application/json" \
    -d '{"dataType": "TRACES", ...}' &
done
wait
```

---

## Phase 9: Verification ⏳ TODO

### Application Startup
- [ ] No errors during startup
- [ ] Admin pool initialized
- [ ] Tenant pools pre-initialized
- [ ] Logs show successful initialization

```bash
# Check logs
grep -i "tenant\|pool\|credential" logs/pulse-server.log
```

### Database Verification
- [ ] MySQL: Tables exist and are populated
- [ ] ClickHouse: Users created with correct permissions
- [ ] MySQL: Encryption working (passwords stored encrypted)

```sql
-- MySQL checks
SELECT COUNT(*) FROM clickhouse_tenant_credentials;
SELECT COUNT(*) FROM clickhouse_credential_audit;

-- ClickHouse checks
SHOW USERS;
SELECT COUNT(*) FROM system.users WHERE name LIKE 'tenant_%';
```

### Query Execution
- [ ] Single tenant query returns results
- [ ] Multiple tenant queries are isolated
- [ ] No cross-tenant data leakage

```bash
# Test tenant isolation
curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Authorization: Bearer $JWT_TENANT_ABC" \
  -H "X-Tenant-Id: tenant_abc" \
  -d '{"dataType": "TRACES"}'

curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Authorization: Bearer $JWT_TENANT_XYZ" \
  -H "X-Tenant-Id: tenant_xyz" \
  -d '{"dataType": "TRACES"}'

# Results should be different and tenant-specific
```

---

## Phase 10: Monitoring Setup ⏳ TODO

### Metrics to Monitor
- [ ] Connection pool statistics per tenant
- [ ] Query execution time per tenant
- [ ] Error rates per tenant
- [ ] Credential rotation events
- [ ] Audit log entries

### Dashboards
- [ ] Tenant pool utilization
- [ ] Query performance by tenant
- [ ] Error trends
- [ ] Credential age

### Alerts
- [ ] Pool exhaustion (>90% capacity)
- [ ] Credential expiration upcoming
- [ ] Authentication failures
- [ ] Query timeout errors

---

## Completion Status

| Phase | Status | Priority |
|-------|--------|----------|
| 1. Code Integration | ✅ COMPLETE | P1 |
| 2. Dependency Injection | ⏳ TODO | P2 |
| 3. Startup/Shutdown | ⏳ TODO | P2 |
| 4. Environment Config | ⏳ TODO | P2 |
| 5. Database Setup | ⏳ TODO | P2 |
| 6. Tenant Registration | ⏳ TODO | P2 |
| 7. JWT Configuration | ⏳ TODO | P1 |
| 8. Testing | ⏳ TODO | P2 |
| 9. Verification | ⏳ TODO | P1 |
| 10. Monitoring Setup | ⏳ TODO | P3 |

---

## Quick Start Commands

```bash
# 1. Generate encryption key
openssl rand -base64 32

# 2. Set environment variables
export ENCRYPTION_MASTER_KEY="<generated_key>"
export CLICKHOUSE_HOST=localhost
export CLICKHOUSE_PORT=8123

# 3. Run migrations
cd /Users/abhishekkumar/Desktop/pulse/backend/server
mvn flyway:migrate

# 4. Create tenant in ClickHouse
cd /Users/abhishekkumar/Desktop/pulse/backend/ingestion
./clickhouse-tenant-setup.sh tenant_abc "password123"

# 5. Start application
mvn spring-boot:run

# 6. Test query
curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Authorization: Bearer <JWT>" \
  -H "X-Tenant-Id: tenant_abc" \
  -H "Content-Type: application/json" \
  -d '{"dataType": "TRACES"}'
```

---

## Support Files

- ✅ [MULTITENANCY_IMPLEMENTATION.md](MULTITENANCY_IMPLEMENTATION.md) - Architecture & design
- ✅ [MULTITENANCY_INTEGRATION_GUIDE.md](MULTITENANCY_INTEGRATION_GUIDE.md) - Step-by-step guide
- ✅ [MULTITENANCY_SUMMARY.md](MULTITENANCY_SUMMARY.md) - Implementation summary

---

Last Updated: January 29, 2026
Status: Phase 1 Complete, Ready for Phase 2
