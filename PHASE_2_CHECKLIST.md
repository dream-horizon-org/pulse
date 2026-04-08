# Phase 2 Implementation Checklist

## Pre-Deployment Setup

### Environment Configuration

- [ ] **Generate Encryption Key**
  ```bash
  export ENCRYPTION_MASTER_KEY=$(openssl rand -hex 32)
  echo "Save this key securely: $ENCRYPTION_MASTER_KEY"
  ```
  - Verify key length: 64 hex characters (256-bit)
  - Store in secure location (Vault, Secrets Manager, etc.)
  - Document key rotation policy

- [ ] **Create .env.local Configuration File**
  ```bash
  cp .env.example .env.local
  
  # Edit .env.local with:
  export ENCRYPTION_MASTER_KEY="<generated-key>"
  export MYSQL_WRITER_HOST="<your-mysql-host>"
  export MYSQL_READER_HOST="<your-mysql-host>"
  export MYSQL_USER="<mysql-user>"
  export MYSQL_PASSWORD="<mysql-password>"
  export MYSQL_DATABASE="pulse"
  export CLICKHOUSE_HOST="<your-clickhouse-host>"
  export CLICKHOUSE_PORT="9000"
  export CLICKHOUSE_USERNAME="default"
  export CLICKHOUSE_PASSWORD="<ch-password>"
  ```

- [ ] **Load Environment Variables**
  ```bash
  source .env.local
  
  # Verify all variables are set
  env | grep -E "ENCRYPTION|MYSQL|CLICKHOUSE"
  ```

### Database Setup

- [ ] **Run MySQL Migrations**
  ```bash
  # Copy migration file
  cp backend/server/src/main/resources/db/migration/V1_1__add_tenant_multitenancy.sql /tmp/
  
  # Execute migration
  mysql -h $MYSQL_WRITER_HOST \
        -u $MYSQL_USER \
        -p$MYSQL_PASSWORD \
        $MYSQL_DATABASE \
        < /tmp/V1_1__add_tenant_multitenancy.sql
  
  # Verify tables created
  mysql -h $MYSQL_WRITER_HOST \
        -u $MYSQL_USER \
        -p$MYSQL_PASSWORD \
        $MYSQL_DATABASE \
        -e "SHOW TABLES LIKE '%tenant%';"
  ```

  Tables created:
  - [ ] `tenants` (id, tenant_id, name, created_at, updated_at, is_active)
  - [ ] `clickhouse_tenant_credentials` (tenant_id, username, password_encrypted, salt, digest, is_active)
  - [ ] `clickhouse_credential_audit` (id, tenant_id, action, performed_by, ip_address, timestamp)

- [ ] **Verify Schema**
  ```bash
  mysql -h $MYSQL_WRITER_HOST \
        -u $MYSQL_USER \
        -p$MYSQL_PASSWORD \
        $MYSQL_DATABASE \
        -e "DESCRIBE tenants; DESCRIBE clickhouse_tenant_credentials; DESCRIBE clickhouse_credential_audit;"
  ```

### ClickHouse Setup

- [ ] **Create Admin User (if not exists)**
  ```bash
  clickhouse-client -h $CLICKHOUSE_HOST \
                    -u default \
                    -p$CLICKHOUSE_PASSWORD \
                    -q "CREATE USER IF NOT EXISTS clickhouse_admin IDENTIFIED BY 'admin_password';"
  ```

- [ ] **Grant Admin Permissions**
  ```bash
  clickhouse-client -h $CLICKHOUSE_HOST \
                    -u default \
                    -p$CLICKHOUSE_PASSWORD \
                    -q "GRANT ALL ON *.* TO clickhouse_admin;"
  ```

- [ ] **Verify ClickHouse Connection**
  ```bash
  clickhouse-client -h $CLICKHOUSE_HOST \
                    -u default \
                    -p$CLICKHOUSE_PASSWORD \
                    -q "SELECT 1;"
  ```

### Build & Deployment

- [ ] **Clean Build**
  ```bash
  cd backend/server
  export JAVA_HOME=$(/usr/libexec/java_home -v 23)
  mvn clean compile -q
  
  # Expected: BUILD SUCCESS
  ```

