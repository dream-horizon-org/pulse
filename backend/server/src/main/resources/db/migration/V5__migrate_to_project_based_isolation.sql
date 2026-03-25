-- V5: Migrate from tenant-based to project-based data isolation
-- This migration replaces tenant_id with project_id in all resource tables
-- CRITICAL: This is a breaking change that requires careful execution

-- ========================================
-- STEP 1: CREATE DEFAULT PROJECTS FOR EXISTING TENANTS
-- ========================================

-- First, create a default project for each existing tenant
INSERT INTO projects (project_id, tenant_id, name, description, api_key, is_active, created_by)
SELECT 
    CONCAT('proj-default-', REPLACE(tenant_id, '-', '')),  -- Generate project_id
    tenant_id,
    CONCAT('Default Project - ', name),
    'Auto-created default project during migration',
    CONCAT('pulse_proj-default-', REPLACE(tenant_id, '-', ''), '_sk_', 
           SUBSTRING(MD5(RAND()), 1, 32)),  -- Generate API key
    TRUE,
    'system-migration'
FROM tenants
WHERE NOT EXISTS (
    SELECT 1 FROM projects WHERE projects.tenant_id = tenants.tenant_id
);

-- ========================================
-- STEP 2: MIGRATE interaction TABLE
-- ========================================

-- Add project_id column
ALTER TABLE interaction 
ADD COLUMN project_id VARCHAR(64) COMMENT 'Project ID (replaces tenant_id)';

-- Populate project_id with default project for each tenant
UPDATE interaction i
INNER JOIN projects p ON i.tenant_id = p.tenant_id
SET i.project_id = p.project_id
WHERE p.name LIKE 'Default Project%'
  AND i.project_id IS NULL;

-- Make project_id NOT NULL
ALTER TABLE interaction 
MODIFY project_id VARCHAR(64) NOT NULL;

-- Add foreign key
ALTER TABLE interaction 
ADD CONSTRAINT fk_interaction_project 
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE;

-- Update indexes
DROP INDEX IF EXISTS idx_interaction_tenant ON interaction;
DROP INDEX IF EXISTS idx_interaction_tenant_archived ON interaction;
CREATE INDEX idx_interaction_project ON interaction(project_id);
CREATE INDEX idx_interaction_project_archived ON interaction(project_id, is_archived);

-- Drop tenant_id column
ALTER TABLE interaction 
DROP FOREIGN KEY fk_interaction_tenant;
ALTER TABLE interaction 
DROP COLUMN tenant_id;

-- ========================================
-- STEP 3: MIGRATE alerts TABLE
-- ========================================

ALTER TABLE alerts 
ADD COLUMN project_id VARCHAR(64);

UPDATE alerts a
INNER JOIN projects p ON a.tenant_id = p.tenant_id
SET a.project_id = p.project_id
WHERE p.name LIKE 'Default Project%';

ALTER TABLE alerts 
MODIFY project_id VARCHAR(64) NOT NULL;

ALTER TABLE alerts 
ADD CONSTRAINT fk_alerts_project 
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_alerts_tenant ON alerts;
DROP INDEX IF EXISTS idx_alerts_tenant_active ON alerts;
CREATE INDEX idx_alerts_project ON alerts(project_id);
CREATE INDEX idx_alerts_project_active ON alerts(project_id, is_active);

ALTER TABLE alerts 
DROP FOREIGN KEY fk_alerts_tenant;
ALTER TABLE alerts 
DROP COLUMN tenant_id;

-- ========================================
-- STEP 4: MIGRATE pulse_sdk_configs TABLE
-- ========================================

ALTER TABLE pulse_sdk_configs 
ADD COLUMN project_id VARCHAR(64);

UPDATE pulse_sdk_configs c
INNER JOIN projects p ON c.tenant_id = p.tenant_id
SET c.project_id = p.project_id
WHERE p.name LIKE 'Default Project%';

ALTER TABLE pulse_sdk_configs 
MODIFY project_id VARCHAR(64) NOT NULL;

ALTER TABLE pulse_sdk_configs 
ADD CONSTRAINT fk_sdk_configs_project 
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_sdk_configs_tenant ON pulse_sdk_configs;
DROP INDEX IF EXISTS idx_sdk_configs_tenant_active ON pulse_sdk_configs;
CREATE INDEX idx_sdk_configs_project ON pulse_sdk_configs(project_id);
CREATE INDEX idx_sdk_configs_project_active ON pulse_sdk_configs(project_id, is_active);

