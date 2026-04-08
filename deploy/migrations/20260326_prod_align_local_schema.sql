-- =============================================================================
-- pulse_db: align PROD schema with LOCAL reference (deploy/local_schema.sql)
-- =============================================================================
-- DATA: Run deploy/migrations/20260326_prod_align_local_data_backfill.sql first
--       on prod (or clone); then post-checks must pass; then run this DDL.
-- =============================================================================
-- Rules for this script:
--   - Does NOT DROP any tables (prod-only tables remain unchanged).
--   - Run during a maintenance window after a full backup / restore drill.
--   - Review and run pre-flight checks (Section 0) in prod-readonly first.
--   - Complete every STEP marked DATA REQUIRED before the ALTER that follows.
-- =============================================================================
-- Server: MySQL 8.0+
-- =============================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -----------------------------------------------------------------------------
-- SECTION 0 — Pre-flight (run manually; expect zero bad rows after fixes)
-- -----------------------------------------------------------------------------

-- Orphan alert_scope.alert_id (must be 0 rows before adding FK):
-- SELECT s.id, s.alert_id FROM alert_scope s
-- LEFT JOIN alerts a ON a.id = s.alert_id WHERE a.id IS NULL;

-- channel_event_mapping / API keys / usage limits / CH creds: project_id must exist:
-- SELECT DISTINCT m.project_id FROM channel_event_mapping m
--   LEFT JOIN projects p ON p.project_id = m.project_id WHERE p.project_id IS NULL;

-- athena_job NULL project_id (must be 0 rows OR fixed before NOT NULL):
-- SELECT job_id FROM athena_job WHERE project_id IS NULL;

-- tenants NULL tenant_id (must be 0 rows OR fixed before NOT NULL):
-- SELECT id FROM tenants WHERE tenant_id IS NULL;

-- symbol_files: JAVA rows (must be remapped before shrinking enum); blob rows need s3_key backfill after ADD COLUMN:
-- SELECT project_id, app_version, app_version_code, platform, framework, LENGTH(file_content)
--   FROM symbol_files WHERE framework = 'java';

-- pulse_sdk_configs: after migration, no duplicate (project_id, version):
-- SELECT project_id, version, COUNT(*) c FROM pulse_sdk_configs GROUP BY project_id, version HAVING c > 1;


