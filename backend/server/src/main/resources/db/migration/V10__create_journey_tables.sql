-- =============================================================================
-- Saved Journeys and On-Save Job Status
-- =============================================================================
-- journey:     saved journey definitions (steps, window, date range).
-- journey_job: status of the on-save Spark job per journey (UI polling).
-- =============================================================================

CREATE TABLE IF NOT EXISTS journey (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    journey_id        VARCHAR(64)  NOT NULL UNIQUE COMMENT 'External ID e.g. UUID',
    project_id        VARCHAR(64)  NOT NULL COMMENT 'Project (proj-xxx)',
    name              VARCHAR(255) NOT NULL COMMENT 'Display name',
    steps_json        JSON         NOT NULL COMMENT 'Array of { stepType, eventName, stepFilters? }',
    window_seconds    BIGINT       NOT NULL DEFAULT 86400 COMMENT 'Journey window in seconds',
    mode              VARCHAR(32)  NOT NULL DEFAULT 'UNIQUE_USERS' COMMENT 'UNIQUE_USERS | SESSIONS',
    date_range_days   INT          NOT NULL DEFAULT 7 COMMENT 'Lookback days for Spark',
    filters_json      JSON         NULL COMMENT 'Global filters applied before path computation',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        VARCHAR(255) NULL,

    CONSTRAINT fk_journey_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_journey_project (project_id),
    INDEX idx_journey_updated (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Saved journey definitions for Spark path-graph computation';

-- On-save Spark job status (one row per run; latest per journey for "current" job)
CREATE TABLE IF NOT EXISTS journey_job (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    journey_id     VARCHAR(64)  NOT NULL COMMENT 'References journey.journey_id',
    job_id         VARCHAR(255) NULL COMMENT 'Glue/EMR job run id',
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | RUNNING | SUCCEEDED | FAILED',
    run_date       DATE         NULL COMMENT 'Date of data computed',
    error_message  TEXT         NULL,
    started_at     TIMESTAMP    NULL,
    completed_at   TIMESTAMP    NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_journey_job_journey FOREIGN KEY (journey_id) REFERENCES journey(journey_id) ON DELETE CASCADE,
    INDEX idx_journey_job_journey (journey_id),
    INDEX idx_journey_job_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='On-save Spark job status for UI polling';
