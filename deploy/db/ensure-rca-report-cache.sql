-- Idempotent: safe if table already exists (e.g. fresh mysql-init.sql volume).
-- Run when you see: Table 'pulse_db.rca_report_cache' doesn't exist

CREATE TABLE IF NOT EXISTS rca_report_cache (
    project_id VARCHAR(64) NOT NULL,
    interaction_name VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    report_body LONGTEXT NOT NULL,
    cached_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (project_id, interaction_name, date)
);
