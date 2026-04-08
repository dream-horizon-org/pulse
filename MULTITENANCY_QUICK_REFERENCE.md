# Multi-Tenancy Quick Reference

## Essential Commands

### 1. Environment Setup (First Time)

```bash
# Generate 256-bit encryption key
export ENCRYPTION_MASTER_KEY=$(openssl rand -hex 32)
echo "Key: $ENCRYPTION_MASTER_KEY"

# Load configuration
source .env.local

# Verify all environment variables
env | grep -E "ENCRYPTION_MASTER_KEY|MYSQL|CLICKHOUSE"
```

### 2. Database Setup

```bash
# MySQL: Run migrations
mysql -h $MYSQL_WRITER_HOST -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  < backend/server/src/main/resources/db/migration/V1_1__add_tenant_multitenancy.sql

# Verify tables
mysql -h $MYSQL_WRITER_HOST -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  -e "SHOW TABLES LIKE '%tenant%';"

# ClickHouse: Create admin user
clickhouse-client -h $CLICKHOUSE_HOST -u default -p$CLICKHOUSE_PASSWORD \
  -q "CREATE USER IF NOT EXISTS clickhouse_admin IDENTIFIED BY 'admin_password';"
```

### 3. Create New Tenant

```bash
# Define tenant
TENANT_ID="acme_corp"
CH_USERNAME="tenant_acme_corp"
CH_PASSWORD=$(openssl rand -base64 32)

# Create ClickHouse user
clickhouse-client -h $CLICKHOUSE_HOST -u default -p$CLICKHOUSE_PASSWORD \
  -q "CREATE USER IF NOT EXISTS $CH_USERNAME IDENTIFIED BY '$CH_PASSWORD';"

# Grant permissions
clickhouse-client -h $CLICKHOUSE_HOST -u default -p$CLICKHOUSE_PASSWORD \
  -q "GRANT SELECT ON otel_traces TO $CH_USERNAME; \
      GRANT SELECT ON otel_logs TO $CH_USERNAME; \
      GRANT SELECT ON otel_metrics TO $CH_USERNAME;"

# Register in MySQL
mysql -h $MYSQL_WRITER_HOST -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  -e "INSERT INTO tenants (tenant_id, name, is_active) \
      VALUES ('$TENANT_ID', 'ACME Corporation', 1);"
```

### 4. Build & Deploy

```bash
# Set Java version
export JAVA_HOME=$(/usr/libexec/java_home -v 23)

# Build
cd backend/server
mvn clean compile
mvn package -DskipTests

# Run
java -Dmultitenancy.enabled=true \
     -Dencryption.master.key=$ENCRYPTION_MASTER_KEY \
     -jar target/pulse-server-*.jar
```

### 5. Test Tenant Isolation

```bash
# Query as tenant 1
curl http://localhost:8080/api/v1/metrics \
  -H "X-Tenant-Id: acme_corp"

# Query as tenant 2
curl http://localhost:8080/api/v1/metrics \
  -H "X-Tenant-Id: beta_corp"

# Should return different data based on X-Tenant-Id header
```

---

## Configuration Files Location

| File | Purpose | Location |
|------|---------|----------|
| `.env.local` | Local environment variables | Root directory |
| `multitenancy-default.conf` | Multi-tenancy settings | Root directory |
| `application.yml` | Application configuration | `backend/server/src/main/resources/` |
| Migration | Database schema | `backend/server/src/main/resources/db/migration/` |

---

## File Structure

```
backend/server/src/main/java/com/pulse/
├── multitenancy/
│   ├── dao/
│   │   └── TenantCredentialsDao.java       # MySQL CRUD operations
│   ├── dto/
│   │   ├── TenantContextDto.java          # Tenant context holder
│   │   └── TenantCredentials.java         # Credentials model
│   ├── service/
│   │   └── TenantService.java             # Tenant lifecycle management
│   └── util/
│       ├── PasswordEncryptionUtil.java    # Encryption/decryption
│       └── TestTenantSetupUtil.java       # Test utilities
├── service/
│   └── ClickhouseQueryService.java        # Tenant-aware queries
├── model/
│   └── QueryConfiguration.java            # Query config with tenantId
├── handler/
│   └── PerformanceMetricDistribution.java # X-Tenant-Id extraction
├── MainModule.java                        # Dependency injection
└── MainVerticle.java                      # App startup/shutdown

backend/server/src/main/resources/
├── db/migration/
│   └── V1_1__add_tenant_multitenancy.sql # MySQL migrations
└── clickhouse/
    └── clickhouse-tenant-setup.sql        # ClickHouse setup
```

---

## Key Configuration Properties

