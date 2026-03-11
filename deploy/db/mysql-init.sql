-- Pulse Observability - MySQL Database Initialization
-- This script creates the necessary database schema for Pulse Server
-- Includes multi-tenant support with tenant_id columns

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS pulse_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Create OpenFGA database for authorization service
-- OpenFGA will automatically create its tables on startup
CREATE DATABASE IF NOT EXISTS openfga CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant permissions to pulse_user for OpenFGA database
GRANT ALL PRIVILEGES ON openfga.* TO 'pulse_user'@'%';
FLUSH PRIVILEGES;

USE pulse_db;

-- ============================================================================
-- TIERS TABLE
-- Defines available tiers and their default usage limits
-- ============================================================================
CREATE TABLE IF NOT EXISTS tiers (
    tier_id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    is_custom_limits_allowed BOOLEAN NOT NULL DEFAULT FALSE,
    usage_limit_defaults JSON NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default tiers
INSERT INTO tiers (name, display_name, is_custom_limits_allowed, usage_limit_defaults) VALUES
('free', 'Free', FALSE, '{
  "max_user_sessions_per_project": {
    "display_name": "Max User Sessions per Project",
    "window_type": "monthly",
    "data_type": "INTEGER",
    "value": 10000,
    "overage": 0
  },
  "max_events_per_project": {
    "display_name": "Max Events per Project",
    "window_type": "monthly",
    "data_type": "INTEGER",
    "value": 100000,
    "overage": 0
  }
}'),
('enterprise', 'Enterprise', TRUE, '{
  "max_user_sessions_per_project": {
    "display_name": "Max User Sessions per Project",
    "window_type": "monthly",
    "data_type": "INTEGER",
    "value": 10000,
    "overage": 10
  },
  "max_events_per_project": {
    "display_name": "Max Events per Project",
    "window_type": "monthly",
    "data_type": "INTEGER",
    "value": 100000,
    "overage": 10
  }
}')
ON DUPLICATE KEY UPDATE name = name;

-- ============================================================================
-- TENANTS TABLE
-- Create tenants table (referenced by other tables)
-- ============================================================================
CREATE TABLE IF NOT EXISTS tenants (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    tier_id INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    gcp_tenant_id VARCHAR(32) NULL,
    domain_name VARCHAR(32) NULL,
    INDEX idx_tenant_active (is_active),
    INDEX idx_gcp_tenant_id (gcp_tenant_id),
    CONSTRAINT fk_tenant_tier FOREIGN KEY (tier_id) REFERENCES tiers(tier_id)
);

-- Insert a default tenant for existing data
INSERT INTO tenants (tenant_id, name, description, is_active, gcp_tenant_id, domain_name)
VALUES ('default', 'Default Tenant', 'Default tenant for existing data', TRUE, 'dummy-f3w8r', 'localhost')
ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
-- Users and Projects tables (must be created BEFORE tables that reference them)
-- ============================================================

-- Users table for authentication and user management
CREATE TABLE IF NOT EXISTS users (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id           VARCHAR(255) NOT NULL UNIQUE COMMENT 'Unique user identifier (user-{uuid})',
    email             VARCHAR(255) NOT NULL UNIQUE COMMENT 'User email from Google OAuth',
    name              VARCHAR(255) NOT NULL COMMENT 'User display name',
    status            ENUM('pending', 'active', 'suspended') NOT NULL DEFAULT 'pending' COMMENT 'pending=added by admin, active=logged in',
    firebase_uid      VARCHAR(255) NULL UNIQUE COMMENT 'Firebase user ID for authentication',
    last_login_at     TIMESTAMP NULL COMMENT 'Track user activity',
    is_active         BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'User account status',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_user_email (email),
    INDEX idx_user_id (user_id),
    INDEX idx_user_active (is_active),
    INDEX idx_user_status (status),
    INDEX idx_user_firebase_uid (firebase_uid)
);

-- Projects table (hierarchy: tenant -> projects)
CREATE TABLE IF NOT EXISTS projects (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Project identifier (proj-{uuid})',
    tenant_id VARCHAR(64) NOT NULL COMMENT 'Parent tenant ID',
    name VARCHAR(255) NOT NULL COMMENT 'Project name',
    description TEXT COMMENT 'Project description',
    slug VARCHAR(100) COMMENT 'Project slug for URL-friendly identifier',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Project status',
    created_by VARCHAR(255) NOT NULL COMMENT 'User who created the project',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT fk_project_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id) ON DELETE CASCADE,
    INDEX idx_project_tenant (tenant_id, is_active)
);

