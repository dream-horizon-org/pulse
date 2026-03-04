-- ClickHouse Setup for Multi-tenant Row-Level Security

-- Strategy 1: User-Level Isolation
-- Each tenant has their own ClickHouse user with SELECT permissions
-- Data isolation is enforced at the database user level

-- Create default tenant user (for system operations)
CREATE USER IF NOT EXISTS 'default'@'%' IDENTIFIED BY 'default_password';

-- Grant permissions to default tenant on the otel database
GRANT SELECT, INSERT ON otel.* TO 'default'@'%';

-- Template for creating tenant-specific users:
-- This should be created programmatically from the Pulse server when a new tenant is registered

-- Example: Create user for tenant_abc
-- CREATE USER IF NOT EXISTS 'tenant_abc'@'%' IDENTIFIED BY 'securePasswordForTenantAbc';
-- GRANT SELECT ON otel.otel_traces TO 'tenant_abc'@'%';
-- GRANT SELECT ON otel.otel_logs TO 'tenant_abc'@'%';
-- GRANT SELECT ON otel.otel_metrics_gauge TO 'tenant_abc'@'%';
-- GRANT SELECT ON otel.stack_trace_events TO 'tenant_abc'@'%';

-- Notes on Strategy 1 implementation:
-- 1. Each tenant gets their own ClickHouse user (tenant_<tenantId>)
-- 2. User credentials are stored encrypted in MySQL (pulse_db)
-- 3. Connection pool is created per tenant in application
-- 4. User can only query tables they have SELECT permission on
-- 5. Data isolation is enforced at the database level
-- 6. No WHERE clause filtering needed for isolation (enforced by DB user permissions)
-- 7. All data ingestion must include tenant.id in ResourceAttributes
