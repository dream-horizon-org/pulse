# Multi-Tenancy Implementation for Pulse Server

## Overview

This document describes the multi-tenancy implementation for Pulse using **Strategy 1: User-Level Isolation** with ClickHouse row-level security.

## Architecture

### Strategy 1: User-Level Isolation

Each tenant has their own ClickHouse user with specific permissions. Data isolation is enforced at the database level, not the application level.

```
Request Flow:
1. Client sends X-Tenant-Id header
2. Controller extracts tenant ID and JWT token
3. Service retrieves tenant credentials from MySQL
4. TenantConnectionPoolManager gets cached pool for tenant
5. Query executes on tenant-specific ClickHouse user
6. ClickHouse enforces row-level security via user permissions
```

## Components

### 1. PasswordEncryptionUtil
- **File**: `util/PasswordEncryptionUtil.java`
- **Purpose**: AES-256 encryption for tenant passwords
- **Features**:
  - Encrypts passwords with random salt
  - Generates SHA-256 digests for verification
  - Supports password verification

### 2. TenantContextDto
- **File**: `dto/TenantContextDto.java`
- **Purpose**: Carries tenant information throughout request lifecycle
- **Contains**:
  - tenantId, userId, userEmail
  - ClickHouse username and decrypted password

### 3. TenantCredentials Model
- **File**: `model/TenantCredentials.java`
- **Purpose**: Database model for tenant credentials
- **Storage**: MySQL with encrypted passwords

### 4. TenantCredentialsDao
- **File**: `dao/interaction/TenantCredentialsDao.java`
- **Purpose**: Data access layer for tenant credentials
- **Operations**:
  - Save/update credentials
  - Fetch credentials by tenant ID
  - Get all active tenant credentials
  - Audit logging

### 5. TenantConnectionPoolManager
- **File**: `client/chclient/TenantConnectionPoolManager.java`
- **Purpose**: Manages per-tenant ClickHouse connection pools
- **Features**:
  - Admin pool created at startup
  - Lazy initialization of tenant pools
  - Thread-safe caching with per-tenant locks
  - 2-5 connections per tenant (configurable)
  - Proper pool cleanup on shutdown

### 6. TenantService
- **File**: `service/TenantService.java`
- **Purpose**: Business logic for tenant management
- **Operations**:
  - Register new tenant
  - Retrieve tenant context
  - Rotate passwords
  - Deactivate tenant

### 7. PerformanceMetricServiceImpl
- **File**: `service/interaction/PerformanceMetricServiceImpl.java`
- **Purpose**: Query service with tenant awareness
- **Features**:
  - Extracts tenantId from request
  - Fetches tenant credentials
  - Uses tenant-specific connection pool
  - Builds dynamic SQL queries

### 8. PerformanceMetricDistribution Controller
- **File**: `resources/performance/PerformanceMetricDistribution.java`
- **Purpose**: REST API endpoint
- **Headers Required**:
  - `Authorization: Bearer <JWT_TOKEN>`
  - `X-Tenant-Id: <TENANT_ID>`

## Database Schema

### MySQL Tables

#### tenants
```sql
- tenant_id (PK)
- tenant_name
- description
- is_active
- created_at, updated_at
```

#### clickhouse_tenant_credentials
```sql
- credential_id (PK)
- tenant_id (FK)
- clickhouse_username
- clickhouse_password_encrypted (AES-256)
- encryption_salt
- password_digest (SHA-256)
- is_active
- created_at, updated_at
```

#### clickhouse_credential_audit
```sql
- audit_id (PK)
- tenant_id (FK)
- action (CREATE, UPDATE, DELETE, DEACTIVATE)
- performed_by
- action_timestamp
- details (JSON)
```

### ClickHouse Tables

Each tenant has their own ClickHouse user with SELECT permissions:

```sql
CREATE USER 'tenant_abc'@'%' IDENTIFIED BY 'securePassword';
GRANT SELECT ON otel.otel_traces TO 'tenant_abc'@'%';
GRANT SELECT ON otel.otel_logs TO 'tenant_abc'@'%';
GRANT SELECT ON otel.otel_metrics_gauge TO 'tenant_abc'@'%';
GRANT SELECT ON otel.stack_trace_events TO 'tenant_abc'@'%';
```

## Configuration

### Environment Variables

```bash
# Encryption master key (must be 256-bit Base64 encoded)
ENCRYPTION_MASTER_KEY=<base64_encoded_256bit_key>

# ClickHouse configuration
CLICKHOUSE_HOST=localhost
CLICKHOUSE_PORT=8123
CLICKHOUSE_USER=default
CLICKHOUSE_PASSWORD=
CLICKHOUSE_R2DBC_URL=r2dbc:clickhouse://localhost:8123/

# MySQL configuration (for pulse_db)
MYSQL_HOST=localhost
MYSQL_PORT=3306
MYSQL_DATABASE=pulse_db
MYSQL_USER=pulse_user
MYSQL_PASSWORD=pulse_password
```

### application.yml

```yaml
encryption:
  master-key: "${ENCRYPTION_MASTER_KEY}"
  algorithm: "AES"
  keysize: 256

clickhouse:
  r2dbc-url: "r2dbc:clickhouse://${CLICKHOUSE_HOST}:${CLICKHOUSE_PORT}/"
  username: "${CLICKHOUSE_USER}"
  password: "${CLICKHOUSE_PASSWORD}"
  initsize: 10
  maxsize: 50
```

## Request Flow

### 1. Client Request

