-- Older local volumes may lack analytics_jobs (funnel DAO subquery references it).
USE pulse_db;

CREATE TABLE IF NOT EXISTS analytics_jobs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_type       VARCHAR(32)  NOT NULL,
    reference_id   BIGINT       NULL,
    job_id         VARCHAR(255) NULL,
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    error_message  TEXT         NULL,
    started_at     TIMESTAMP    NULL,
    completed_at   TIMESTAMP    NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_analysis_job_entity (job_type, reference_id),
    INDEX idx_analysis_job_status (status),
    INDEX idx_analysis_job_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Optional: latest FUNNEL job row for funnel id=5 (UI status subquery).
INSERT INTO analytics_jobs (job_type, reference_id, status, started_at, completed_at)
SELECT 'FUNNEL', 5, 'SUCCEEDED', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM analytics_jobs WHERE job_type = 'FUNNEL' AND reference_id = 5
);
