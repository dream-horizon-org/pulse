-- =============================================================================
-- Funnel / journey tags (many-to-many via mapping rows)
-- =============================================================================
-- One row per (project, entity type, entity id, tag). entity_id is funnel.id or
-- journey.id. Tags are free-form strings (trimmed by the API); uniqueness is
-- enforced per entity so the same label is not stored twice for one funnel/journey.
-- =============================================================================

CREATE TABLE IF NOT EXISTS funnel_journey_tag (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id    VARCHAR(64)  NOT NULL,
    entity_type   VARCHAR(16)  NOT NULL COMMENT 'FUNNEL | JOURNEY',
    entity_id     BIGINT       NOT NULL COMMENT 'funnel.id or journey.id',
    tag           VARCHAR(128) NOT NULL,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_funnel_journey_tag_project
        FOREIGN KEY (project_id) REFERENCES projects(project_id) ON DELETE CASCADE,

    UNIQUE KEY uk_funnel_journey_tag (project_id, entity_type, entity_id, tag),
    KEY idx_funnel_journey_tag_entity (project_id, entity_type, entity_id),
    KEY idx_funnel_journey_tag_tag (project_id, tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Tag mappings for saved funnels and journeys';
