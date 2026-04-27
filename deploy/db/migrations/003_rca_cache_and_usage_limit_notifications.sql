-- One-time upgrade for older MySQL volumes: legacy rca_report_cache (interaction_name, no
-- rca_type), missing rca_report_jobs, and usage_limit_notifications without project_usage_limit_id /
-- is_active. Do not re-run after a successful apply (ALTERs will error).
--
-- Example:
--   docker exec -i pulse-mysql mysql -u"${MYSQL_USER:-pulse_user}" -p"${MYSQL_PASSWORD:-pulse_password}" "${MYSQL_DATABASE:-pulse_db}" < deploy/db/migrations/003_rca_cache_and_usage_limit_notifications.sql

-- ---------------------------------------------------------------------------
-- 1) rca_report_cache: interaction_name -> entity_key + rca_type (INTERACTION)
-- ---------------------------------------------------------------------------
ALTER TABLE rca_report_cache
  ADD COLUMN rca_type VARCHAR(32) NOT NULL DEFAULT 'INTERACTION' AFTER project_id,
  CHANGE COLUMN interaction_name entity_key VARCHAR(255) NOT NULL,
  DROP PRIMARY KEY,
  ADD PRIMARY KEY (project_id, rca_type, entity_key, date);

-- ---------------------------------------------------------------------------
-- 2) rca_report_jobs (missing entirely on older volumes)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rca_report_jobs (
    job_id VARCHAR(64) NOT NULL,
    project_id VARCHAR(64) NOT NULL,
    rca_type VARCHAR(32) NOT NULL,
    entity_key VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    status ENUM('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    error_message TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    created_by VARCHAR(255) NULL,
    worker_instance_id VARCHAR(64) NULL,
    PRIMARY KEY (job_id),
    UNIQUE KEY uk_active_job (project_id, rca_type, entity_key, date, status),
    INDEX idx_lookup (project_id, rca_type, entity_key, date),
    INDEX idx_status_created (status, created_at)
);

-- ---------------------------------------------------------------------------
-- 3) usage_limit_notifications: project_usage_limit_id + is_active + FK
-- ---------------------------------------------------------------------------
ALTER TABLE usage_limit_notifications
  ADD COLUMN project_usage_limit_id BIGINT NULL COMMENT 'FK to project_usage_limits' AFTER thresholds_notified,
  ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE AFTER project_usage_limit_id;

UPDATE usage_limit_notifications uln
INNER JOIN project_usage_limits pul
  ON pul.project_id = uln.project_id AND pul.is_active = TRUE
SET uln.project_usage_limit_id = pul.project_usage_limit_id
WHERE uln.project_usage_limit_id IS NULL;

UPDATE usage_limit_notifications uln
INNER JOIN (
  SELECT project_id, MIN(project_usage_limit_id) AS project_usage_limit_id
  FROM project_usage_limits
  GROUP BY project_id
) pul ON pul.project_id = uln.project_id
SET uln.project_usage_limit_id = pul.project_usage_limit_id
WHERE uln.project_usage_limit_id IS NULL;

ALTER TABLE usage_limit_notifications
  MODIFY COLUMN project_usage_limit_id BIGINT NOT NULL COMMENT 'FK to project_usage_limits row version at first notification for the month';

CREATE INDEX idx_project_usage_limit ON usage_limit_notifications (project_usage_limit_id);

ALTER TABLE usage_limit_notifications
  ADD CONSTRAINT fk_usage_notif_limit
    FOREIGN KEY (project_usage_limit_id)
    REFERENCES project_usage_limits (project_usage_limit_id)
    ON DELETE RESTRICT;
