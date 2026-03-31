-- =============================================================================
-- Redesign: funnel, journey, spark_jobs tables
-- Replaces V9 funnel/funnel_job and V10 journey/journey_job
-- =============================================================================
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS funnel_job;
DROP TABLE IF EXISTS journey_job;
DROP TABLE IF EXISTS funnel;
DROP TABLE IF EXISTS journey;
DROP TABLE IF EXISTS spark_jobs;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- funnel
-- =============================================================================
CREATE TABLE funnel (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        VARCHAR(64)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    description       TEXT         NULL,
    funnel_type       VARCHAR(32)  NOT NULL DEFAULT 'AUTO'     COMMENT 'AUTO | ONCE',
    step_order_type   VARCHAR(32)  NOT NULL DEFAULT 'ORDERED'  COMMENT 'ORDERED | UNORDERED',
    steps_json        JSON         NOT NULL                    COMMENT 'Array of { eventName, stepFilters? }',
    window_seconds    BIGINT       NOT NULL DEFAULT 86400,
    mode              VARCHAR(32)  NOT NULL DEFAULT 'UNIQUE_USERS' COMMENT 'UNIQUE_USERS | SESSIONS',
    filters_json      JSON         NULL,
    date_range        INT          NOT NULL DEFAULT 7          COMMENT 'Lookback days for bulk Spark run',
    start_time        TIMESTAMP    NULL                        COMMENT 'On-demand: analysis window start',
    end_time          TIMESTAMP    NULL                        COMMENT 'On-demand: analysis window end',
    expiry            TIMESTAMP    NULL                        COMMENT 'AUTO funnels skip after this date',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        VARCHAR(255) NULL,

    CONSTRAINT fk_funnel_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_funnel_project (project_id),
    INDEX idx_funnel_updated (updated_at),
    INDEX idx_funnel_project_updated (project_id, updated_at),
    FULLTEXT INDEX idx_funnel_name_fts (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Saved funnel definitions for Spark computation and dashboard';

-- =============================================================================
-- journey
-- =============================================================================
CREATE TABLE journey (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        VARCHAR(64)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    description       TEXT         NULL,
    anchor_event      VARCHAR(255) NOT NULL                    COMMENT 'Anchor event for path traversal',
    direction         VARCHAR(32)  NOT NULL                    COMMENT 'START | END',
    depth             INT          NOT NULL DEFAULT 5          COMMENT 'Number of event levels from anchor',
    mode              VARCHAR(32)  NOT NULL DEFAULT 'UNIQUE_USERS' COMMENT 'UNIQUE_USERS | SESSIONS',
    filters_json      JSON         NULL,
    start_time        TIMESTAMP    NULL,
    end_time          TIMESTAMP    NULL,
    journey_type      VARCHAR(32)  NOT NULL DEFAULT 'AUTO'     COMMENT 'AUTO | ONCE',
    expiry            TIMESTAMP    NULL,
    date_range        INT          NOT NULL DEFAULT 7,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        VARCHAR(255) NULL,

    CONSTRAINT fk_journey_project FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,
    INDEX idx_journey_project (project_id),
    INDEX idx_journey_updated (updated_at),
    INDEX idx_journey_project_updated (project_id, updated_at),
    INDEX idx_journey_anchor_event (anchor_event),
    INDEX idx_journey_direction (direction),
    FULLTEXT INDEX idx_journey_name_fts (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Saved journey definitions for event path exploration and dashboard';

-- =============================================================================
-- spark_jobs  (single table for all job types)
-- =============================================================================
CREATE TABLE spark_jobs (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_type       VARCHAR(32)  NOT NULL COMMENT 'FUNNEL | JOURNEY | BULK_FUNNEL | BULK_JOURNEY | EVENT_CATALOG',
    reference_id   BIGINT       NULL     COMMENT 'funnel.id or journey.id; NULL for bulk jobs',
    job_id         VARCHAR(255) NULL     COMMENT 'EMR/Glue job run id',
    status         VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING | RUNNING | SUCCEEDED | FAILED',
    error_message  TEXT         NULL,
    started_at     TIMESTAMP    NULL,
    completed_at   TIMESTAMP    NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_analysis_job_entity (job_type, reference_id),
    INDEX idx_analysis_job_status (status),
    INDEX idx_analysis_job_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='Spark job status for all analysis types';