-- Insert sample projects
INSERT INTO projects (project_id, tenant_id, name, description, slug, is_active, created_by) VALUES
-- Default tenant projects
('default-project', 'default', 'Default Project', 'Default project for existing data', 'default', TRUE, 'system'),
('pulse-mobile-android', 'default', 'Pulse Mobile Android', 'Android mobile application for Pulse', 'pulse-android', TRUE, 'system'),
('pulse-mobile-ios', 'default', 'Pulse Mobile iOS', 'iOS mobile application for Pulse', 'pulse-ios', TRUE, 'system'),
('pulse-web-dashboard', 'default', 'Pulse Web Dashboard', 'Web dashboard for monitoring and analytics', 'pulse-web', TRUE, 'system')
ON DUPLICATE KEY UPDATE name = name;

-- Insert Fancode tenant (example multi-tenant setup)
INSERT INTO tenants (tenant_id, name, description, is_active, gcp_tenant_id, domain_name)
VALUES ('fancode', 'Fancode', 'Fancode sports streaming platform', TRUE, 'Fancode-1rsts', 'fancode.com')
ON DUPLICATE KEY UPDATE name = name;

-- Insert Fancode projects
INSERT INTO projects (project_id, tenant_id, name, description, slug, is_active, created_by) VALUES
('fancode-mobile-android', 'fancode', 'Fancode Android', 'Fancode Android mobile app', 'android', TRUE, 'system'),
('fancode-mobile-ios', 'fancode', 'Fancode iOS', 'Fancode iOS mobile app', 'ios', TRUE, 'system'),
('fancode-mobile-rn', 'fancode', 'Fancode React Native', 'Fancode React Native shared codebase', 'react-native', TRUE, 'system'),
('fancode-web', 'fancode', 'Fancode Web', 'Fancode web application', 'web', TRUE, 'system'),
('fancode-tv', 'fancode', 'Fancode TV', 'Fancode TV application (Android TV, Fire TV)', 'tv', TRUE, 'system')
ON DUPLICATE KEY UPDATE name = name;

-- Insert Dream11 tenant (another example)
INSERT INTO tenants (tenant_id, name, description, is_active, gcp_tenant_id, domain_name)
VALUES ('dream11', 'Dream11', 'Dream11 fantasy sports platform', TRUE, 'Dream11-abcde', 'dream11.com')
ON DUPLICATE KEY UPDATE name = name;

-- Insert Dream11 projects
INSERT INTO projects (project_id, tenant_id, name, description, slug, is_active, created_by) VALUES
('dream11-android', 'dream11', 'Dream11 Android', 'Dream11 Android application', 'android', TRUE, 'system'),
('dream11-ios', 'dream11', 'Dream11 iOS', 'Dream11 iOS application', 'ios', TRUE, 'system'),
('dream11-web', 'dream11', 'Dream11 Web', 'Dream11 web platform', 'web', TRUE, 'system'),
('dream11-pwa', 'dream11', 'Dream11 PWA', 'Dream11 Progressive Web App', 'pwa', TRUE, 'system')
ON DUPLICATE KEY UPDATE name = name;

-- ============================================================
-- Tables that reference projects
-- ============================================================

CREATE TABLE interaction (
    interaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(25) NOT NULL,
    details JSON,
    is_archived TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(255),
    INDEX idx_interaction_project (project_id),
    INDEX idx_interaction_project_archived (project_id, is_archived),
    CONSTRAINT fk_interaction_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
);

-- Symbol files table with project_id in composite primary key
CREATE TABLE symbol_files (
    project_id VARCHAR(64) NOT NULL DEFAULT 'default',
    app_version VARCHAR(64) NOT NULL,
    app_version_code INT NOT NULL,
    platform ENUM('ios','android') NOT NULL,
    framework ENUM('java','js', 'mapping', 'dsym') NOT NULL,
    file_content LONGBLOB NOT NULL,
    bundleid VARCHAR(255),
    PRIMARY KEY (project_id, app_version, app_version_code, platform, framework),
    CONSTRAINT fk_symbol_files_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
);

CREATE TABLE pulse_sdk_configs (
    version INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    config_json JSON NOT NULL,
    INDEX idx_sdk_configs_project (project_id),
    INDEX idx_sdk_configs_project_active (project_id, is_active),
    CONSTRAINT fk_sdk_configs_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
);