-- -----------------------------------------------------------------------------
-- SECTION 1 — Add missing table (local has; prod may not)
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `incidents` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `description` text NOT NULL,
  `severity` enum('P1','P2','P3','P4') NOT NULL,
  `reporter_name` varchar(255) NOT NULL,
  `reporter_email` varchar(255) NOT NULL,
  `org_identifier` varchar(64) NOT NULL,
  `status` enum('OPEN','ACKNOWLEDGED','RECOVERED','CLOSED') NOT NULL DEFAULT 'OPEN',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `acknowledged_at` timestamp NULL DEFAULT NULL,
  `recovered_at` timestamp NULL DEFAULT NULL,
  `closed_at` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_incidents_org` (`org_identifier`),
  KEY `idx_incidents_severity` (`severity`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- -----------------------------------------------------------------------------
-- SECTION 2 — Remove FKs / indexes that exist on PROD but not LOCAL
-- (Safe order: drop redundant foreign keys first so project_id no longer references tenants where local uses projects only)
-- -----------------------------------------------------------------------------

-- alerts: drop duplicate tenant FK + duplicate indexes
ALTER TABLE `alerts`
  DROP FOREIGN KEY `fk_alerts_tenant`;
ALTER TABLE `alerts`
  DROP INDEX `idx_alerts_tenant`,
  DROP INDEX `idx_alerts_tenant_active`;

-- interaction: drop parallel tenant FK + redundant indexes
ALTER TABLE `interaction`
  DROP FOREIGN KEY `fk_interaction_tenant`;
ALTER TABLE `interaction`
  DROP INDEX `idx_interaction_tenant`,
  DROP INDEX `idx_interaction_tenant_archived`;

-- notification_channels_old
ALTER TABLE `notification_channels_old`
  DROP FOREIGN KEY `fk_notification_channels_tenant`;
ALTER TABLE `notification_channels_old`
  DROP INDEX `idx_notification_channels_tenant`;


-- -----------------------------------------------------------------------------
-- SECTION 3 — pulse_sdk_configs: PK + indexes + FK (LOCAL shape)
-- PROD: PK(version) AUTO_INCREMENT. LOCAL: PK(project_id, version), FK to projects.
-- -----------------------------------------------------------------------------

-- Drop redundant duplicate indexes (same columns as _project_* on prod dumps)
ALTER TABLE `pulse_sdk_configs`
  DROP INDEX `idx_sdk_configs_tenant`,
  DROP INDEX `idx_sdk_configs_tenant_active`;

-- Remove AUTO_INCREMENT + old primary key
ALTER TABLE `pulse_sdk_configs`
  MODIFY COLUMN `version` int unsigned NOT NULL;
ALTER TABLE `pulse_sdk_configs`
  DROP PRIMARY KEY;

-- Rename old PK column (globally unique) for reordering; new `version` is per-project
ALTER TABLE `pulse_sdk_configs`
  CHANGE COLUMN `version` `_migration_legacy_pk` int unsigned NOT NULL;

ALTER TABLE `pulse_sdk_configs`
  ADD COLUMN `version` int unsigned NOT NULL DEFAULT 1 AFTER `project_id`;

UPDATE `pulse_sdk_configs` `p`
INNER JOIN (
  SELECT
    `_migration_legacy_pk`,
    ROW_NUMBER() OVER (PARTITION BY `project_id` ORDER BY `_migration_legacy_pk`) AS `rn`
  FROM `pulse_sdk_configs`
) `x` ON `p`.`_migration_legacy_pk` = `x`.`_migration_legacy_pk`
SET `p`.`version` = `x`.`rn`;

ALTER TABLE `pulse_sdk_configs`
  DROP COLUMN `_migration_legacy_pk`;

ALTER TABLE `pulse_sdk_configs`
  ADD PRIMARY KEY (`project_id`, `version`);

-- Match local column order: version, then project_id (see local_schema.sql)
ALTER TABLE `pulse_sdk_configs`
  MODIFY COLUMN `version` int unsigned NOT NULL FIRST;
ALTER TABLE `pulse_sdk_configs`
  MODIFY COLUMN `project_id` varchar(64) NOT NULL AFTER `version`;

ALTER TABLE `pulse_sdk_configs`
  ADD CONSTRAINT `fk_sdk_configs_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`) ON DELETE CASCADE;


-- -----------------------------------------------------------------------------
-- SECTION 4 — symbol_files: S3 pointer + enum + FK (LOCAL shape)
-- DATA REQUIRED:
--   1) Upload each file_content blob to S3 (or your object store) and set s3_key below.
--   2) For framework = 'java' rows, decide target enum ('js' | 'mapping' | 'dsym') and UPDATE.
-- -----------------------------------------------------------------------------

ALTER TABLE `symbol_files`
  DROP FOREIGN KEY `fk_symbol_files_tenant`;

ALTER TABLE `symbol_files`
  ADD COLUMN `s3_key` varchar(512) NULL AFTER `framework`;

-- >>> DATA REQUIRED: backfill s3_key for every row, e.g.:
-- UPDATE symbol_files SET s3_key = CONCAT('s3://your-bucket/prefix/', project_id, '/', app_version, '/', platform, '/', framework)
--   WHERE s3_key IS NULL;

-- >>> DATA REQUIRED: eliminate 'java' before enum shrink, e.g.:
-- UPDATE symbol_files SET framework = 'js' WHERE framework = 'java';

-- After all rows have non-null s3_key and no framework='java':
ALTER TABLE `symbol_files`
  MODIFY COLUMN `framework` enum('js','mapping','dsym') NOT NULL;

ALTER TABLE `symbol_files`
  DROP COLUMN `file_content`;

ALTER TABLE `symbol_files`
  MODIFY COLUMN `s3_key` varchar(512) NOT NULL;

ALTER TABLE `symbol_files`
  ADD CONSTRAINT `fk_symbol_files_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`) ON DELETE CASCADE;


-- -----------------------------------------------------------------------------
-- SECTION 5 — Column nullability + tenant id
-- -----------------------------------------------------------------------------

-- >>> DATA REQUIRED: set real project_id for any NULL before NOT NULL:
-- UPDATE athena_job SET project_id = 'REPLACE_ME' WHERE project_id IS NULL;

ALTER TABLE `athena_job`
  MODIFY COLUMN `project_id` varchar(64) NOT NULL COMMENT 'Project where query was executed (data isolation)';

-- >>> DATA REQUIRED: assign tenant_id for NULL rows (must be unique vs tenants.tenant_id):
-- UPDATE tenants SET tenant_id = CONCAT('tenant-', id) WHERE tenant_id IS NULL;

ALTER TABLE `tenants`
  MODIFY COLUMN `tenant_id` varchar(64) NOT NULL;


-- -----------------------------------------------------------------------------
-- SECTION 6 — Add FKs / constraints present on LOCAL only
-- -----------------------------------------------------------------------------

ALTER TABLE `alert_scope`
  ADD CONSTRAINT `fk_subject_alert` FOREIGN KEY (`alert_id`) REFERENCES `alerts` (`id`);

ALTER TABLE `channel_event_mapping`
  ADD CONSTRAINT `fk_mapping_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`) ON DELETE CASCADE;

ALTER TABLE `clickhouse_project_credentials`
  ADD CONSTRAINT `fk_chcred_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`) ON DELETE CASCADE;

ALTER TABLE `project_api_keys`
  ADD CONSTRAINT `fk_pak_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`) ON DELETE CASCADE;

ALTER TABLE `project_usage_limits`
  ADD CONSTRAINT `fk_pul_project` FOREIGN KEY (`project_id`) REFERENCES `projects` (`project_id`) ON DELETE CASCADE;


SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- OPTIONAL — Collation parity with local dumps (utf8mb4_0900_ai_ci)
-- Run only if you want DB/table default collation to match local mysqldump defaults.
-- Review app + index behavior before applying in prod.
-- -----------------------------------------------------------------------------
-- ALTER DATABASE pulse_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
