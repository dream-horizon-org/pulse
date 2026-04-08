#!/bin/bash
# =====================================================
# COMPLETE MULTI-TENANCY TEST SETUP
# Run this script to set up test tenants end-to-end
# =====================================================

set -e

echo "========================================"
echo "STEP 1: Create MySQL table for tenant credentials"
echo "========================================"

docker exec -i pulse-mysql mysql -upulse_user -ppulse_password pulse_db << 'MYSQL_EOF'
-- Create tenant credentials table if not exists
CREATE TABLE IF NOT EXISTS clickhouse_tenant_credentials (
    credential_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(100) NOT NULL UNIQUE,
    clickhouse_username VARCHAR(100) NOT NULL,
    clickhouse_password_encrypted TEXT NOT NULL,
    encryption_salt VARCHAR(100) NOT NULL,
    password_digest VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_active (tenant_id, is_active)
);

-- Create audit table for credential changes
CREATE TABLE IF NOT EXISTS clickhouse_credential_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(255) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

SELECT 'MySQL tables created successfully!' AS status;
MYSQL_EOF

echo ""
echo "========================================"
echo "STEP 2: Insert encrypted tenant credentials into MySQL"
echo "========================================"
echo "Using default dev encryption key: MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="
echo ""

docker exec -i pulse-mysql mysql -upulse_user -ppulse_password pulse_db << 'MYSQL_EOF'
-- Delete existing test tenants (for clean re-run)
DELETE FROM clickhouse_tenant_credentials WHERE tenant_id IN ('test_a', 'test_b');

-- Insert Tenant A (password: testPasswordA123)
INSERT INTO clickhouse_tenant_credentials
  (tenant_id, clickhouse_username, clickhouse_password_encrypted, encryption_salt, password_digest, is_active)
VALUES
  ('test_a', 'tenant_test_a', 'UJ/DLtwf1m4aAbOpeQ5nx2bRJlRRJd5ouCGiZ3c7nnM=', 'cSid8zCNP9X6R35fn4hHuQ==', 'OOMU3peUz8IYa4msp1ucxTe5RJBieogUjNjJP29E9NM=', TRUE);

-- Insert Tenant B (password: testPasswordB456)
INSERT INTO clickhouse_tenant_credentials
  (tenant_id, clickhouse_username, clickhouse_password_encrypted, encryption_salt, password_digest, is_active)
VALUES
  ('test_b', 'tenant_test_b', 'rg4IJJP3iDx0Skv+nrEzY2bRJlRRJd5ouCGiZ3c7nnM=', 'zkh77cXoKWXCWo0TEc6SqQ==', 'DLZFKLrnTiSID3VSYYD3aDbejPNnX5l/TrkXfi/JaXs=', TRUE);

SELECT tenant_id, clickhouse_username, is_active, created_at 
FROM clickhouse_tenant_credentials 
WHERE tenant_id IN ('test_a', 'test_b');
MYSQL_EOF

echo ""
echo "========================================"
echo "STEP 3: Create ClickHouse users and row policies"
echo "========================================"

docker exec -i pulse-clickhouse clickhouse-client --multiquery << 'CH_EOF'
-- Drop existing test users (for clean re-run)
DROP USER IF EXISTS 'tenant_test_a';
DROP USER IF EXISTS 'tenant_test_b';
DROP ROW POLICY IF EXISTS tenant_test_a_traces ON otel.otel_traces;
DROP ROW POLICY IF EXISTS tenant_test_a_logs ON otel.otel_logs;
DROP ROW POLICY IF EXISTS tenant_test_a_metrics ON otel.otel_metrics_gauge;
DROP ROW POLICY IF EXISTS tenant_test_a_stacktrace ON otel.stack_trace_events;
DROP ROW POLICY IF EXISTS tenant_test_b_traces ON otel.otel_traces;
DROP ROW POLICY IF EXISTS tenant_test_b_logs ON otel.otel_logs;
DROP ROW POLICY IF EXISTS tenant_test_b_metrics ON otel.otel_metrics_gauge;
DROP ROW POLICY IF EXISTS tenant_test_b_stacktrace ON otel.stack_trace_events;

-- Create Tenant Users
CREATE USER 'tenant_test_a' IDENTIFIED BY 'testPasswordA123';
CREATE USER 'tenant_test_b' IDENTIFIED BY 'testPasswordB456';

-- Grant SELECT permissions
GRANT SELECT ON otel.otel_traces TO 'tenant_test_a';
GRANT SELECT ON otel.otel_logs TO 'tenant_test_a';
GRANT SELECT ON otel.otel_metrics_gauge TO 'tenant_test_a';
GRANT SELECT ON otel.stack_trace_events TO 'tenant_test_a';

GRANT SELECT ON otel.otel_traces TO 'tenant_test_b';
GRANT SELECT ON otel.otel_logs TO 'tenant_test_b';
GRANT SELECT ON otel.otel_metrics_gauge TO 'tenant_test_b';
GRANT SELECT ON otel.stack_trace_events TO 'tenant_test_b';