-- ============================================================
-- DEFAULT SDK CONFIGURATION TEMPLATE (for reference)
-- This configuration is automatically created for each new project
-- via ProjectService.createProject() in Java code
-- ============================================================
-- {
--   "sampling": {
--     "default": {
--       "sessionSampleRate": 1
--     },
--     "rules": [],
--     "criticalEventPolicies": {
--       "alwaysSend": []
--     },
--     "criticalSessionPolicies": {
--       "alwaysSend": []
--     }
--   },
--   "signals": {
--     "filters": {
--       "mode": "blacklist",
--       "values": []
--     },
--     "scheduleDurationMs": 5000,
--     "logsCollectorUrl": "http://10.0.2.2:4318/v1/logs",
--     "metricCollectorUrl": "http://10.0.2.2:4318/v1/metrics",
--     "spanCollectorUrl": "http://10.0.2.2:4318/v1/traces",
--     "attributesToDrop": [],
--     "attributesToAdd": []
--   },
--   "interaction": {
--     "collectorUrl": "http://10.0.2.2:4318/v1/traces/v1/interactions",
--     "configUrl": "http://10.0.2.2:8080/v1/interaction-configs/",
--     "beforeInitQueueSize": 100
--   },
--   "features": [
--     {
--       "featureName": "interaction",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "java_crash",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "js_crash",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "java_anr",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "network_change",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "network_instrumentation",
--       "sessionSampleRate": 0,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "screen_session",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "custom_events",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "rn_navigation",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "rn_screen_load",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     },
--     {
--       "featureName": "rn_screen_interactive",
--       "sessionSampleRate": 1,
--       "sdks": ["pulse_android_java", "pulse_android_rn", "pulse_ios_swift", "pulse_ios_rn"]
--     }
--   ]
-- }

CREATE TABLE severity
(
    severity_id INT PRIMARY KEY AUTO_INCREMENT,
    name        INT NOT NULL,
    description TEXT
);


CREATE TABLE alerts (
    id INT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL,
    name TEXT NOT NULL,
    description TEXT NOT NULL,
    scope VARCHAR(100) NOT NULL,
    dimension_filter TEXT,
    condition_expression VARCHAR(255) NOT NULL,
    severity_id INT NOT NULL,
    notification_channel_id INT NOT NULL,
    evaluation_period INT NOT NULL,
    evaluation_interval INT NOT NULL,
    last_snoozed_at TIMESTAMP NULL DEFAULT NULL,
    snoozed_from TIMESTAMP NULL DEFAULT NULL,
    snoozed_until TIMESTAMP NULL DEFAULT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,

    INDEX idx_alerts_project (project_id),
    INDEX idx_alerts_project_active (project_id, is_active),

    CONSTRAINT fk_alerts_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_severity FOREIGN KEY (severity_id) REFERENCES severity(severity_id),
    CONSTRAINT fk_alert_notification_channel FOREIGN KEY (notification_channel_id) REFERENCES notification_channels(notification_channel_id)
);

CREATE TABLE alert_scope (
    id INT PRIMARY KEY AUTO_INCREMENT,
    alert_id INT NOT NULL,
    name VARCHAR(255) NOT NULL,
    conditions JSON NULL,
    state VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_subject_alert FOREIGN KEY (alert_id) REFERENCES alerts (id)
);

CREATE TABLE alert_evaluation_history (
    evaluation_id INT PRIMARY KEY AUTO_INCREMENT,
    scope_id INT NOT NULL,
    evaluation_result JSON NOT NULL,
    state VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_eval_subject FOREIGN KEY (scope_id) REFERENCES alert_scope (id)
);

