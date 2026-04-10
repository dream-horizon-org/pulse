-- Create users table for authentication and user management
-- This table stores user profile information from Google OAuth
-- Relationships to tenants and projects are managed in OpenFGA

CREATE TABLE IF NOT EXISTS users (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id           VARCHAR(255) NOT NULL UNIQUE COMMENT 'Unique user identifier (user-{uuid})',
    email             VARCHAR(255) NOT NULL UNIQUE COMMENT 'User email from Google OAuth',
    name              VARCHAR(255) NOT NULL COMMENT 'User display name',
    profile_picture   VARCHAR(512) COMMENT 'URL to user profile picture',
    is_active         BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'User account status',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    INDEX idx_user_email (email),
    INDEX idx_user_id (user_id),
    INDEX idx_user_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='User profiles and authentication data';