```properties
# Encryption
encryption.masterKey=<256-bit hex key>
encryption.algorithm=AES/CBC/PKCS5Padding
encryption.keySize=256

# Connection Pools
pool.adminPoolSize=10
pool.tenantMinPoolSize=2
pool.tenantMaxPoolSize=5
pool.maxIdleTimeMinutes=5

# Database
mysql.writerHost=<hostname>
mysql.readerHost=<hostname>
mysql.username=<user>
mysql.password=<password>
mysql.database=pulse

# ClickHouse
clickhouse.host=<hostname>
clickhouse.port=9000
clickhouse.username=default
clickhouse.password=<password>

# Multi-Tenancy
multitenancy.enabled=true
```

---

## Debugging

### Check if Tenant Exists

```bash
mysql -h $MYSQL_WRITER_HOST -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  -e "SELECT * FROM tenants WHERE tenant_id='acme_corp';"
```

### Check Encrypted Credentials

```bash
mysql -h $MYSQL_WRITER_HOST -u $MYSQL_USER -p$MYSQL_PASSWORD $MYSQL_DATABASE \
  -e "SELECT tenant_id, clickhouse_username, \
             SUBSTRING(clickhouse_password_encrypted, 1, 30) as pwd_encrypted \
      FROM clickhouse_tenant_credentials WHERE tenant_id='acme_corp';"
```

### Test ClickHouse Connection

```bash
clickhouse-client -h $CLICKHOUSE_HOST \
  -u tenant_acme_corp \
  -p '<password>' \
  -q "SELECT 1;"
```

### Verify Encryption Key

```bash
# Key should be 64 hex characters (32 bytes = 256 bits)
echo -n $ENCRYPTION_MASTER_KEY | wc -c
# Output should be: 64
```

### Check Connection Pools

```bash
# Query active connections
mysql -h $MYSQL_WRITER_HOST -u root -p \
  -e "SHOW PROCESSLIST;" | grep pulse
```

### View Application Logs

```bash
# Real-time logs
tail -f logs/pulse.log

# Filter for multi-tenancy
grep -i "tenant\|multitenancy\|encryption" logs/pulse.log

# Filter for errors
grep "ERROR\|WARN" logs/pulse.log
```

---

## Common Issues & Quick Fixes

| Issue | Command |
|-------|---------|
| "Tenant not found" | `mysql ... -e "SELECT * FROM tenants;"` |
| "Failed to decrypt" | `echo $ENCRYPTION_MASTER_KEY` (verify it's set) |
| "ClickHouse auth failed" | `clickhouse-client ... -q "SHOW USERS;"` |
| "Connection pool timeout" | Restart app: `pkill -f pulse-server` |
| "X-Tenant-Id missing" | Add header: `-H "X-Tenant-Id: acme_corp"` |

---

## Performance Tuning

### Increase Connection Pool Size

```bash
# In multitenancy-default.conf
pool {
    tenantMaxPoolSize = 10    # Default: 5
    tenantMinPoolSize = 3     # Default: 2
}
```

### Enable Connection Pooling Logs

```bash
# In logback.xml
<logger name="com.pulse.multitenancy" level="DEBUG"/>
<logger name="io.r2dbc.pool" level="DEBUG"/>
```

### Monitor Pool Performance

```bash
# View active connections per pool
mysql -h $MYSQL_WRITER_HOST -u $MYSQL_USER -p$MYSQL_PASSWORD -e \
  "SELECT USER, COUNT(*) as connections FROM INFORMATION_SCHEMA.PROCESSLIST GROUP BY USER;"
```

---

## Useful Links & References

- **Encryption**: AES-256 in CBC mode with PKCS5 padding
- **Connection Pool**: R2DBC with reactive extensions (RxJava3)
- **Dependency Injection**: Google Guice 5.x
- **Encryption Master Key**: 256-bit key in hex format (64 characters)
- **Multi-Tenancy Strategy**: Strategy 1 (User-Level Isolation via RLS)

---

## Next Steps

1. ✅ Generate ENCRYPTION_MASTER_KEY
2. ✅ Configure .env.local
3. ✅ Run database migrations
4. ✅ Create ClickHouse users
5. ✅ Build application
6. ✅ Start application
7. ✅ Test tenant isolation
8. → Monitor performance and stability

---

## Support

For issues:

1. Check logs: `tail -f logs/pulse.log`
2. Verify environment: `env | grep ENCRYPTION_MASTER_KEY`
3. Test connectivity: `mysql ...` and `clickhouse-client ...`
4. Review configuration: `cat .env.local`
5. Consult troubleshooting: [MULTITENANCY_TESTING_GUIDE.md](MULTITENANCY_TESTING_GUIDE.md)

---

**Last Updated**: 2024
**Version**: Phase 2 Ready
