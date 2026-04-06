-- =============================================================================
-- Saved Funnels and On-Save Job Status
-- =============================================================================
-- funnel: saved funnel definitions (steps, window, date range).
-- funnel_job: status of the on-save Spark job per funnel (UI polling).
-- Daily batch job status is not stored; use Glue/cron logs.
-- =============================================================================

CREATE TABLE IF NOT EXISTS funnel (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        VARCHAR(64)  NOT NULL COMMENT 'Project (proj-xxx)',
    name              VARCHAR(255) NOT NULL COMMENT 'Display name',
    description       TEXT         NULL COMMENT 'Optional funnel description',
    funnel_type       VARCHAR(32)  NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO | ONCE',
    step_order_type   VARCHAR(32)  NOT NULL DEFAULT 'ORDERED' COMMENT 'ORDERED | UNORDERED',
    steps_json        JSON         NOT NULL COMMENT 'Array of { eventName, dataType?, stepFilters? }',
    window_seconds    BIGINT       NOT NULL DEFAULT 86400 COMMENT 'Funnel window in seconds',
    mode              VARCHAR(32)  NOT NULL DEFAULT 'UNIQUE_USERS' COMMENT 'UNIQUE_USERS | SESSIONS',
    filters_json      JSON         NULL COMMENT 'Global filters (same shape as FunnelRequest.filters)',
    date_range        INT          NOT NULL DEFAULT 7 COMMENT 'Lookback days for Spark (e.g. 7 or 30)',
    start_time        TIMESTAMP    NULL COMMENT 'Selected analysis start date and time',
    end_time          TIMESTAMP    NULL COMMENT 'Selected analysis end date and time',
    expiry            TIMESTAMP    NULL COMMENT 'Optional funnel activation expiry',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        VARCHAR(255) NULL,

    CONSTRAINT fk_funnel_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,

    INDEX idx_funnel_project (project_id),
    INDEX idx_funnel_updated (updated_at),
    INDEX idx_funnel_project_updated (project_id, updated_at),
    FULLTEXT INDEX idx_funnel_name_fts (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Saved funnel definitions for Spark computation and dashboard';

-- -----------------------------------------------------------------------------
-- On-save Spark job status (one row per run; latest per funnel for "current" job)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS spark_jobs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_type       VARCHAR(32)  NOT NULL COMMENT 'FUNNEL | JOURNEY',
    reference_id    BIGINT       NULL COMMENT 'References funnel.id or journey.id',
    job_id         VARCHAR(255) NULL COMMENT 'Spark/Glue/EMR job run id',
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | RUNNING | SUCCEEDED | FAILED',
    error_message  TEXT         NULL,
    started_at     TIMESTAMP    NULL,
    completed_at   TIMESTAMP    NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_analysis_job_entity (job_type, reference_id),
    INDEX idx_analysis_job_status (status),
    INDEX idx_analysis_job_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Spark job status for saved funnel and journey analyses';

-- =============================================================================
-- Saved Journeys
-- =============================================================================
-- journey: saved journey definitions for path exploration from an anchor event.
-- Supports forward and backward traversal with configurable depth and filters.
-- =============================================================================

CREATE TABLE IF NOT EXISTS journey (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        VARCHAR(64)  NOT NULL COMMENT 'Project (proj-xxx)',
    name              VARCHAR(255) NOT NULL COMMENT 'Display name',
    description       TEXT         NULL COMMENT 'Optional journey description',

    anchor_event      VARCHAR(255) NOT NULL COMMENT 'Anchor event name',
    direction         VARCHAR(32)  NOT NULL COMMENT 'START | END',
    depth             INT          NOT NULL DEFAULT 5 COMMENT 'Number of event levels to traverse',
    mode              VARCHAR(32)  NOT NULL DEFAULT 'UNIQUE_USERS' COMMENT 'UNIQUE_USERS | SESSIONS',
    filters_json      JSON         NULL COMMENT 'Global filters for journey analysis',
    start_time        TIMESTAMP    NULL COMMENT 'Selected analysis start date and time',
    end_time          TIMESTAMP    NULL COMMENT 'Selected analysis end date and time',
    journey_type      VARCHAR(32)  NOT NULL DEFAULT 'AUTO' COMMENT 'AUTO | ONCE',
    expiry             TIMESTAMP    NULL COMMENT 'Optional funnel activation expiry',
    date_range        INT          NOT NULL DEFAULT 7 COMMENT 'Lookback days for Spark (e.g. 7 or 30)',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        VARCHAR(255) NULL,

    CONSTRAINT fk_journey_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,

    INDEX idx_journey_project (project_id),
    INDEX idx_journey_updated (updated_at),
    INDEX idx_journey_project_updated (project_id, updated_at),
    INDEX idx_journey_anchor_event (anchor_event),
    INDEX idx_journey_direction (direction),
    FULLTEXT INDEX idx_journey_name_fts (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Saved journey definitions for event path exploration and dashboard';