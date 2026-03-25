-- ============================================================================
-- Migration V9: Make SDK Config Version Project-Specific
-- 
-- Problem: Currently, `version` is a global AUTO_INCREMENT field across all
-- projects, causing new projects to start at V2, V3, etc. instead of V1.
-- 
-- Solution: 
-- 1. Remove AUTO_INCREMENT from version column
-- 2. Change PRIMARY KEY to composite (project_id, version)
-- 3. Recalculate version numbers to be project-specific (1, 2, 3 per project)
-- ============================================================================

-- Step 1: Create a temporary table with the new schema
CREATE TABLE pulse_sdk_configs_new (
    version INT UNSIGNED NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    config_json JSON NOT NULL,
    
    -- Composite primary key: version is now project-specific
    PRIMARY KEY (project_id, version),
    
    INDEX idx_sdk_configs_project (project_id),
    INDEX idx_sdk_configs_project_active (project_id, is_active),
    CONSTRAINT fk_sdk_configs_project_new FOREIGN KEY (project_id) 
        REFERENCES projects(project_id) ON DELETE CASCADE
);

-- Step 2: Migrate existing data with recalculated project-specific versions
-- Use ROW_NUMBER() to assign sequential version numbers per project
INSERT INTO pulse_sdk_configs_new 
    (version, project_id, description, is_active, created_at, created_by, config_json)
SELECT 
    ROW_NUMBER() OVER (PARTITION BY project_id ORDER BY version ASC) as new_version,
    project_id,
    description,
    is_active,
    created_at,
    created_by,
    config_json
FROM pulse_sdk_configs
ORDER BY project_id, version;

-- Step 3: Drop the old table
DROP TABLE pulse_sdk_configs;

-- Step 4: Rename the new table to the original name
RENAME TABLE pulse_sdk_configs_new TO pulse_sdk_configs;

-- Verification query (commented out, but can be run manually to verify)
-- SELECT project_id, version, description, created_at 
-- FROM pulse_sdk_configs 
-- ORDER BY project_id, version;
