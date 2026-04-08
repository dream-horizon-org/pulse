# Multi-Tenancy Implementation - Phase 2 Setup Guide

**Date:** 29 January 2026  
**Purpose:** Complete configuration and deployment of multi-tenancy features

---

## Table of Contents
1. [Generate Encryption Key](#1-generate-encryption-key)
2. [Configure Environment Variables](#2-configure-environment-variables)
3. [Run Database Migrations](#3-run-database-migrations)
4. [Create ClickHouse Tenant Users](#4-create-clickhouse-tenant-users)
5. [End-to-End Testing](#5-end-to-end-testing)
6. [Troubleshooting](#troubleshooting)

---

## 1. Generate Encryption Key

### Step 1a: Generate Master Key

```bash
# Generate a secure 256-bit (32-byte) encryption master key
openssl rand -hex 32
```

**Output Example:**
```
a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2
```

### Step 1b: Save the Key Securely

```bash
# Store in secure location (do not commit to git)
echo "ENCRYPTION_MASTER_KEY=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2" >> ~/.env
```

### Step 1c: Verify Key Format

The key should be:
- ✅ 64 hexadecimal characters (32 bytes)
- ✅ No spaces or special characters
- ✅ Uppercase or lowercase (both valid)

---

## 2. Configure Environment Variables

### Step 2a: Create Local Environment File

**File:** `/Users/abhishekkumar/Desktop/pulse/.env.local` (gitignored)

```bash
# Encryption
export ENCRYPTION_MASTER_KEY=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2

# Existing MySQL Configuration
export MYSQL_WRITER_HOST=localhost
export MYSQL_READER_HOST=localhost
export MYSQL_DATABASE=pulse
export MYSQL_USER=pulse_user
export MYSQL_PASSWORD=pulse_password
export MYSQL_WRITER_MAX_POOL_SIZE=10
export MYSQL_READER_MAX_POOL_SIZE=10

# Existing ClickHouse Configuration
export CLICKHOUSE_HOST=localhost
export CLICKHOUSE_PORT=9000
export CLICKHOUSE_USERNAME=default
export CLICKHOUSE_PASSWORD=password
export CLICKHOUSE_R2DBC_URL=r2dbc:clickhouse://localhost:9000/default

# Multi-Tenancy Settings
export TENANT_POOL_MIN_SIZE=2
export TENANT_POOL_MAX_SIZE=5
export ADMIN_POOL_SIZE=10
export TENANT_POOL_IDLE_TIMEOUT_MS=300000
export MULTITENANCY_ENABLED=true
```

### Step 2b: Load Environment Variables

```bash
# Add to ~/.zshrc or ~/.bash_profile
if [ -f ~/.env.local ]; then
    export $(cat ~/.env.local | xargs)
fi

# Or load manually for this session
source ~/.env.local
```

### Step 2c: Verify Environment Variables

```bash
# Check that variables are loaded
echo $ENCRYPTION_MASTER_KEY
echo $MYSQL_DATABASE
echo $CLICKHOUSE_HOST
```

---

## 3. Run Database Migrations

### Step 3a: Verify MySQL Connection

```bash
# Test MySQL connection
mysql -h ${MYSQL_WRITER_HOST} -u ${MYSQL_USER} -p${MYSQL_PASSWORD} -e "SELECT 1;"

# Expected output: 1
```

### Step 3b: Locate Migration Files

```bash
# The migration file is already created at:
ls -la /Users/abhishekkumar/Desktop/pulse/backend/server/src/main/resources/db/migration/
# Look for: V1_1__add_tenant_multitenancy.sql
```

### Step 3c: Run Migration with Flyway

If using Flyway (check pom.xml):

```bash
cd /Users/abhishekkumar/Desktop/pulse/backend/server

# Run Flyway migration
mvn flyway:migrate -Dflyway.configFiles=flyway.properties

# Or manually if Flyway not configured
mvn flyway:migrate \
  -Dflyway.url="jdbc:mysql://${MYSQL_WRITER_HOST}:3306/${MYSQL_DATABASE}" \
  -Dflyway.user="${MYSQL_USER}" \
  -Dflyway.password="${MYSQL_PASSWORD}"
```

### Step 3d: Manual Migration (if Flyway not available)

```bash
# Connect to MySQL
mysql -h ${MYSQL_WRITER_HOST} -u ${MYSQL_USER} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE}

# Run the migration SQL
SOURCE /Users/abhishekkumar/Desktop/pulse/backend/server/src/main/resources/db/migration/V1_1__add_tenant_multitenancy.sql;

# Verify tables were created
SHOW TABLES LIKE 'clickhouse%';
SHOW TABLES LIKE '%tenant%';
```

### Step 3e: Verify Migration Success

```sql
-- Connect to MySQL
mysql -h ${MYSQL_WRITER_HOST} -u ${MYSQL_USER} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE}

-- Check created tables
DESCRIBE tenants;
DESCRIBE clickhouse_tenant_credentials;
DESCRIBE clickhouse_credential_audit;

-- Expected columns:
-- tenants: tenant_id, name, is_active, created_at, updated_at
-- clickhouse_tenant_credentials: credential_id, tenant_id, clickhouse_username, clickhouse_password_encrypted, encryption_salt, password_digest, is_active, created_at, updated_at
-- clickhouse_credential_audit: audit_id, tenant_id, action, performed_by, details, created_at
```

---

## 4. Create ClickHouse Tenant Users

### Step 4a: Connect to ClickHouse

```bash
# Using clickhouse-client
clickhouse-client \
  --host ${CLICKHOUSE_HOST} \
  --port ${CLICKHOUSE_PORT} \
  --user ${CLICKHOUSE_USERNAME} \
  --password ${CLICKHOUSE_PASSWORD}
```

### Step 4b: Create First Tenant (acme_corp)

```sql
-- Connect as admin first (in ClickHouse client)

-- Create tenant user
CREATE USER tenant_acme_corp IDENTIFIED BY 'secure_password_123';

-- Create tenant database (if needed)
CREATE DATABASE IF NOT EXISTS acme_corp_data;

-- Grant permissions for tenant data access
GRANT SELECT, INSERT, UPDATE, DELETE ON acme_corp_data.* TO tenant_acme_corp;

-- Grant specific table permissions with row-level security
-- (adjust based on your actual tables)
GRANT SELECT ON otel_traces TO tenant_acme_corp;
GRANT SELECT ON otel_logs TO tenant_acme_corp;
GRANT SELECT ON otel_metrics TO tenant_acme_corp;

-- Verify user was created
SHOW USERS LIKE 'tenant_%';

-- Verify permissions
SHOW GRANTS FOR tenant_acme_corp;
```

### Step 4c: Register Tenant in MySQL

```bash
# This can be done programmatically or manually

# Option 1: Manual SQL Insert
mysql -h ${MYSQL_WRITER_HOST} -u ${MYSQL_USER} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE}

INSERT INTO tenants (tenant_id, name, is_active)
VALUES ('acme_corp', 'ACME Corporation', 1);

-- Verify insert
SELECT * FROM tenants;
```

### Step 4d: Register Tenant Credentials

```bash
# Use the TenantService API or direct SQL

# Option 1: Using the application API (after deployment)
curl -X POST http://localhost:8080/v1/tenants/register \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -d '{
    "tenantId": "acme_corp",
    "clickhouseUsername": "tenant_acme_corp",
    "clickhousePassword": "secure_password_123"
  }'

# Option 2: Direct MySQL Insert (for testing)
-- Generate encrypted password using Java:
-- PasswordEncryptionUtil.encryptPassword("secure_password_123")
-- This will return: {encryptedPassword, salt, digest}

INSERT INTO clickhouse_tenant_credentials 
  (tenant_id, clickhouse_username, clickhouse_password_encrypted, encryption_salt, password_digest, is_active)
VALUES 
  ('acme_corp', 'tenant_acme_corp', '${ENCRYPTED_PASSWORD}', '${SALT}', '${DIGEST}', 1);
```

### Step 4e: Verify Tenant Setup

```bash
# Test tenant user connection
clickhouse-client \
  --host ${CLICKHOUSE_HOST} \
  --port ${CLICKHOUSE_PORT} \
  --user tenant_acme_corp \
  --password 'secure_password_123' \
  -q "SELECT 1;"

# Should output: 1

# Verify permissions
clickhouse-client \
  --host ${CLICKHOUSE_HOST} \
  --port ${CLICKHOUSE_PORT} \
  --user tenant_acme_corp \
  --password 'secure_password_123' \
  -q "SHOW TABLES;"
```

### Step 4f: Create Additional Tenants (Example)

```bash
# Repeat for each additional tenant

# TENANT 2: globex_corp
clickhouse-client -h ${CLICKHOUSE_HOST} -u ${CLICKHOUSE_USERNAME} -p${CLICKHOUSE_PASSWORD} << EOF

CREATE USER tenant_globex_corp IDENTIFIED BY 'secure_password_456';
CREATE DATABASE IF NOT EXISTS globex_corp_data;
GRANT SELECT, INSERT, UPDATE, DELETE ON globex_corp_data.* TO tenant_globex_corp;

EOF

# Register in MySQL
mysql -h ${MYSQL_WRITER_HOST} -u ${MYSQL_USER} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} << EOF

INSERT INTO tenants (tenant_id, name, is_active)
VALUES ('globex_corp', 'Globex Corporation', 1);

EOF
```

---

## 5. End-to-End Testing

### Step 5a: Build and Run Application

```bash
cd /Users/abhishekkumar/Desktop/pulse/backend/server

# Build with multi-tenancy support
export JAVA_HOME=$(/usr/libexec/java_home -v 23)
mvn clean package -DskipTests

# Run the application
java -jar target/pulse-server-*.jar \
  --ENCRYPTION_MASTER_KEY=${ENCRYPTION_MASTER_KEY} \
  --MYSQL_WRITER_HOST=${MYSQL_WRITER_HOST} \
  --MYSQL_DATABASE=${MYSQL_DATABASE} \
  --CLICKHOUSE_HOST=${CLICKHOUSE_HOST}
```

### Step 5b: Test Tenant Context Extraction

```bash
# Get JWT token (example - adjust based on your auth setup)
JWT_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Test performance metrics endpoint with tenant ID
curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "X-Tenant-Id: acme_corp" \
  -d '{
    "dataType": "METRICS",
    "select": [
      {
        "function": "CRASH_RATE",
        "alias": "crash_rate"
      }
    ],
    "groupBy": ["interactionName"],
    "startTime": 1704067200000,
    "endTime": 1704153600000
  }'

# Expected: 200 OK with metric data for acme_corp only
```

### Step 5c: Verify Tenant Isolation

```bash
# Query as tenant_acme_corp
curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACME_JWT_TOKEN}" \
  -H "X-Tenant-Id: acme_corp" \
  -d '{"dataType": "METRICS", ...}'

# Save response as ACME_RESULT

# Query as tenant_globex_corp
curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${GLOBEX_JWT_TOKEN}" \
  -H "X-Tenant-Id: globex_corp" \
  -d '{"dataType": "METRICS", ...}'

# Save response as GLOBEX_RESULT

# Verify: ACME_RESULT should NOT contain globex_corp data
# Verify: GLOBEX_RESULT should NOT contain acme_corp data

echo "Isolation test:"
echo "ACME contains acme data: $(grep acme_corp ACME_RESULT)"
echo "GLOBEX contains globex data: $(grep globex_corp GLOBEX_RESULT)"
```

### Step 5d: Test Connection Pool Management

```bash
# Monitor pool statistics (if enabled)
curl -X GET http://localhost:8080/v1/admin/pools/stats \
  -H "Authorization: Bearer ${ADMIN_JWT_TOKEN}"

# Expected output:
# {
#   "adminPool": { "activeConnections": 2, "idleConnections": 8, "totalConnections": 10 },
#   "tenantPools": {
#     "acme_corp": { "activeConnections": 1, "idleConnections": 1, "totalConnections": 2 },
#     "globex_corp": { "activeConnections": 0, "idleConnections": 2, "totalConnections": 2 }
#   }
# }
```

### Step 5e: Test Error Cases

```bash
# Test 1: Missing X-Tenant-Id header
curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -d '{"dataType": "METRICS", ...}'

# Expected: 400 Bad Request - "X-Tenant-Id header is required"

# Test 2: Invalid tenant ID
curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${JWT_TOKEN}" \
  -H "X-Tenant-Id: invalid_tenant" \
  -d '{"dataType": "METRICS", ...}'

# Expected: 404 Not Found or credentials not found error

# Test 3: Credentials mismatch (tenant in header vs JWT)
curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer ${ACME_JWT_TOKEN}" \
  -H "X-Tenant-Id: globex_corp" \
  -d '{"dataType": "METRICS", ...}'

# Expected: 403 Forbidden - "Tenant mismatch between header and token"
```

### Step 5f: Performance Testing

```bash
# Load test with multiple tenants
# Use Apache JMeter or wrk

# Example with wrk:
wrk -t4 -c100 -d30s -s multi-tenant-load.lua http://localhost:8080

# multi-tenant-load.lua:
# request = function()
#   return wrk.format(nil,
#     "/v1/interactions/performance-metric/distribution",
#     {
#       ["X-Tenant-Id"] = "acme_corp",
#       ["Authorization"] = "Bearer " .. jwt_token
#     },
#     '{"dataType":"METRICS",...}')
# end

# Monitor metrics:
# - Request latency
# - Connection pool utilization
# - Tenant isolation maintained under load
```

---

## Troubleshooting

### Issue: ENCRYPTION_MASTER_KEY not found

```bash
# Verify environment variable is set
echo $ENCRYPTION_MASTER_KEY

# If empty, reload environment:
source ~/.env.local
export $(cat ~/.env.local | xargs)

# Verify again
echo $ENCRYPTION_MASTER_KEY
```

### Issue: Connection to ClickHouse fails

```bash
# Verify ClickHouse is running
telnet ${CLICKHOUSE_HOST} ${CLICKHOUSE_PORT}

# Test with clickhouse-client
clickhouse-client -h ${CLICKHOUSE_HOST} -u ${CLICKHOUSE_USERNAME} -p${CLICKHOUSE_PASSWORD} -q "SELECT 1;"

# Check ClickHouse logs
tail -f /var/log/clickhouse-server/clickhouse-server.log
```

### Issue: Tenant credentials not found

```bash
# Verify credentials are in MySQL
mysql -h ${MYSQL_WRITER_HOST} -u ${MYSQL_USER} -p${MYSQL_PASSWORD} ${MYSQL_DATABASE} \
  -e "SELECT * FROM clickhouse_tenant_credentials WHERE tenant_id='acme_corp';"

# Check decryption is working
# Enable debug logging in PasswordEncryptionUtil
# Verify ENCRYPTION_MASTER_KEY is correct
```

### Issue: Cross-tenant data visible

```bash
# Check ClickHouse user permissions
clickhouse-client -h ${CLICKHOUSE_HOST} -u ${CLICKHOUSE_USERNAME} -p${CLICKHOUSE_PASSWORD} \
  -q "SHOW GRANTS FOR tenant_acme_corp;"

# Verify row-level policies
SELECT name FROM system.row_policies WHERE database='default';

# Check tenant_id filter in actual queries
# Enable query logging in ClickHouse
```

### Issue: Connection pool exhaustion

```bash
# Check pool statistics
curl -X GET http://localhost:8080/v1/admin/pools/stats \
  -H "Authorization: Bearer ${ADMIN_JWT_TOKEN}"

# If connections exhausted:
# 1. Increase TENANT_POOL_MAX_SIZE
# 2. Reduce TENANT_POOL_IDLE_TIMEOUT_MS
# 3. Add connection pool monitoring

# Restart application if stuck
pkill -9 java
```

---

## Verification Checklist

- [ ] ENCRYPTION_MASTER_KEY generated and set
- [ ] All environment variables loaded correctly
- [ ] MySQL migration completed successfully
- [ ] MySQL tables created and verified
- [ ] ClickHouse tenant users created
- [ ] Tenant credentials registered in MySQL
- [ ] Application builds successfully
- [ ] Application starts without errors
- [ ] Single tenant query works
- [ ] Multi-tenant isolation verified
- [ ] Connection pool statistics available
- [ ] Error cases handled gracefully
- [ ] Performance acceptable under load

---

## Next Steps

1. **Deployment:** Deploy to staging/production with this configuration
2. **Monitoring:** Set up alerting for pool exhaustion, encryption errors
3. **Automation:** Implement automated tenant registration/offboarding
4. **Documentation:** Update operational runbooks
5. **Training:** Brief team on new multi-tenant architecture

---

**Need Help?**
- Check logs: `tail -f logs/application.log`
- Enable debug: Add `--debug` flag to startup
- Contact: Refer to MULTITENANCY_IMPLEMENTATION_STATUS.md for architecture details
