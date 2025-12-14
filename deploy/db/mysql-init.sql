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


CREATE TABLE Severity
(
    severity_id INT PRIMARY KEY AUTO_INCREMENT,
    name        INT NOT NULL,
    description TEXT
);

CREATE TABLE Notification_Channels
(
    notification_channel_id INT PRIMARY KEY AUTO_INCREMENT,
    name                    VARCHAR(100) NOT NULL,
    notification_webhook_url                  TEXT
);


CREATE TABLE Alerts (
  id                          INT PRIMARY KEY AUTO_INCREMENT,
  name                        TEXT NOT NULL,
  description                 TEXT NOT NULL,
  scope                       VARCHAR(100) NOT NULL,
  dimension_filter            TEXT,
  condition_expression        VARCHAR(255) NOT NULL,
  severity_id                 INT NOT NULL,
  notification_channel_id     INT NOT NULL,
  evaluation_period           INT NOT NULL,
  evaluation_interval         INT NOT NULL,
  last_snoozed_at             TIMESTAMP NULL DEFAULT NULL,
  snoozed_from                TIMESTAMP NULL DEFAULT NULL,
  snoozed_until               TIMESTAMP NULL DEFAULT NULL,
  created_by                  VARCHAR(255) NOT NULL,
  updated_by                  VARCHAR(255),
  created_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  is_active                   BOOLEAN NOT NULL DEFAULT TRUE,

  CONSTRAINT fk_alert_severity
    FOREIGN KEY (severity_id) REFERENCES Severity(severity_id),

  CONSTRAINT fk_alert_notification_channel
    FOREIGN KEY (notification_channel_id) REFERENCES Notification_Channels(notification_channel_id)
);

CREATE TABLE Alert_Scope (
  id                     INT PRIMARY KEY AUTO_INCREMENT,
  alert_id               INT NOT NULL,
  name                   VARCHAR(255) NOT NULL,
  conditions             JSON NULL,
  state                  VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
  is_active              BOOLEAN NOT NULL DEFAULT TRUE,
  created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_subject_alert FOREIGN KEY (alert_id) REFERENCES Alerts (id)
);

CREATE TABLE Alert_Evaluation_History (
  evaluation_id      INT PRIMARY KEY AUTO_INCREMENT,
  scope_id           INT NOT NULL,
  evaluation_result  JSON NOT NULL,
  state              VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
  evaluated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_eval_subject FOREIGN KEY (scope_id) REFERENCES Alert_Scope (id)
);

CREATE TABLE Alert_Metrics (
  id                  INT PRIMARY KEY AUTO_INCREMENT,
  name                VARCHAR(255) NOT NULL,
  label               VARCHAR(500) NOT NULL,
  scope               VARCHAR(100) NOT NULL,
  created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY unique_metric_scope (name, scope)
);

INSERT INTO Severity (name, description)
VALUES
    (1, 'It is at incident commanders discretion to update the severity of the incident since this is not clubbed now anymore for multiple services.'),
    (2, 'It is at incident commanders discretion to update the severity of the incident since this is not clubbed now anymore for multiple services.'),
    (3, 'SPM of 1 service is impacted'),
    (3, 'Operator alerts triggered for multiple services'),
    (3, 'Operator alerts triggered for 1 service');

INSERT INTO Notification_Channels (name, notification_webhook_url)
VALUES
    ('Incident management', 'http://whistlebot.local/declare-incident');

-- Insert Interaction scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('APDEX', 'APDEX value [0,1]', 'interaction'),
    ('CRASH', 'CRASH value >= 0', 'interaction'),
    ('ANR', 'ANR value >= 0', 'interaction'),
    ('FROZEN_FRAME', 'FROZEN_FRAME value >= 0', 'interaction'),
    ('ANALYSED_FRAME', 'ANALYSED_FRAME value >= 0', 'interaction'),
    ('UNANALYSED_FRAME', 'UNANALYSED_FRAME value >= 0', 'interaction'),
    ('DURATION_P99', 'DURATION_P99 value >= 0', 'interaction'),
    ('DURATION_P95', 'DURATION_P95 value >= 0', 'interaction'),
    ('DURATION_P50', 'DURATION_P50 value >= 0', 'interaction'),
    ('ERROR_RATE', 'ERROR_RATE value [0,1]', 'interaction'),
    ('INTERACTION_SUCCESS_COUNT', 'INTERACTION_SUCCESS_COUNT value >= 0', 'interaction'),
    ('INTERACTION_ERROR_COUNT', 'INTERACTION_ERROR_COUNT value >= 0', 'interaction'),
    ('INTERACTION_ERROR_DISTINCT_USERS', 'INTERACTION_ERROR_DISTINCT_USERS value >= 0', 'interaction'),
    ('USER_CATEGORY_EXCELLENT', 'USER_CATEGORY_EXCELLENT value >= 0', 'interaction'),
    ('USER_CATEGORY_GOOD', 'USER_CATEGORY_GOOD value >= 0', 'interaction'),
    ('USER_CATEGORY_AVERAGE', 'USER_CATEGORY_AVERAGE value >= 0', 'interaction'),
    ('USER_CATEGORY_POOR', 'USER_CATEGORY_POOR value >= 0', 'interaction'),
    ('CRASH_RATE', 'CRASH_RATE value [0,1]', 'interaction'),
    ('ANR_RATE', 'ANR_RATE value [0,1]', 'interaction'),
    ('FROZEN_FRAME_RATE', 'FROZEN_FRAME_RATE value [0,1]', 'interaction'),
    ('POOR_USER_RATE', 'POOR_USER_RATE value [0,1]', 'interaction'),
    ('AVERAGE_USER_RATE', 'AVERAGE_USER_RATE value [0,1]', 'interaction'),
    ('GOOD_USER_RATE', 'GOOD_USER_RATE value [0,1]', 'interaction'),
    ('EXCELLENT_USER_RATE', 'EXCELLENT_USER_RATE value [0,1]', 'interaction');