CREATE TABLE scope_types (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE,
    label VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE alert_metrics (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    label VARCHAR(500) NOT NULL,
    scope VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_metric_scope (name, scope)
);

INSERT INTO severity (name, description)
VALUES
    (1, 'Critical: Production outage or severe degradation with significant user impact. Requires immediate action and incident management.'),
    (2, 'Warning: Degraded performance, elevated errors, or risk of user impact. Should be investigated soon but is not a full outage.'),
    (3, 'Info: Informational or low-risk condition. No immediate action required; useful for visibility, trend analysis, or validation of changes.');

-- Insert Scope Types
INSERT INTO scope_types (name, label) VALUES
    ('interaction', 'Interactions'),
    ('network_api', 'Network APIs'),
    ('screen', 'Screen'),
    ('app_vitals', 'App Vitals');

-- Insert interaction scope metrics
INSERT INTO alert_metrics (name, label, scope) VALUES
    ('APDEX', 'Apdex Score (0 to 1)', 'interaction'),
    ('CRASH', 'Crash Count', 'interaction'),
    ('ANR', 'ANR Count', 'interaction'),
    ('FROZEN_FRAME', 'Frozen Frame Count', 'interaction'),
    ('ANALYSED_FRAME', 'Analysed Frame Count', 'interaction'),
    ('UNANALYSED_FRAME', 'Unanalysed Frame Count', 'interaction'),
    ('DURATION_P99', 'Duration P99 (ms)', 'interaction'),
    ('DURATION_P95', 'Duration P95 (ms)', 'interaction'),
    ('DURATION_P50', 'Duration P50 (ms)', 'interaction'),
    ('ERROR_RATE', 'Error Rate (%)', 'interaction'),
    ('INTERACTION_SUCCESS_COUNT', 'Success Count', 'interaction'),
    ('INTERACTION_ERROR_COUNT', 'Error Count', 'interaction'),
    ('INTERACTION_ERROR_DISTINCT_USERS', 'Distinct Users with Errors', 'interaction'),
    ('USER_CATEGORY_EXCELLENT', 'Excellent Users Count', 'interaction'),
    ('USER_CATEGORY_GOOD', 'Good Users Count', 'interaction'),
    ('USER_CATEGORY_AVERAGE', 'Average Users Count', 'interaction'),
    ('USER_CATEGORY_POOR', 'Poor Users Count', 'interaction'),
    ('CRASH_RATE', 'Crash Rate (%)', 'interaction'),
    ('ANR_RATE', 'ANR Rate (%)', 'interaction'),
    ('FROZEN_FRAME_RATE', 'Frozen Frame Rate (%)', 'interaction'),
    ('POOR_USER_RATE', 'Poor Users (%)', 'interaction'),
    ('AVERAGE_USER_RATE', 'Average Users (%)', 'interaction'),
    ('GOOD_USER_RATE', 'Good Users Rate (%)', 'interaction'),
    ('EXCELLENT_USER_RATE', 'Excellent Users (%)', 'interaction');

-- Insert APP_VITALS scope metrics
INSERT INTO alert_metrics (name, label, scope) VALUES
    ('CRASH_FREE_USERS_PERCENTAGE', 'Crash-Free Users %', 'app_vitals'),
    ('CRASH_FREE_SESSIONS_PERCENTAGE', 'Crash-Free Sessions %', 'app_vitals'),
    ('CRASH_USERS', 'Crash Users Count', 'app_vitals'),
    ('CRASH_SESSIONS', 'Crash Sessions Count', 'app_vitals'),
    ('ALL_USERS', 'Total Users Count', 'app_vitals'),
    ('ALL_SESSIONS', 'Total Sessions Count', 'app_vitals'),
    ('ANR_FREE_USERS_PERCENTAGE', 'ANR-Free Users %', 'app_vitals'),
    ('ANR_FREE_SESSIONS_PERCENTAGE', 'ANR-Free Sessions %', 'app_vitals'),
    ('ANR_USERS', 'ANR Users Count', 'app_vitals'),
    ('ANR_SESSIONS', 'ANR Sessions Count', 'app_vitals'),
    ('NON_FATAL_FREE_USERS_PERCENTAGE', 'Non-Fatal Free Users %', 'app_vitals'),
    ('NON_FATAL_FREE_SESSIONS_PERCENTAGE', 'Non-Fatal Free Sessions %', 'app_vitals'),
    ('NON_FATAL_USERS', 'Non-Fatal Users Count', 'app_vitals'),
    ('NON_FATAL_SESSIONS', 'Non-Fatal Sessions Count', 'app_vitals');

-- Insert Screen scope metrics
INSERT INTO alert_metrics (name, label, scope) VALUES
    ('SCREEN_DAILY_USERS', 'Daily Users Count', 'screen'),
    ('ERROR_RATE', 'Error Rate (%)', 'screen'),
    ('SCREEN_TIME', 'Screen Time (s)', 'screen'),
    ('LOAD_TIME', 'Load Time (ms)', 'screen'),
    ('CRASH_FREE_USERS_PERCENTAGE', 'Crash Free Users Percentage (%)', 'screen'),
    ('CRASH_FREE_SESSIONS_PERCENTAGE', 'Crash Free Sessions Percentage (%)', 'screen'),
    ('ANR_FREE_USERS_PERCENTAGE', 'ANR Free Users Percentage (%)', 'screen'),
    ('ANR_FREE_SESSIONS_PERCENTAGE', 'ANR Free Sessions Percentage (%)', 'screen'),
    ('NON_FATAL_FREE_USERS_PERCENTAGE', 'Non-Fatal Free Users Percentage (%)', 'screen'),
    ('NON_FATAL_FREE_SESSIONS_PERCENTAGE', 'Non-Fatal Free Sessions Percentage (%)', 'screen');

-- Insert network_api scope metrics
INSERT INTO alert_metrics (name, label, scope) VALUES
    ('NET_0', 'Connection Error Count', 'network_api'),
    ('NET_2XX', '2XX Success Count', 'network_api'),
    ('NET_3XX', '3XX Redirect Count', 'network_api'),
    ('NET_4XX', '4XX Client Error Count', 'network_api'),
    ('NET_5XX', '5XX Server Error Count', 'network_api'),
    ('NET_4XX_RATE', '4XX Error Rate (%)', 'network_api'),
    ('NET_5XX_RATE', '5XX Error Rate (%)', 'network_api'),
    ('DURATION_P99', 'Duration P99 (ms)', 'network_api'),
    ('DURATION_P95', 'Duration P95 (ms)', 'network_api'),
    ('DURATION_P50', 'Duration P50 (ms)', 'network_api'),
    ('ERROR_RATE', 'Error Rate (%)', 'network_api'),
    ('NET_COUNT', 'Total Network Requests', 'network_api');

-- Grant privileges (adjust as needed for your environment)
-- GRANT ALL PRIVILEGES ON pulse_db.* TO 'pulse_user'@'%' IDENTIFIED BY 'pulse_password';
-- FLUSH PRIVILEGES;

-- ============================================================
-- NEW TABLES FOR MULTI-TENANCY & RBAC (February 2026)
-- ============================================================

-- NOTE: project_api_keys table is defined later in the file (after project_usage_limits)
-- NOTE: clickhouse_project_credentials table is defined later in the file

-- Athena job tracking table (depends on projects table)
CREATE TABLE IF NOT EXISTS athena_job (
    job_id VARCHAR(255) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT 'Parent tenant for organizational hierarchy',
    project_id VARCHAR(64) COMMENT 'Project where query was executed (data isolation)',
    query_string TEXT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    query_execution_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    result_location VARCHAR(500),
    error_message TEXT,
    data_scanned_in_bytes BIGINT NULL,
    execution_time_millis BIGINT NULL,
    engine_execution_time_millis BIGINT NULL,
    query_queue_time_millis BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_status (status),
    INDEX idx_query_execution_id (query_execution_id),
    INDEX idx_created_at (created_at),
    INDEX idx_user_email (user_email),
    INDEX idx_user_email_created_at (user_email, created_at),
    INDEX idx_athena_job_tenant (tenant_id),
    INDEX idx_athena_job_project (project_id),
    INDEX idx_athena_job_tenant_project (tenant_id, project_id),
    CONSTRAINT fk_athena_job_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id),
    CONSTRAINT fk_athena_job_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE
);

