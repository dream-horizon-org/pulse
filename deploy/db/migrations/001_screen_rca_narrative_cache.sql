-- Screen RCA narrative cache (matches mysql-init.sql).
-- Apply when upgrading an existing MySQL volume (init scripts do not re-run).
--
-- Example (defaults from docker-compose):
--   docker exec -i pulse-mysql mysql -u"${MYSQL_USER:-pulse_user}" -p"${MYSQL_PASSWORD:-pulse_password}" "${MYSQL_DATABASE:-pulse_db}" < deploy/db/migrations/001_screen_rca_narrative_cache.sql
--
-- If you previously created the table with window_start/window_end DATETIME columns,
-- run 002_screen_rca_narrative_cache_use_dates.sql instead (drops and recreates this cache table).

CREATE TABLE IF NOT EXISTS screen_rca_narrative_cache (
    project_id VARCHAR(64) NOT NULL,
    screen_name VARCHAR(512) NOT NULL,
    window_start_date DATE NOT NULL COMMENT 'UTC calendar date of request start instant',
    window_end_date DATE NOT NULL COMMENT 'UTC calendar date of request end instant',
    payload_fingerprint CHAR(64) NOT NULL COMMENT 'SHA-256 hex of rootCausePayload JSON',
    report_body MEDIUMTEXT NOT NULL,
    cached_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (project_id, screen_name, window_start_date, window_end_date, payload_fingerprint)
);
