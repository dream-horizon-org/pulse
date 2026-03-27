-- =============================================================================
-- pulse_db: DATA backfill BEFORE 20260326_prod_align_local_schema.sql
-- =============================================================================
-- Sink project_id matches deploy/db/mysql-init.sql sample project:
--   ('default-project', 'default', 'Default Project', ...)
-- (Hyphenated id: default-project — not default_project.)
--
-- Run order:
--   1) Full backup; run on prod clone first.
--   2) This script (backfill).
--   3) deploy/migrations/20260326_prod_align_local_schema.sql (DDL).
--
-- Optional: SET sql_safe_updates = 0; if your client requires WHERE keys on UPDATE.
-- =============================================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET FOREIGN_KEY_CHECKS = 0;

-- Sink must exist for all attribution fixes (init: mysql-init.sql)
SET @sink_project_id = 'default-project';
SET @sink_tenant_id = 'default';

-- -----------------------------------------------------------------------------
-- 0) Ensure sink tenant + project exist (prod may lack dev seed rows)
-- -----------------------------------------------------------------------------
INSERT INTO `tenants` (`tenant_id`, `name`, `description`, `tier_id`, `is_active`)
SELECT @sink_tenant_id, 'Default Tenant', 'Sink tenant for migration backfill', 1, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `tenants` t WHERE t.`tenant_id` <=> @sink_tenant_id);

INSERT INTO `projects` (
  `project_id`, `tenant_id`, `name`, `description`, `slug`, `is_active`, `created_by`
)
SELECT
  @sink_project_id,
  @sink_tenant_id,
  'Default Project',
  'Sink project for migration backfill (matches mysql-init default-project)',
  'default',
  1,
  'system'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `projects` p WHERE p.`project_id` <=> @sink_project_id);

-- Fail fast if sink still missing
SELECT `project_id`, `tenant_id` FROM `projects` WHERE `project_id` = @sink_project_id;
-- Expect exactly one row; abort manually if zero.

-- -----------------------------------------------------------------------------
-- 1) tenants: NOT NULL tenant_id (prod allowed NULL)
-- -----------------------------------------------------------------------------
UPDATE `tenants` `t`
SET `t`.`tenant_id` = CONCAT('legacy-tenant-', `t`.`id`)
WHERE `t`.`tenant_id` IS NULL
   OR `t`.`tenant_id` = '';

-- Resolve accidental duplicates from above (unlikely); re-run CONCAT with id only once per row
-- If this UPDATE touches 0 rows, schema may already be clean.

-- -----------------------------------------------------------------------------
-- 2) athena_job: NULL project_id → sink
-- -----------------------------------------------------------------------------
UPDATE `athena_job` `j`
SET `j`.`project_id` = @sink_project_id
WHERE `j`.`project_id` IS NULL
   OR NOT EXISTS (
     SELECT 1 FROM `projects` `p` WHERE `p`.`project_id` <=> `j`.`project_id`
   );

-- -----------------------------------------------------------------------------
-- 3) Children of projects: invalid project_id → sink
-- -----------------------------------------------------------------------------
UPDATE `channel_event_mapping` `m`
SET `m`.`project_id` = @sink_project_id
WHERE NOT EXISTS (
  SELECT 1 FROM `projects` `p` WHERE `p`.`project_id` <=> `m`.`project_id`
);

UPDATE `clickhouse_project_credentials` `c`
SET `c`.`project_id` = @sink_project_id
WHERE NOT EXISTS (
  SELECT 1 FROM `projects` `p` WHERE `p`.`project_id` <=> `c`.`project_id`
);

UPDATE `project_api_keys` `k`
SET `k`.`project_id` = @sink_project_id
WHERE NOT EXISTS (
  SELECT 1 FROM `projects` `p` WHERE `p`.`project_id` <=> `k`.`project_id`
);

UPDATE `project_usage_limits` `u`
SET `u`.`project_id` = @sink_project_id
WHERE NOT EXISTS (
  SELECT 1 FROM `projects` `p` WHERE `p`.`project_id` <=> `u`.`project_id`
);

UPDATE `pulse_sdk_configs` `s`
SET `s`.`project_id` = @sink_project_id
WHERE NOT EXISTS (
  SELECT 1 FROM `projects` `p` WHERE `p`.`project_id` <=> `s`.`project_id`
);

UPDATE `interaction` `i`
SET `i`.`project_id` = @sink_project_id
WHERE NOT EXISTS (
  SELECT 1 FROM `projects` `p` WHERE `p`.`project_id` <=> `i`.`project_id`
);

UPDATE `alerts` `a`
SET `a`.`project_id` = @sink_project_id
WHERE NOT EXISTS (
  SELECT 1 FROM `projects` `p` WHERE `p`.`project_id` <=> `a`.`project_id`
);

UPDATE `email_suppression_list` `e`
SET `e`.`project_id` = @sink_project_id
WHERE `e`.`project_id` IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `projects` `p` WHERE `p`.`project_id` <=> `e`.`project_id`
  );

UPDATE `notification_channels` `n`
SET `n`.`project_id` = @sink_project_id
WHERE `n`.`project_id` IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM `projects` `p` WHERE `p`.`project_id` <=> `n`.`project_id`
  );

-- -----------------------------------------------------------------------------
-- 4) alert_scope: remove orphan scopes (alert_id / alerts missing)
--    alert_evaluation_history references alert_scope — delete dependent rows first.
-- -----------------------------------------------------------------------------
DELETE `h` FROM `alert_evaluation_history` `h`
INNER JOIN `alert_scope` `s` ON `s`.`id` = `h`.`scope_id`
LEFT JOIN `alerts` `a` ON `a`.`id` = `s`.`alert_id`
WHERE `a`.`id` IS NULL;

DELETE `s` FROM `alert_scope` `s`
LEFT JOIN `alerts` `a` ON `a`.`id` = `s`.`alert_id`
WHERE `a`.`id` IS NULL;

-- -----------------------------------------------------------------------------
-- 5) symbol_files: legacy data not needed — purge all rows before DDL migration
-- -----------------------------------------------------------------------------
-- After purge, migration adds s3_key, drops file_content, etc. on an empty table.
DELETE FROM `symbol_files`;

SET FOREIGN_KEY_CHECKS = 1;

-- -----------------------------------------------------------------------------
-- 6) Post-checks (expect 0 for each count)
-- -----------------------------------------------------------------------------
SELECT 'athena_bad_project' AS `check`, COUNT(*) AS `n` FROM `athena_job` `j`
LEFT JOIN `projects` `p` ON `p`.`project_id` <=> `j`.`project_id`
WHERE `p`.`project_id` IS NULL
UNION ALL
SELECT 'tenants_null_tid', COUNT(*) FROM `tenants` WHERE `tenant_id` IS NULL OR `tenant_id` = ''
UNION ALL
SELECT 'mapping_bad_project', COUNT(*) FROM `channel_event_mapping` `m`
LEFT JOIN `projects` `p` ON `p`.`project_id` <=> `m`.`project_id` WHERE `p`.`project_id` IS NULL
UNION ALL
SELECT 'alert_scope_orphan', COUNT(*) FROM `alert_scope` `s`
LEFT JOIN `alerts` `a` ON `a`.`id` = `s`.`alert_id` WHERE `a`.`id` IS NULL
UNION ALL
SELECT 'symbol_files_rows', COUNT(*) FROM `symbol_files`;