-- ============================================================================
-- PROJECT USAGE LIMITS TABLE
-- Single source of truth for project limits. One active record per project.
-- ============================================================================
CREATE TABLE IF NOT EXISTS project_usage_limits (
    project_usage_limit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL,
    usage_limits JSON NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    disabled_at TIMESTAMP NULL,
    disabled_by VARCHAR(255),
    disabled_reason VARCHAR(255) NULL,
    created_by VARCHAR(255) NOT NULL,
    CONSTRAINT fk_pul_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_pul_active (project_id, is_active)
);

-- ============================================================================
-- PROJECT API KEYS TABLE
-- Stores API keys for project authentication at API Gateway
-- ============================================================================
CREATE TABLE IF NOT EXISTS project_api_keys (
    project_api_key_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(64) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    api_key_encrypted TEXT NOT NULL,
    encryption_salt VARCHAR(100) NOT NULL,
    api_key_digest VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at TIMESTAMP NULL COMMENT 'NULL means never expires',
    grace_period_ends_at TIMESTAMP NULL,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at TIMESTAMP NULL,
    deactivated_by VARCHAR(255) NULL,
    deactivation_reason VARCHAR(255) NULL,
    CONSTRAINT fk_pak_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_pak_project_active (project_id, is_active),
    INDEX idx_pak_digest (api_key_digest),
    INDEX idx_pak_grace_period (grace_period_ends_at),
    INDEX idx_pak_expires (expires_at)
);

-- ============================================================================
-- CLICKHOUSE PROJECT CREDENTIALS TABLE
-- Stores ClickHouse user credentials for each project
-- ============================================================================
CREATE TABLE IF NOT EXISTS clickhouse_project_credentials (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL UNIQUE COMMENT 'Project ID (projectName-{uuid})',
    clickhouse_username VARCHAR(255) NOT NULL UNIQUE,
    clickhouse_password_encrypted TEXT NOT NULL,
    encryption_salt VARCHAR(100) NOT NULL,
    password_digest VARCHAR(100) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_chcred_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_ch_project_active (project_id, is_active)
);

-- ============================================================================
-- CLICKHOUSE PROJECT CREDENTIAL AUDIT TABLE
-- Audits changes to ClickHouse project credentials
-- ============================================================================
CREATE TABLE IF NOT EXISTS clickhouse_project_credential_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL COMMENT 'Project ID (projectName-{uuid})',
    action VARCHAR(50) NOT NULL,
    performed_by VARCHAR(255) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_chaudit_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_chaudit_project (project_id),
    INDEX idx_chaudit_created (created_at)
);