ALTER TABLE pulse_sdk_configs 
DROP FOREIGN KEY fk_sdk_configs_tenant;
ALTER TABLE pulse_sdk_configs 
DROP COLUMN tenant_id;

-- ========================================
-- STEP 5: MIGRATE notification_channels TABLE
-- ========================================

ALTER TABLE notification_channels 
ADD COLUMN project_id VARCHAR(64);

UPDATE notification_channels n
INNER JOIN projects p ON n.tenant_id = p.tenant_id
SET n.project_id = p.project_id
WHERE p.name LIKE 'Default Project%';

ALTER TABLE notification_channels 
MODIFY project_id VARCHAR(64) NOT NULL;

ALTER TABLE notification_channels 
ADD CONSTRAINT fk_notification_channels_project 
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE;

DROP INDEX IF EXISTS idx_notification_channels_tenant ON notification_channels;
CREATE INDEX idx_notification_channels_project ON notification_channels(project_id);

ALTER TABLE notification_channels 
DROP FOREIGN KEY fk_notification_channels_tenant;
ALTER TABLE notification_channels 
DROP COLUMN tenant_id;

-- ========================================
-- STEP 6: MIGRATE athena_job TABLE
-- ========================================
-- Note: Keep both tenant_id and project_id for organizational hierarchy

ALTER TABLE athena_job 
ADD COLUMN project_id VARCHAR(64);

UPDATE athena_job a
INNER JOIN projects p ON a.tenant_id = p.tenant_id
SET a.project_id = p.project_id
WHERE p.name LIKE 'Default Project%';

ALTER TABLE athena_job 
MODIFY project_id VARCHAR(64) NOT NULL;

ALTER TABLE athena_job 
ADD CONSTRAINT fk_athena_job_project 
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE;

-- Keep tenant_id for organizational hierarchy queries
CREATE INDEX idx_athena_job_project ON athena_job(project_id);
CREATE INDEX idx_athena_job_tenant_project ON athena_job(tenant_id, project_id);

-- ========================================
-- STEP 7: MIGRATE symbol_files TABLE (Special handling for composite PK)
-- ========================================

-- Create temporary table with new schema
CREATE TABLE symbol_files_new LIKE symbol_files;

-- Remove old primary key
ALTER TABLE symbol_files_new DROP PRIMARY KEY;

-- Add project_id column
ALTER TABLE symbol_files_new 
ADD COLUMN project_id VARCHAR(64) NOT NULL AFTER tenant_id;

-- Create new primary key without tenant_id
ALTER TABLE symbol_files_new 
ADD PRIMARY KEY (project_id, app_version, app_version_code, platform, framework);

-- Migrate data
INSERT INTO symbol_files_new 
    (project_id, app_version, app_version_code, platform, framework, file_content, bundleid)
SELECT 
    p.project_id,
    s.app_version,
    s.app_version_code,
    s.platform,
    s.framework,
    s.file_content,
    s.bundleid
FROM symbol_files s
INNER JOIN projects p ON s.tenant_id = p.tenant_id
WHERE p.name LIKE 'Default Project%';

-- Add foreign key
ALTER TABLE symbol_files_new 
ADD CONSTRAINT fk_symbol_files_project 
    FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE;

-- Swap tables
DROP TABLE symbol_files;
RENAME TABLE symbol_files_new TO symbol_files;

-- ========================================
-- STEP 8: DROP clickhouse_tenant_credentials (replaced by clickhouse_project_credentials)
-- ========================================

DROP TABLE IF EXISTS clickhouse_tenant_credentials;

-- ========================================
-- VERIFICATION QUERIES
-- ========================================

-- Verify all tables have project_id
SELECT 'interaction' as table_name, COUNT(*) as row_count FROM interaction
UNION ALL
SELECT 'alerts', COUNT(*) FROM alerts
UNION ALL
SELECT 'pulse_sdk_configs', COUNT(*) FROM pulse_sdk_configs
UNION ALL
SELECT 'notification_channels', COUNT(*) FROM notification_channels
UNION ALL
SELECT 'athena_job', COUNT(*) FROM athena_job
UNION ALL
SELECT 'symbol_files', COUNT(*) FROM symbol_files;

-- Verify no orphaned data
SELECT 
    'Projects created' as status, 
    COUNT(*) as count 
FROM projects;

SELECT 
    'Default projects' as status, 
    COUNT(*) as count 
FROM projects 
WHERE name LIKE 'Default Project%';

-- Migration complete!
SELECT 'Migration V5 completed successfully!' as status;