- [ ] **Run Tests**
  ```bash
  mvn test -q
  
  # Expected: All tests pass
  ```

- [ ] **Package Application**
  ```bash
  mvn package -DskipTests -q
  
  # Expected: target/pulse-server-*.jar created
  ```

- [ ] **Verify JAR**
  ```bash
  jar -tf target/pulse-server-*.jar | grep -E "multitenancy|PasswordEncryption" | head -10
  ```

---

## Phase 2 Execution

### Step 1: Run Automated Setup Script

- [ ] **Execute Setup Script**
  ```bash
  chmod +x multitenancy-setup.sh
  
  # Quick mode (non-interactive)
  ./multitenancy-setup.sh --quick
  
  # OR Interactive mode (recommended for first run)
  ./multitenancy-setup.sh --interactive
  ```

  Script will:
  - [ ] Generate ENCRYPTION_MASTER_KEY (if not set)
  - [ ] Create .env.local configuration
  - [ ] Run MySQL migrations
  - [ ] Setup ClickHouse tenant
  - [ ] Build application

### Step 2: Manual Tenant Registration

- [ ] **Create First Production Tenant**
  
  For each tenant, execute:
  ```bash
  # 1. Generate ClickHouse user credentials
  TENANT_ID="acme_corp"
  CH_USERNAME="tenant_${TENANT_ID}"
  CH_PASSWORD=$(openssl rand -base64 32)
  
  # 2. Create ClickHouse user
  clickhouse-client -h $CLICKHOUSE_HOST \
                    -u default \
                    -p$CLICKHOUSE_PASSWORD \
                    -q "CREATE USER IF NOT EXISTS ${CH_USERNAME} IDENTIFIED BY '${CH_PASSWORD}';"
  
  # 3. Grant permissions
  clickhouse-client -h $CLICKHOUSE_HOST \
                    -u default \
                    -p$CLICKHOUSE_PASSWORD \
                    -q "GRANT SELECT ON otel_traces TO ${CH_USERNAME}; \
                        GRANT SELECT ON otel_logs TO ${CH_USERNAME}; \
                        GRANT SELECT ON otel_metrics TO ${CH_USERNAME};"
  
  # 4. Register in MySQL
  mysql -h $MYSQL_WRITER_HOST \
        -u $MYSQL_USER \
        -p$MYSQL_PASSWORD \
        $MYSQL_DATABASE \
        -e "INSERT INTO tenants (tenant_id, name, is_active) \
            VALUES ('${TENANT_ID}', 'ACME Corporation', 1);"
  
  # 5. Store encrypted credentials in MySQL
  # Note: This requires the application's PasswordEncryptionUtil
  # For now, insert placeholder (will be updated by application)
  ```

- [ ] **Verify Tenant Registration**
  ```bash
  mysql -h $MYSQL_WRITER_HOST \
        -u $MYSQL_USER \
        -p$MYSQL_PASSWORD \
        $MYSQL_DATABASE \
        -e "SELECT * FROM tenants WHERE is_active=1;"
  ```

### Step 3: Application Startup

- [ ] **Start Application**
  ```bash
  cd backend/server
  
  # Set environment
  source ../../.env.local
  export JAVA_HOME=$(/usr/libexec/java_home -v 23)
  
  # Start with multi-tenancy enabled
  java -Dmultitenancy.enabled=true \
       -Dencryption.master.key=$ENCRYPTION_MASTER_KEY \
       -jar target/pulse-server-*.jar
  ```

- [ ] **Verify Startup Logs**
  ```
  Expected output:
  ✅ Multi-tenancy feature enabled
  ✅ Loaded N active tenants
  ✅ Admin connection pool initialized
  ✅ Tenant connection pools initialized for: acme_corp, ...
  ✅ Application started successfully
  ```

- [ ] **Check Health Endpoint**
  ```bash
  curl http://localhost:8080/actuator/health
  
  # Expected: {"status":"UP"}
  ```

---

## Phase 2 Testing

### Unit Tests

