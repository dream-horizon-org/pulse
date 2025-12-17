-- Pulse Observability - MySQL Database Initialization
-- This script creates the necessary database schema for Pulse Server

-- Create database if not exists
CREATE DATABASE IF NOT EXISTS pulse_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE pulse_db;

CREATE TABLE Interaction (
    Interaction_id BIGINT PRIMARY KEY AUTO_INCREMENT,
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

CREATE TABLE Scope_Types (
  id                  INT PRIMARY KEY AUTO_INCREMENT,
  name                VARCHAR(255) NOT NULL UNIQUE,
  label               VARCHAR(500) NOT NULL,
  created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
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
    (1, 'Critical: Production outage or severe degradation with significant user impact. Requires immediate action and incident management.'),
    (2, 'Warning: Degraded performance, elevated errors, or risk of user impact. Should be investigated soon but is not a full outage.'),
    (3, 'Info: Informational or low-risk condition. No immediate action required; useful for visibility, trend analysis, or validation of changes.');

INSERT INTO Notification_Channels (name, notification_webhook_url)
VALUES
    ('Incident management', 'http://whistlebot.local/declare-incident');

-- Insert Scope Types
INSERT INTO Scope_Types (name, label) VALUES
    ('interaction', 'Interactions'),
    ('network_api', 'Network APIs'),
    ('screen', 'Screen'),
    ('app_vitals', 'App Vitals');

-- Insert interaction scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('APDEX', 'Apdex Score (0 to 1)', 'interaction'),
    ('CRASH', 'Crash Count', 'interaction'),
    ('ANR', 'ANR Count', 'interaction'),
    ('FROZEN_FRAME', 'Frozen Frame Count', 'interaction'),
    ('ANALYSED_FRAME', 'Analysed Frame Count', 'interaction'),
    ('UNANALYSED_FRAME', 'Unanalysed Frame Count', 'interaction'),
    ('DURATION_P99', 'Duration P99 (ms)', 'interaction'),
    ('DURATION_P95', 'Duration P95 (ms)', 'interaction'),
    ('DURATION_P50', 'Duration P50 (ms)', 'interaction'),
    ('ERROR_RATE', 'Error Rate (%)', 'interaction'),
    ('Interaction_SUCCESS_COUNT', 'Success Count', 'interaction'),
    ('Interaction_ERROR_COUNT', 'Error Count', 'interaction'),
    ('Interaction_ERROR_DISTINCT_USERS', 'Distinct Users with Errors', 'interaction'),
    ('USER_CATEGORY_EXCELLENT', 'Excellent Users Count', 'interaction'),
    ('USER_CATEGORY_GOOD', 'Good Users Count', 'interaction'),
    ('USER_CATEGORY_AVERAGE', 'Average Users Count', 'interaction'),
    ('USER_CATEGORY_POOR', 'Poor Users Count', 'interaction'),
    ('CRASH_RATE', 'Crash Rate (%)', 'interaction'),
    ('ANR_RATE', 'ANR Rate (%)', 'interaction'),
    ('FROZEN_FRAME_RATE', 'Frozen Frame Rate (%)', 'interaction'),
    ('POOR_USER_RATE', 'Poor Users (%)', 'interaction'),
    ('AVERAGE_USER_RATE', 'Average Users (%)', 'interaction'),
    ('GOOD_USER_RATE', 'Good Users Rate (%)', 'interaction'),
    ('EXCELLENT_USER_RATE', 'Excellent Users (%)', 'interaction');

-- Insert APP_VITALS scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('CRASH_FREE_USERS_PERCENTAGE', 'CRASH FREE USERS PERCENTAGE value [0,1]', 'app_vitals'),
    ('CRASH_FREE_SESSIONS_PERCENTAGE', 'CRASH FREE SESSIONS PERCENTAGE value [0,1]', 'app_vitals'),
    ('CRASH_USERS', 'CRASH USERS value >= 0', 'app_vitals'),
    ('CRASH_SESSIONS', 'CRASH SESSIONS value >= 0', 'app_vitals'),
    ('ALL_USERS', 'ALL USERS value >= 0', 'app_vitals'),
    ('ALL_SESSIONS', 'ALL SESSIONS value >= 0', 'app_vitals'),
    ('ANR_FREE_USERS_PERCENTAGE', 'ANR FREE USERS PERCENTAGE value [0,1]', 'app_vitals'),
    ('ANR_FREE_SESSIONS_PERCENTAGE', 'ANR FREE SESSIONS PERCENTAGE value [0,1]', 'app_vitals'),
    ('ANR_USERS', 'ANR USERS value >= 0', 'app_vitals'),
    ('ANR_SESSIONS', 'ANR SESSIONS value >= 0', 'app_vitals'),
    ('NON_FATAL_FREE_USERS_PERCENTAGE', 'NON FATAL FREE USERS PERCENTAGE value [0,1]', 'app_vitals'),
    ('NON_FATAL_FREE_SESSIONS_PERCENTAGE', 'NON FATAL FREE SESSIONS PERCENTAGE value [0,1]', 'app_vitals'),
    ('NON_FATAL_USERS', 'NON FATAL USERS value >= 0', 'app_vitals'),
    ('NON_FATAL_SESSIONS', 'NON FATAL SESSIONS value >= 0', 'app_vitals');
    ('APP_VITALS_CRASH_FREE_USERS_PERCENTAGE', 'Crash-Free Users %', 'app_vitals'),
    ('APP_VITALS_CRASH_FREE_SESSIONS_PERCENTAGE', 'Crash-Free Sessions %', 'app_vitals'),
    ('APP_VITALS_CRASH_USERS', 'Crash Users Count', 'app_vitals'),
    ('APP_VITALS_CRASH_SESSIONS', 'Crash Sessions Count', 'app_vitals'),
    ('APP_VITALS_ALL_USERS', 'Total Users Count', 'app_vitals'),
    ('APP_VITALS_ALL_SESSIONS', 'Total Sessions Count', 'app_vitals'),
    ('APP_VITALS_ANR_FREE_USERS_PERCENTAGE', 'ANR-Free Users %', 'app_vitals'),
    ('APP_VITALS_ANR_FREE_SESSIONS_PERCENTAGE', 'ANR-Free Sessions %', 'app_vitals'),
    ('APP_VITALS_ANR_USERS', 'ANR Users Count', 'app_vitals'),
    ('APP_VITALS_ANR_SESSIONS', 'ANR Sessions Count', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_FREE_USERS_PERCENTAGE', 'Non-Fatal Free Users %', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_FREE_SESSIONS_PERCENTAGE', 'Non-Fatal Free Sessions %', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_USERS', 'Non-Fatal Users Count', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_SESSIONS', 'Non-Fatal Sessions Count', 'app_vitals');

-- Insert Screen scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('SCREEN_DAILY_USERS', 'Daily Users Count', 'screen'),
    ('SCREEN_ERROR_RATE', 'Error Rate (%)', 'screen'),
    ('SCREEN_TIME', 'Screen Time (s)', 'screen'),
    ('LOAD_TIME', 'Load Time (ms)', 'screen');

-- Insert network_api scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('NET_0', 'Connection Error Count', 'network_api'),
    ('NET_2XX', '2XX Success Count', 'network_api'),
    ('NET_3XX', '3XX Redirect Count', 'network_api'),
    ('NET_4XX', '4XX Client Error Count', 'network_api'),
    ('NET_5XX', '5XX Server Error Count', 'network_api'),
    ('NET_4XX_RATE', '4XX Error Rate (%)', 'network_api'),
    ('DURATION_P99', 'DURATION P99 value >= 0', 'network_api'),
    ('DURATION_P95', 'DURATION P95 value >= 0', 'network_api'),
    ('DURATION_P50', 'DURATION P50 value >= 0', 'network_api'),
    ('ERROR_RATE', 'ERROR RATE value [0,1]', 'network_api'),
    ('NET_5XX_RATE', '5XX Error Rate (%)', 'network_api');

-- Grant privileges (adjust as needed for your environment)
-- GRANT ALL PRIVILEGES ON pulse_db.* TO 'pulse_user'@'%' IDENTIFIED BY 'pulse_password';
-- FLUSH PRIVILEGES;

-- Display summary
SELECT 'Database initialization completed successfully!' AS status;
SELECT COUNT(*) AS total_tables FROM information_schema.tables WHERE table_schema = 'pulse_db';
