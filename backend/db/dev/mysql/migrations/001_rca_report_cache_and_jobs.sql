-- Apply on existing local pulse_db volumes created before rca_report_* tables were in mysql-init.sql.
-- Safe to re-run (CREATE TABLE IF NOT EXISTS).

USE pulse_db;

CREATE TABLE IF NOT EXISTS rca_report_cache (
    project_id VARCHAR(64) NOT NULL,
    rca_type VARCHAR(32) NOT NULL,
    entity_key VARCHAR(255) NOT NULL,
    date DATE NOT NULL,
    report_body LONGTEXT NOT NULL,
    cached_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (project_id, rca_type, entity_key, date)
);

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