-- ============================================================================
-- TERMS & CONDITIONS VERSIONING
-- Stores published TnC document versions with S3 URLs
-- ============================================================================
CREATE TABLE IF NOT EXISTS tnc_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    version VARCHAR(20) NOT NULL UNIQUE,
    tos_s3_url VARCHAR(1024) NOT NULL,
    aup_s3_url VARCHAR(1024) NOT NULL,
    privacy_policy_s3_url VARCHAR(1024) NOT NULL,
    summary TEXT,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tnc_active (is_active)
);

-- ============================================================================
-- TERMS & CONDITIONS ACCEPTANCE TRACKING
-- Tracks per-tenant acceptance of TnC versions (org-level, not project-level)
-- ============================================================================
CREATE TABLE IF NOT EXISTS tnc_acceptances (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id VARCHAR(64) NOT NULL,
    tnc_version_id BIGINT NOT NULL,
    accepted_by_email VARCHAR(255) NOT NULL,
    accepted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    UNIQUE KEY uk_tenant_version (tenant_id, tnc_version_id),
    INDEX idx_tnc_tenant (tenant_id),
    CONSTRAINT fk_tnc_acc_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id),
    CONSTRAINT fk_tnc_acc_version FOREIGN KEY (tnc_version_id) REFERENCES tnc_versions(id)

);
-- Notification Service Tables
CREATE TABLE IF NOT EXISTS notification_channels (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NULL,
    channel_type ENUM('SLACK', 'SLACK_WEBHOOK', 'EMAIL', 'TEAMS') NOT NULL,
    name VARCHAR(255) NOT NULL,
    config JSON NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_channel_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    UNIQUE KEY unique_project_channel_type (project_id, channel_type),
    INDEX idx_channel_project_type_active (project_id, channel_type, is_active)
);

-- Insert default platform email channel for system notifications (onboarding, etc.)
INSERT INTO notification_channels (project_id, channel_type, name, config) VALUES
('default-project', 'EMAIL', 'Platform Email Channel', JSON_OBJECT(
    'type', 'EMAIL',
    'fromAddress', 'noreply@pulse-ux.com',
    'fromName', 'Pulse Platform'
))
ON DUPLICATE KEY UPDATE config = config;


CREATE TABLE IF NOT EXISTS notification_templates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_name VARCHAR(255) NOT NULL,
    channel_type ENUM('SLACK', 'SLACK_WEBHOOK', 'EMAIL', 'TEAMS') NULL,
    version INT NOT NULL DEFAULT 1,
    body JSON NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_template_version (event_name, channel_type),
    INDEX idx_template_event (event_name, is_active)
);

-- Insert project creation onboarding email template
INSERT INTO notification_templates (event_name, channel_type, version, body) VALUES
('project_created', 'EMAIL', 1, JSON_OBJECT(
    'type', 'EMAIL',
    'subject', '[Pulse] Welcome - {{projectName}} is ready!',
    'html', '<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family: -apple-system, BlinkMacSystemFont, \'Segoe UI\', Roboto, Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f5f5f5;\"><div style=\"background: #1a1a2e; padding: 30px; border-radius: 10px 10px 0 0; text-align: center;\"><h1 style=\"color: #00BFA5; margin: 0; font-size: 28px;\">Welcome to Pulse!</h1></div><div style=\"background: #ffffff; padding: 30px; border-radius: 0 0 10px 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);\"><p style=\"font-size: 16px;\">Hi <strong>{{createdBy}}</strong>,</p><p style=\"font-size: 16px;\">Great news! Your project <strong style=\"color: #00BFA5;\">{{projectName}}</strong> has been successfully created.</p><p style=\"font-size: 16px; margin-top: 25px;\"><strong>Your API Key</strong></p><p style=\"font-size: 14px; color: #666;\">Use this key to integrate the Pulse SDK into your application:</p><div style=\"background: #1a1a2e; color: #00BFA5; padding: 15px 20px; border-radius: 8px; font-family: \'Courier New\', monospace; font-size: 14px; word-break: break-all; margin: 15px 0; border-left: 4px solid #00BFA5;\">{{apiKey}}</div><p style=\"color: #888; font-size: 12px; margin-top: 5px;\">Keep this key secure. Do not share it publicly or commit it to version control.</p><div style=\"text-align: center; margin: 30px 0;\"><a href=\"https://pulse-ux.com\" style=\"display: inline-block; background: #00BFA5; color: #ffffff; padding: 14px 32px; text-decoration: none; border-radius: 6px; font-weight: 600; font-size: 16px;\">Go to Dashboard</a></div><hr style=\"border: none; border-top: 1px solid #eee; margin: 25px 0;\"><p style=\"color: #888; font-size: 13px; text-align: center;\">Need help integrating? Check out our <a href=\"https://docs.pulse-ux.com\" style=\"color: #00BFA5; text-decoration: none;\">SDK documentation</a>.</p><p style=\"color: #aaa; font-size: 12px; text-align: center; margin-top: 20px;\">-- The Pulse Team</p></div></body></html>',
    'text', '[Pulse] Welcome - {{projectName}} is ready!\n\nHi {{createdBy}},\n\nGreat news! Your project {{projectName}} has been successfully created.\n\nYour API Key:\n{{apiKey}}\n\nUse this key to integrate the Pulse SDK into your application.\n\nKeep this key secure. Do not share it publicly or commit it to version control.\n\nGo to Dashboard: https://pulse-ux.com\n\nNeed help integrating? Visit https://docs.pulse-ux.com\n\n-- The Pulse Team'
))
ON DUPLICATE KEY UPDATE body = body;