-- Insert Screen scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('SCREEN_DAILY_USERS', 'SCREEN_DAILY_USERS value >= 0', 'screen'),
    ('SCREEN_ACTIVE_USERS', 'SCREEN_ACTIVE_USERS value >= 0', 'screen'),
    ('SCREEN_ERROR_RATE', 'SCREEN_ERROR_RATE value [0,1]', 'screen'),
    ('SCREEN_TIME', 'SCREEN_TIME value >= 0', 'screen');

-- Insert APP_VITALS scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('APP_VITALS_CRASH_FREE_USERS_PERCENTAGE', 'APP_VITALS_CRASH_FREE_USERS_PERCENTAGE', 'app_vitals'),
    ('APP_VITALS_CRASH_FREE_SESSIONS_PERCENTAGE', 'APP_VITALS_CRASH_FREE_SESSIONS_PERCENTAGE', 'app_vitals'),
    ('APP_VITALS_CRASH_USERS', 'APP_VITALS_CRASH_USERS', 'app_vitals'),
    ('APP_VITALS_CRASH_SESSIONS', 'APP_VITALS_CRASH_SESSIONS', 'app_vitals'),
    ('APP_VITALS_ALL_USERS', 'APP_VITALS_ALL_USERS', 'app_vitals'),
    ('APP_VITALS_ALL_SESSIONS', 'APP_VITALS_ALL_SESSIONS', 'app_vitals'),
    ('APP_VITALS_ANR_FREE_USERS_PERCENTAGE', 'APP_VITALS_ANR_FREE_USERS_PERCENTAGE', 'app_vitals'),
    ('APP_VITALS_ANR_FREE_SESSIONS_PERCENTAGE', 'APP_VITALS_ANR_FREE_SESSIONS_PERCENTAGE', 'app_vitals'),
    ('APP_VITALS_ANR_USERS', 'APP_VITALS_ANR_USERS', 'app_vitals'),
    ('APP_VITALS_ANR_SESSIONS', 'APP_VITALS_ANR_SESSIONS', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_FREE_USERS_PERCENTAGE', 'APP_VITALS_NON_FATAL_FREE_USERS_PERCENTAGE', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_FREE_SESSIONS_PERCENTAGE', 'APP_VITALS_NON_FATAL_FREE_SESSIONS_PERCENTAGE', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_USERS', 'APP_VITALS_NON_FATAL_USERS', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_SESSIONS', 'APP_VITALS_NON_FATAL_SESSIONS', 'app_vitals');

-- Insert Network scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('NET_0', 'NET_0', 'network_api'),
    ('NET_2XX', 'NET_2XX', 'network_api'),
    ('NET_3XX', 'NET_3XX', 'network_api'),
    ('NET_4XX', 'NET_4XX', 'network_api'),
    ('NET_5XX', 'NET_5XX', 'network_api'),
    ('NET_4XX_RATE', 'NET_4XX_RATE', 'network_api'),
    ('NET_5XX_RATE', 'NET_5XX_RATE', 'network_api');

-- Grant privileges (adjust as needed for your environment)
-- GRANT ALL PRIVILEGES ON pulse_db.* TO 'pulse_user'@'%' IDENTIFIED BY 'pulse_password';
-- FLUSH PRIVILEGES;

-- Display summary
SELECT 'Database initialization completed successfully!' AS status;
SELECT COUNT(*) AS total_tables FROM information_schema.tables WHERE table_schema = 'pulse_db';