- [ ] **Encryption Utility Tests**
  ```bash
  mvn test -Dtest=PasswordEncryptionUtilTest
  
  # Expected: All encryption/decryption tests pass
  ```

- [ ] **Tenant Context Tests**
  ```bash
  mvn test -Dtest=TenantContextDtoTest
  
  # Expected: All context creation/equality tests pass
  ```

### Integration Tests

- [ ] **Database Tests**
  ```bash
  mvn test -Dtest=TenantCredentialsDaoIntegrationTest
  
  # Expected: All CRUD operations pass
  ```

- [ ] **Service Tests**
  ```bash
  mvn test -Dtest=TenantServiceIntegrationTest
  
  # Expected: Tenant lifecycle tests pass
  ```

### End-to-End Tests

- [ ] **Create Test Tenant**
  ```bash
  TENANT_ID="test_e2e_$(date +%s)"
  
  curl -X POST http://localhost:8080/api/v1/admin/tenants \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"tenantId\": \"${TENANT_ID}\",
      \"name\": \"E2E Test Tenant\",
      \"clickhouseUsername\": \"tenant_e2e\",
      \"clickhousePassword\": \"test_password_123\"
    }"
  
  # Expected: 201 Created response
  ```

- [ ] **Tenant Isolation Test**
  ```bash
  # Insert data as tenant 1
  curl -X POST http://localhost:8080/api/v1/metrics \
    -H "X-Tenant-Id: acme_corp" \
    -H "Content-Type: application/json" \
    -d '{"metric": "cpu_usage", "value": 45, "timestamp": "'"$(date -u +%Y-%m-%dT%H:%M:%SZ)"'"}'
  
  # Query as tenant 1
  RESULT1=$(curl -s http://localhost:8080/api/v1/metrics \
    -H "X-Tenant-Id: acme_corp" | jq '.data | length')
  
  # Query as different tenant
  RESULT2=$(curl -s http://localhost:8080/api/v1/metrics \
    -H "X-Tenant-Id: beta_corp" | jq '.data | length')
  
  # Expected: RESULT1 > RESULT2 (acme_corp sees its data, beta_corp doesn't)
  ```

- [ ] **Encryption Test**
  ```bash
  # Verify password is encrypted in database
  mysql -h $MYSQL_WRITER_HOST \
        -u $MYSQL_USER \
        -p$MYSQL_PASSWORD \
        $MYSQL_DATABASE \
        -e "SELECT tenant_id, clickhouse_username, \
                   SUBSTRING(clickhouse_password_encrypted, 1, 50) as encrypted_pwd \
            FROM clickhouse_tenant_credentials \
            WHERE tenant_id='acme_corp';"
  
  # Expected: clickhouse_password_encrypted is not readable plaintext
  ```

### Manual Verification

- [ ] **Connection Pool Health**
  ```bash
  curl http://localhost:8080/actuator/health/db
  
  # Expected: All pools healthy
  ```

- [ ] **Configuration Verification**
  ```bash
  curl http://localhost:8080/actuator/configprops | jq '.propertySources[] | select(.name | contains("multitenancy"))'
  
  # Expected: All multi-tenancy properties visible
  ```

- [ ] **Logging Verification**
  ```bash
  # Check application logs for any errors
  tail -f logs/pulse.log | grep -E "ERROR|WARN"
  
  # Expected: No tenant-related errors
  ```

---

## Post-Deployment Verification

### Security Checklist

- [ ] **Encryption Key Security**
  - [ ] Key stored in secure vault (HashiCorp Vault, AWS Secrets Manager, etc.)
  - [ ] Key rotation policy documented
  - [ ] Access to key restricted to application service account
  - [ ] Key never logged or exposed in error messages

- [ ] **Database Security**
  - [ ] MySQL credentials use strong passwords (20+ characters)
  - [ ] MySQL users have minimal required permissions (no GRANT privilege)
  - [ ] ClickHouse users have role-based permissions (SELECT only on required tables)
  - [ ] Network access restricted (firewall rules, security groups)

- [ ] **API Security**
  - [ ] X-Tenant-Id header validation implemented
  - [ ] Invalid tenant IDs rejected with 401/403
  - [ ] No tenant data leakage between requests
  - [ ] Audit logging enabled for credential access