-- Insert collaborator added email template
INSERT INTO notification_templates (event_name, channel_type, version, body) VALUES
('collaborator_added', 'EMAIL', 1, JSON_OBJECT(
    'type', 'EMAIL',
    'subject', '[Pulse] You''ve been added to {{projectName}}',
    'html', '<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family: -apple-system, BlinkMacSystemFont, \'Segoe UI\', Roboto, Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f5f5f5;\"><div style=\"background: #1a1a2e; padding: 30px; border-radius: 10px 10px 0 0; text-align: center;\"><h1 style=\"color: #00BFA5; margin: 0; font-size: 28px;\">You have been added to a new project in Pulse!</h1></div><div style=\"background: #ffffff; padding: 30px; border-radius: 0 0 10px 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);\"><p style=\"font-size: 16px;\">Hi,</p><p style=\"font-size: 16px;\"><strong style=\"color: #00BFA5;\">{{addedBy}}</strong> has added you to <strong style=\"color: #00BFA5;\">{{projectName}}</strong> project with <strong>{{role}}</strong> access.</p><div style=\"text-align: center; margin: 30px 0;\"><a href=\"https://pulse-ux.com/projects/{{projectId}}\" style=\"display: inline-block; background: #00BFA5; color: #ffffff; padding: 14px 32px; text-decoration: none; border-radius: 6px; font-weight: 600; font-size: 16px;\">Go to Dashboard</a></div><hr style=\"border: none; border-top: 1px solid #eee; margin: 25px 0;\"><p style=\"color: #888; font-size: 13px; text-align: center;\">Need help getting started? Check out our <a href=\"https://docs.pulse-ux.com\" style=\"color: #00BFA5; text-decoration: none;\">documentation</a>.</p><p style=\"color: #aaa; font-size: 12px; text-align: center; margin-top: 20px;\">-- The Pulse Team</p></div></body></html>',
    'text', '[Pulse] You''ve been added to {{projectName}}\n\nHi,\n\n{{addedBy}} has added you to {{projectName}} project with {{role}} access.\n\nGo to Dashboard: https://pulse-ux.com/projects/{{projectId}}\n\nNeed help getting started? Visit https://docs.pulse-ux.com\n\n-- The Pulse Team'
))
ON DUPLICATE KEY UPDATE body = body;

-- Insert collaborator removed email template
INSERT INTO notification_templates (event_name, channel_type, version, body) VALUES
('collaborator_removed', 'EMAIL', 1, JSON_OBJECT(
    'type', 'EMAIL',
    'subject', '[Pulse] Your access to {{projectName}} has been revoked',
    'html', '<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family: -apple-system, BlinkMacSystemFont, \'Segoe UI\', Roboto, Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f5f5f5;\"><div style=\"background: #1a1a2e; padding: 30px; border-radius: 10px 10px 0 0; text-align: center;\"><h1 style=\"color: #00BFA5; margin: 0; font-size: 28px;\">Your access for a project in Pulse has been revoked.</h1></div><div style=\"background: #ffffff; padding: 30px; border-radius: 0 0 10px 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);\"><p style=\"font-size: 16px;\">Hi,</p><p style=\"font-size: 16px;\"><strong style=\"color: #00BFA5;\">{{removedBy}}</strong> has revoked your access to <strong style=\"color: #00BFA5;\">{{projectName}}</strong> project.</p><p style=\"font-size: 14px; color: #666; margin-top: 20px;\">If you believe this was a mistake, please contact your project administrator.</p><hr style=\"border: none; border-top: 1px solid #eee; margin: 25px 0;\"><p style=\"color: #aaa; font-size: 12px; text-align: center; margin-top: 20px;\">-- The Pulse Team</p></div></body></html>',
    'text', '[Pulse] Your access to {{projectName}} has been revoked\n\nHi,\n\n{{removedBy}} has revoked your access to {{projectName}} project.\n\nIf you believe this was a mistake, please contact your project administrator.\n\n-- The Pulse Team'
))
ON DUPLICATE KEY UPDATE body = body;

