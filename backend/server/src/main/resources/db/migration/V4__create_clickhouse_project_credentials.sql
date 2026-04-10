-- ClickHouse Project Credentials Table
-- Stores encrypted credentials for per-project ClickHouse users
-- Each project gets its own dedicated ClickHouse user with row-level policies

CREATE TABLE IF NOT EXISTS clickhouse_project_credentials (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id VARCHAR(100) NOT NULL UNIQUE COMMENT 'Project ID (proj-{uuid})',
    clickhouse_username VARCHAR(100) NOT NULL COMMENT 'ClickHouse username for this project',
    clickhouse_password_encrypted TEXT NOT NULL COMMENT 'AES encrypted password',
    encryption_salt VARCHAR(100) NOT NULL COMMENT 'Salt used for encryption',
    password_digest VARCHAR(100) NOT NULL COMMENT 'SHA-256 digest for verification',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Credential active status',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_ch_project_cred FOREIGN KEY (project_id) 
        REFERENCES projects(project_id) ON DELETE CASCADE,
    
    INDEX idx_project_active (project_id, is_active),
    INDEX idx_username (clickhouse_username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci 
COMMENT='Per-project ClickHouse credentials for data isolation';