```bash
curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Authorization: Bearer eyJhbGc..." \
  -H "X-Tenant-Id: tenant_abc" \
  -H "Content-Type: application/json" \
  -d '{
    "dataType": "TRACES",
    "timeRange": { "start": "2024-01-01T00:00:00", "end": "2024-01-01T23:59:59" },
    "select": [{"name": "SpanName"}, {"name": "Duration"}],
    "filters": [{"field": "ServiceName", "operator": "EQ", "value": ["payment"]}],
    "groupBy": ["ServiceName"],
    "limit": 1000
  }'
```

### 2. Controller Processing

- Validates `X-Tenant-Id` header
- Extracts JWT token
- Parses JWT claims (userId, userEmail)
- Enriches QueryRequest with tenant context
- Passes to service

### 3. Service Processing

- Extracts tenantId from request
- Builds dynamic SQL query
- Creates QueryConfiguration
- Fetches tenant credentials from MySQL
- Retrieves tenant-specific connection pool
- Executes query

### 4. Query Execution

- Connection pool established for tenant_abc
- Query runs as `tenant_abc` ClickHouse user
- ClickHouse enforces row-level security
- Results returned to client

## API Endpoints

### Get Metric Distribution

```
POST /v1/interactions/performance-metric/distribution
Headers:
  Authorization: Bearer <JWT>
  X-Tenant-Id: <TENANT_ID>
  Content-Type: application/json

Body: QueryRequest DTO with:
  - dataType (TRACES, LOGS, METRICS, EXCEPTIONS)
  - timeRange
  - select, filters, groupBy, orderBy, limit
```

## Tenant Registration

### 1. Create ClickHouse User

```bash
./clickhouse-tenant-setup.sh tenant_abc "securePassword123"
```

### 2. Register in Pulse via API (TODO)

```bash
POST /api/v1/tenants/tenant_abc/credentials
Headers:
  Authorization: Bearer <ADMIN_JWT>
Body:
  {
    "password": "securePassword123"
  }
```

## Security Features

1. **Password Encryption**: All passwords encrypted with AES-256 in MySQL
2. **Salt & Digest**: Each password has random salt and SHA-256 digest
3. **User-Level Isolation**: ClickHouse user permissions enforce data access
4. **Audit Logging**: All credential changes logged with timestamp and user
5. **Connection Pooling**: Minimal credentials in memory
6. **JWT Validation**: Tenant context from verified JWT token

## Connection Pool Management

### Startup

```java
@Inject
private TenantConnectionPoolManager poolManager;
private TenantCredentialsDao credentialsDao;

public void initializePools() {
    // Admin pool created upfront
    poolManager.initializeAdminPool();
    
    // Load active tenants and initialize pools
    credentialsDao.getAllActiveTenantCredentials()
        .subscribe(credentials -> {
            poolManager.getPoolForTenant(
                credentials.getTenantId(),
                credentials.getClickhouseUsername(),
                credentials.getClickhousePassword()
            );
        });
}
```

### Runtime

- Pools cached after first access
- Per-tenant locks prevent race conditions
- Idle connections closed after 5 minutes
- Maximum 5 connections per tenant

### Shutdown

```java
public void cleanup() {
    poolManager.closeAllPools(); // Close all tenant pools
}
```

## Monitoring & Debugging

### Pool Statistics

```java
TenantConnectionPoolManager.PoolStatistics stats = 
    poolManager.getPoolStatistics(tenantId);
    
System.out.println("Active connections: " + stats.activeConnections);
System.out.println("Max connections: " + stats.maxConnections);
System.out.println("Is active: " + stats.isActive);
```

### Audit Logs

View all credential changes in MySQL:

```sql
SELECT * FROM clickhouse_credential_audit 
WHERE tenant_id = 'tenant_abc' 
ORDER BY action_timestamp DESC;
```

### Logs

```
INFO  Registering new tenant: tenant_abc
INFO  Saved ClickHouse credentials for tenant: tenant_abc
INFO  Creating new connection pool for tenant: tenant_abc with 5 max connections
INFO  Executing query for tenant: tenant_abc, user: user@example.com
INFO  Query executed successfully for tenant: tenant_abc
```

## Troubleshooting

### Issue: "No credentials found for tenant"

**Cause**: Tenant not registered in MySQL

**Solution**: Register tenant credentials first

```bash
./clickhouse-tenant-setup.sh tenant_abc "password"
# Then register in Pulse
POST /api/v1/tenants/tenant_abc/credentials
```

### Issue: "ClickHouse user permission denied"

**Cause**: User doesn't have SELECT permission on table

**Solution**: Grant permissions

```bash
curl -X POST "http://localhost:8123/" \
  -u "default:password" \
  -d "GRANT SELECT ON otel.otel_traces TO 'tenant_abc'@'%'"
```

### Issue: "Connection pool exhausted"

**Cause**: Too many queries overwhelming pool

**Solution**: Increase pool size or optimize queries

```yaml
clickhouse:
  maxsize: 100  # Increase if needed
```

## Best Practices

1. **Separate Credentials**: Use unique passwords per tenant
2. **Rotate Regularly**: Rotate passwords every 90 days
3. **Monitor Connections**: Track pool statistics
4. **Audit Logs**: Review credential changes regularly
5. **Encrypt Master Key**: Rotate encryption master key
6. **Connection Cleanup**: Deactivate unused tenants
7. **Backup**: Regular database backups of credentials
8. **JWT Validation**: Always validate JWT claims match tenant ID

## Future Enhancements

1. **RBAC**: Role-based access control within tenant
2. **Rate Limiting**: Per-tenant query rate limits
3. **Query Caching**: Tenant-specific query result caching
4. **Metrics**: Tenants per pool, connection utilization
5. **Admin Console**: UI for tenant management
6. **Webhook**: Webhook on tenant creation/deletion
7. **Data Export**: Secure tenant data export feature
8. **Cost Attribution**: Track resource usage per tenant