-- Insert collaborator role updated email template
INSERT INTO notification_templates (event_name, channel_type, version, body) VALUES
('collaborator_role_updated', 'EMAIL', 1, JSON_OBJECT(
    'type', 'EMAIL',
    'subject', '[Pulse] Your role in {{projectName}} has been updated',
    'html', '<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body style=\"font-family: -apple-system, BlinkMacSystemFont, \'Segoe UI\', Roboto, Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f5f5f5;\"><div style=\"background: #1a1a2e; padding: 30px; border-radius: 10px 10px 0 0; text-align: center;\"><h1 style=\"color: #00BFA5; margin: 0; font-size: 28px;\">Your access for a project in Pulse has been updated.</h1></div><div style=\"background: #ffffff; padding: 30px; border-radius: 0 0 10px 10px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);\"><p style=\"font-size: 16px;\">Hi,</p><p style=\"font-size: 16px;\"><strong style=\"color: #00BFA5;\">{{updatedBy}}</strong> has updated your access to <strong style=\"color: #00BFA5;\">{{projectName}}</strong> project to <strong>{{newRole}}</strong>.</p><div style=\"text-align: center; margin: 30px 0;\"><a href=\"https://pulse-ux.com\" style=\"display: inline-block; background: #00BFA5; color: #ffffff; padding: 14px 32px; text-decoration: none; border-radius: 6px; font-weight: 600; font-size: 16px;\">Go to Dashboard</a></div><hr style=\"border: none; border-top: 1px solid #eee; margin: 25px 0;\"><p style=\"color: #aaa; font-size: 12px; text-align: center; margin-top: 20px;\">-- The Pulse Team</p></div></body></html>',
    'text', '[Pulse] Your role in {{projectName}} has been updated\n\nHi,\n\n{{updatedBy}} has updated your access to {{projectName}} project to {{newRole}}.\n\nGo to Dashboard: https://pulse-ux.com\n\n-- The Pulse Team'
))
ON DUPLICATE KEY UPDATE body = body;

CREATE TABLE IF NOT EXISTS channel_event_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    channel_id BIGINT NOT NULL,
    event_name VARCHAR(255) NOT NULL,
    recipient VARCHAR(512) NULL,
    recipient_name VARCHAR(255) NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_mapping_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    CONSTRAINT fk_mapping_channel FOREIGN KEY (channel_id) REFERENCES notification_channels(id) ON DELETE CASCADE,
    UNIQUE KEY unique_mapping (channel_id, event_name, recipient_name),
    INDEX idx_mapping_project_event (project_id, event_name, is_active)
);

CREATE TABLE IF NOT EXISTS notification_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    channel_type ENUM('SLACK', 'SLACK_WEBHOOK', 'EMAIL', 'TEAMS') NOT NULL,
    channel_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    recipient VARCHAR(512) NOT NULL,
    subject VARCHAR(500) NULL,
    status ENUM('PENDING', 'QUEUED', 'PROCESSING', 'SENT', 'DELIVERED', 'FAILED', 'RETRYING', 'SKIPPED', 'PERMANENT_FAILURE', 'BOUNCED', 'COMPLAINED') NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP NULL,
    error_message TEXT NULL,
    error_code VARCHAR(100) NULL,
    external_id VARCHAR(255) NULL,
    provider_response TEXT NULL,
    latency_ms INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP NULL,
    CONSTRAINT fk_notification_log_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_log_project_status (project_id, status),
    UNIQUE INDEX idx_log_idempotency (project_id, idempotency_key(128), channel_type, recipient(128)),
    INDEX idx_log_external_id (external_id)
);

CREATE TABLE IF NOT EXISTS email_suppression_list (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id VARCHAR(64) NULL,
    email VARCHAR(320) NOT NULL,
    reason ENUM('BOUNCE', 'COMPLAINT', 'MANUAL') NOT NULL,
    bounce_type VARCHAR(50) NULL,
    source_message_id VARCHAR(255) NULL,
    suppressed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_suppression_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    UNIQUE KEY unique_project_email_suppression (project_id, email),
    INDEX idx_suppression_email (email)
);

-- Display summary
SELECT 'Database initialization completed successfully (with new RBAC tables)!' AS status;
SELECT COUNT(*) AS total_tables FROM information_schema.tables WHERE table_schema = 'pulse_db';