-- Row Policies for Tenant A (can ONLY see TenantId = 'test_a')
CREATE ROW POLICY tenant_test_a_traces ON otel.otel_traces FOR SELECT USING TenantId = 'test_a' TO 'tenant_test_a';
CREATE ROW POLICY tenant_test_a_logs ON otel.otel_logs FOR SELECT USING TenantId = 'test_a' TO 'tenant_test_a';
CREATE ROW POLICY tenant_test_a_metrics ON otel.otel_metrics_gauge FOR SELECT USING TenantId = 'test_a' TO 'tenant_test_a';
CREATE ROW POLICY tenant_test_a_stacktrace ON otel.stack_trace_events FOR SELECT USING TenantId = 'test_a' TO 'tenant_test_a';

-- Row Policies for Tenant B (can ONLY see TenantId = 'test_b')
CREATE ROW POLICY tenant_test_b_traces ON otel.otel_traces FOR SELECT USING TenantId = 'test_b' TO 'tenant_test_b';
CREATE ROW POLICY tenant_test_b_logs ON otel.otel_logs FOR SELECT USING TenantId = 'test_b' TO 'tenant_test_b';
CREATE ROW POLICY tenant_test_b_metrics ON otel.otel_metrics_gauge FOR SELECT USING TenantId = 'test_b' TO 'tenant_test_b';
CREATE ROW POLICY tenant_test_b_stacktrace ON otel.stack_trace_events FOR SELECT USING TenantId = 'test_b' TO 'tenant_test_b';

SELECT 'ClickHouse users and policies created!' AS status;
SHOW USERS;
SHOW ROW POLICIES;
CH_EOF

echo ""
echo "========================================"
echo "STEP 4: Insert sample test data for each tenant"
echo "========================================"

docker exec -i pulse-clickhouse clickhouse-client --multiquery << 'CH_EOF'
-- Insert sample traces for Tenant A
INSERT INTO otel.otel_traces (
    Timestamp, TraceId, SpanId, ParentSpanId, TraceState, SpanName, SpanKind,
    ServiceName, ResourceAttributes, ScopeName, ScopeVersion, SpanAttributes,
    Duration, StatusCode, StatusMessage
) VALUES (
    now64(9),
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    '1111111111111111',
    '0000000000000000',
    '',
    'test-span-tenant-a',
    'INTERNAL',
    'test-service-a',
    {'tenant.id': 'test_a', 'service.name': 'test-service-a'},
    'test-scope',
    '1.0',
    {'pulse.type': 'test', 'user.id': 'user-a-1'},
    1000000,
    'OK',
    'Success from Tenant A'
);

-- Insert sample traces for Tenant B
INSERT INTO otel.otel_traces (
    Timestamp, TraceId, SpanId, ParentSpanId, TraceState, SpanName, SpanKind,
    ServiceName, ResourceAttributes, ScopeName, ScopeVersion, SpanAttributes,
    Duration, StatusCode, StatusMessage
) VALUES (
    now64(9),
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    '2222222222222222',
    '0000000000000000',
    '',
    'test-span-tenant-b',
    'INTERNAL',
    'test-service-b',
    {'tenant.id': 'test_b', 'service.name': 'test-service-b'},
    'test-scope',
    '1.0',
    {'pulse.type': 'test', 'user.id': 'user-b-1'},
    2000000,
    'OK',
    'Success from Tenant B'
);

SELECT 'Sample data inserted!' AS status;
SELECT TenantId, COUNT(*) as count FROM otel.otel_traces GROUP BY TenantId;
CH_EOF

echo ""
echo "========================================"
echo "STEP 5: Verify tenant isolation in ClickHouse"
echo "========================================"

echo ""
echo "--- As tenant_test_a (should ONLY see test_a data):"
docker exec -i pulse-clickhouse clickhouse-client \
  --user tenant_test_a --password testPasswordA123 \
  -q "SELECT TenantId, ServiceName, SpanName, StatusMessage FROM otel.otel_traces FORMAT Pretty"

echo ""
echo "--- As tenant_test_b (should ONLY see test_b data):"
docker exec -i pulse-clickhouse clickhouse-client \
  --user tenant_test_b --password testPasswordB456 \
  -q "SELECT TenantId, ServiceName, SpanName, StatusMessage FROM otel.otel_traces FORMAT Pretty"

echo ""
echo "========================================"
echo "SETUP COMPLETE!"
echo "========================================"
echo ""
echo "Test via Pulse Server API:"
echo ""
echo "# Test Tenant A:"
echo 'curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \'
echo '  -H "Content-Type: application/json" \'
echo '  -H "X-Tenant-Id: test_a" \'
echo '  -d '\''{"dataType": "TRACES", "timeRange": {"start": "2026-01-01T00:00:00Z", "end": "2026-12-31T23:59:59Z"}}'\'''
echo ""
echo "# Test Tenant B:"
echo 'curl -X POST http://localhost:8080/v1/interactions/performance-metric/distribution \'
echo '  -H "Content-Type: application/json" \'
echo '  -H "X-Tenant-Id: test_b" \'
echo '  -d '\''{"dataType": "TRACES", "timeRange": {"start": "2026-01-01T00:00:00Z", "end": "2026-12-31T23:59:59Z"}}'\'''
echo ""
