-- Pulse Observability - MySQL Database Initialization
-- This script creates the necessary database schema for Pulse Server

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS pulse_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pulse_db;

CREATE TABLE interaction (
    interaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(25) NOT NULL,
    details JSON,
    is_archived TINYINT(1) DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL,
    last_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(255)
);

-- Minimal table
CREATE TABLE symbol_files (
  app_version       VARCHAR(64)         NOT NULL,  -- e.g. "7.3.1"
  app_version_code  INT                 NOT NULL,  -- e.g. 7030100
  platform          ENUM('ios','android') NOT NULL,
  framework         ENUM('java','js')   NOT NULL,  -- java = R8 mapping.txt, js = RN sourcemap
  file_content      LONGBLOB            NOT NULL,  -- raw bytes of mapping.txt or .map
  PRIMARY KEY (app_version, app_version_code, platform, framework)
);

CREATE TABLE pulse_sdk_configs (
  version     INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  description TEXT NOT NULL,
  is_active   BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by  VARCHAR(255),
  config_json JSON NOT NULL
);

-- Grant privileges (adjust as needed for your environment)
-- GRANT ALL PRIVILEGES ON pulse_db.* TO 'pulse_user'@'%' IDENTIFIED BY 'pulse_password';
-- FLUSH PRIVILEGES;

-- Display summary
SELECT 'Database initialization completed successfully!' AS status;
SELECT COUNT(*) AS total_tables FROM information_schema.tables WHERE table_schema = 'pulse_db';
