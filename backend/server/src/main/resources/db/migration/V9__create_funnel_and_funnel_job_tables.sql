-- =============================================================================
-- Saved Funnels and On-Save Job Status
-- =============================================================================
-- funnel: saved funnel definitions (steps, window, date range).
-- funnel_job: status of the on-save Spark job per funnel (UI polling).
-- Daily batch job status is not stored; use Glue/cron logs.
-- =============================================================================

CREATE TABLE IF NOT EXISTS funnel (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    funnel_id         VARCHAR(64)  NOT NULL UNIQUE COMMENT 'External ID e.g. UUID',
    project_id        VARCHAR(64)  NOT NULL COMMENT 'Project (proj-xxx)',
    name              VARCHAR(255) NOT NULL COMMENT 'Display name',
    steps_json        JSON         NOT NULL COMMENT 'Array of { eventName, dataType?, stepFilters? }',
    window_seconds    BIGINT       NOT NULL DEFAULT 86400 COMMENT 'Funnel window in seconds',
    mode              VARCHAR(32)  NOT NULL DEFAULT 'UNIQUE_USERS' COMMENT 'UNIQUE_USERS | SESSIONS',
    date_range_days   INT          NOT NULL DEFAULT 7 COMMENT 'Lookback days for Spark (e.g. 7 or 30)',
    filters_json      JSON         NULL COMMENT 'Global filters (same shape as FunnelRequest.filters)',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        VARCHAR(255) NULL,

    CONSTRAINT fk_funnel_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_funnel_project (project_id),
    INDEX idx_funnel_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Saved funnel definitions for Spark computation and dashboard';

-- -----------------------------------------------------------------------------
-- On-save Spark job status (one row per run; latest per funnel for "current" job)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS funnel_job (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    funnel_id      VARCHAR(64)  NOT NULL COMMENT 'References funnel.funnel_id',
    job_id         VARCHAR(255) NULL COMMENT 'Glue/EMR job run id',
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | RUNNING | SUCCEEDED | FAILED',
    run_date       DATE         NULL COMMENT 'Date of data computed',
    error_message  TEXT         NULL,
    started_at     TIMESTAMP    NULL,
    completed_at   TIMESTAMP    NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_funnel_job_funnel FOREIGN KEY (funnel_id) REFERENCES funnel(funnel_id) ON DELETE CASCADE,
    INDEX idx_funnel_job_funnel (funnel_id),
    INDEX idx_funnel_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='On-save Spark job status for UI polling (Computing... / done / failed)';