### Performance Baseline

- [ ] **Measure Query Latency**
  ```bash
  # Single tenant query
  time curl http://localhost:8080/api/v1/metrics?limit=1000 \
    -H "X-Tenant-Id: acme_corp"
  
  # Expected: < 500ms for typical query
  ```

- [ ] **Check Connection Pool Usage**
  ```bash
  # Should see active connections per tenant
  mysql -h $MYSQL_WRITER_HOST \
        -u $MYSQL_USER \
        -p$MYSQL_PASSWORD \
        -e "SHOW PROCESSLIST;"
  ```

- [ ] **Monitor Memory Usage**
  ```bash
  # Java process memory
  ps aux | grep java | grep pulse-server
  
  # Expected: Proportional to number of active tenants (~100-200MB per 5 tenants)
  ```

### Documentation

- [ ] **Record Deployment Details**
  - [ ] Deployment date and time
  - [ ] Encryption key fingerprint (last 8 chars: ${ENCRYPTION_MASTER_KEY: -8})
  - [ ] Database version and configuration
  - [ ] ClickHouse version and configuration
  - [ ] Application version (git commit hash)
  - [ ] Tenant IDs created

- [ ] **Create Runbook**
  - [ ] How to add new tenant
  - [ ] How to rotate encryption key
  - [ ] How to backup/restore credentials
  - [ ] How to troubleshoot tenant isolation issues
  - [ ] Escalation contacts

---

## Troubleshooting & Rollback

### Common Issues & Fixes

| Issue | Symptom | Fix |
|-------|---------|-----|
| ENCRYPTION_MASTER_KEY not set | "NullPointerException at PasswordEncryptionUtil" | `export ENCRYPTION_MASTER_KEY=<key>` and restart |
| MySQL migration not applied | "Table 'tenants' doesn't exist" | Re-run migration script |
| ClickHouse user not created | "Authentication failed for user 'tenant_*'" | Create user manually with clickhouse-client |
| Stale pool connections | "Timeout waiting for connection" | Restart application to refresh pools |
| Encryption key mismatch | "Failed to decrypt credentials" | Use correct ENCRYPTION_MASTER_KEY that was used to encrypt |

### Rollback Procedure

If critical issues found:

- [ ] **Stop Application**
  ```bash
  pkill -f pulse-server
  ```

- [ ] **Drop Migration** (if needed)
  ```bash
  mysql -h $MYSQL_WRITER_HOST \
        -u $MYSQL_USER \
        -p$MYSQL_PASSWORD \
        $MYSQL_DATABASE \
        -e "DROP TABLE IF EXISTS tenants, clickhouse_tenant_credentials, clickhouse_credential_audit;"
  ```

- [ ] **Remove ClickHouse Users** (if needed)
  ```bash
  clickhouse-client -h $CLICKHOUSE_HOST \
                    -u default \
                    -p$CLICKHOUSE_PASSWORD \
                    -q "DROP USER IF EXISTS tenant_*;"
  ```

- [ ] **Restore Previous Version**
  ```bash
  git checkout previous-tag
  mvn clean package -DskipTests
  ```

---

## Sign-Off

- [ ] **QA Verification**: All tests pass ✅
- [ ] **Security Review**: Encryption and isolation verified ✅
- [ ] **Performance Baseline**: Acceptable latency (<500ms) ✅
- [ ] **Documentation**: Runbook created and reviewed ✅
- [ ] **Deployment Date**: _______________
- [ ] **Verified By**: _______________
- [ ] **Approval**: _______________

---

## Next Steps (Phase 3)

After Phase 2 completion, proceed to:

1. **Multi-tenant API Endpoints** - Add CRUD operations for tenant management
2. **Authentication Enhancement** - Integrate tenant context with JWT
3. **Monitoring & Observability** - Add metrics for per-tenant performance
4. **Backup & Disaster Recovery** - Implement per-tenant backup procedures
5. **Scaling & Optimization** - Connection pooling tuning, caching strategies

---

**Last Updated**: 2024
**Status**: Ready for Phase 2 Execution
