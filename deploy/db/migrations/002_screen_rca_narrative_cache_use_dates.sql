-- Migrate screen_rca_narrative_cache from DATETIME window to DATE window (UTC calendar dates).
-- Cache is ephemeral: drop and recreate. Run after 001 if the old schema exists.
--
-- Example:
--   docker exec -i pulse-mysql mysql -u"${MYSQL_USER:-pulse_user}" -p"${MYSQL_PASSWORD:-pulse_password}" "${MYSQL_DATABASE:-pulse_db}" < deploy/db/migrations/002_screen_rca_narrative_cache_use_dates.sql

DROP TABLE IF EXISTS screen_rca_narrative_cache;

CREATE TABLE screen_rca_narrative_cache (
    project_id VARCHAR(64) NOT NULL,
    screen_name VARCHAR(512) NOT NULL,
    window_start_date DATE NOT NULL COMMENT 'UTC calendar date of request start instant',
    window_end_date DATE NOT NULL COMMENT 'UTC calendar date of request end instant',
    payload_fingerprint CHAR(64) NOT NULL COMMENT 'SHA-256 hex of rootCausePayload JSON',
    report_body MEDIUMTEXT NOT NULL,
    cached_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (project_id, screen_name, window_start_date, window_end_date, payload_fingerprint)
);
