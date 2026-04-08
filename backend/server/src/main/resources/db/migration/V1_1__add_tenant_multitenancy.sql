-- MySQL Migration for Multi-tenant Support
-- Creates tables for storing tenant credentials and audit logs

-- Table to store tenant information
CREATE TABLE IF NOT EXISTS tenants (
    tenant_id VARCHAR(255) PRIMARY KEY,
    tenant_name VARCHAR(500) NOT NULL,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Table to store encrypted ClickHouse credentials per tenant
CREATE TABLE IF NOT EXISTS clickhouse_tenant_credentials (
    credential_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(255) NOT NULL UNIQUE,
    clickhouse_username VARCHAR(255) NOT NULL,
    -- Store password as hashed/encrypted value (AES-256)
    clickhouse_password_encrypted VARCHAR(512) NOT NULL,
    -- Store salt for password encryption
    encryption_salt VARCHAR(64) NOT NULL,
    -- Store a digest for verification (SHA-256 hash)
    password_digest VARCHAR(256),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_ch_cred_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_ch_username (clickhouse_username)
);

-- Audit log for credential changes
CREATE TABLE IF NOT EXISTS clickhouse_credential_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(255) NOT NULL,
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(255) NOT NULL,
    old_username VARCHAR(255),
    new_username VARCHAR(255),
    action_timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    details JSON,
    CONSTRAINT fk_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    INDEX idx_tenant_audit (tenant_id, action_timestamp)
);

-- Add tenant_id column to existing tables (if not already present)
ALTER TABLE interaction ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255) DEFAULT 'default';
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255) DEFAULT 'default';
ALTER TABLE pulse_sdk_configs ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(255) DEFAULT 'default';

-- Create indexes for tenant columns
CREATE INDEX IF NOT EXISTS idx_interaction_tenant ON interaction(tenant_id);
CREATE INDEX IF NOT EXISTS idx_alerts_tenant ON alerts(tenant_id);
CREATE INDEX IF NOT EXISTS idx_sdk_config_tenant ON pulse_sdk_configs(tenant_id);

-- Insert default tenant record
INSERT IGNORE INTO tenants (tenant_id, tenant_name, description, is_active)
VALUES ('default', 'Default Tenant', 'Default tenant for single-tenant setup', TRUE);
