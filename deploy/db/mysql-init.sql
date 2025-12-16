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
    ('APDEX', 'APDEX value [0,1]', 'interaction'),
    ('CRASH', 'CRASH value >= 0', 'interaction'),
    ('ANR', 'ANR value >= 0', 'interaction'),
    ('FROZEN_FRAME', 'FROZEN FRAME value >= 0', 'interaction'),
    ('ANALYSED_FRAME', 'ANALYSED FRAME value >= 0', 'interaction'),
    ('UNANALYSED_FRAME', 'UNANALYSED FRAME value >= 0', 'interaction'),
    ('DURATION_P99', 'DURATION P99 value >= 0', 'interaction'),
    ('DURATION_P95', 'DURATION P95 value >= 0', 'interaction'),
    ('DURATION_P50', 'DURATION P50 value >= 0', 'interaction'),
    ('ERROR_RATE', 'ERROR RATE value [0,1]', 'interaction'),
    ('Interaction_SUCCESS_COUNT', 'Interaction SUCCESS COUNT value >= 0', 'interaction'),
    ('Interaction_ERROR_COUNT', 'Interaction ERROR COUNT value >= 0', 'interaction'),
    ('Interaction_ERROR_DISTINCT_USERS', 'Interaction ERROR DISTINCT USERS value >= 0', 'interaction'),
    ('USER_CATEGORY_EXCELLENT', 'USER CATEGORY EXCELLENT value >= 0', 'interaction'),
    ('USER_CATEGORY_GOOD', 'USER CATEGORY GOOD value >= 0', 'interaction'),
    ('USER_CATEGORY_AVERAGE', 'USER CATEGORY AVERAGE value >= 0', 'interaction'),
    ('USER_CATEGORY_POOR', 'USER CATEGORY POOR value >= 0', 'interaction'),
    ('CRASH_RATE', 'CRASH RATE value [0,1]', 'interaction'),
    ('ANR_RATE', 'ANR RATE value [0,1]', 'interaction'),
    ('FROZEN_FRAME_RATE', 'FROZEN FRAME RATE value [0,1]', 'interaction'),
    ('POOR_USER_RATE', 'POOR USER RATE value [0,1]', 'interaction'),
    ('AVERAGE_USER_RATE', 'AVERAGE USER RATE value [0,1]', 'interaction'),
    ('GOOD_USER_RATE', 'GOOD USER RATE value [0,1]', 'interaction'),
    ('EXCELLENT_USER_RATE', 'EXCELLENT USER RATE value [0,1]', 'interaction');

-- Insert APP_VITALS scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('APP_VITALS_CRASH_FREE_USERS_PERCENTAGE', 'APP VITALS CRASH FREE USERS PERCENTAGE value [0,1]', 'app_vitals'),
    ('APP_VITALS_CRASH_FREE_SESSIONS_PERCENTAGE', 'APP VITALS CRASH FREE SESSIONS PERCENTAGE value [0,1]', 'app_vitals'),
    ('APP_VITALS_CRASH_USERS', 'APP VITALS CRASH USERS value >= 0', 'app_vitals'),
    ('APP_VITALS_CRASH_SESSIONS', 'APP VITALS CRASH SESSIONS value >= 0', 'app_vitals'),
    ('APP_VITALS_ALL_USERS', 'APP VITALS ALL USERS value >= 0', 'app_vitals'),
    ('APP_VITALS_ALL_SESSIONS', 'APP VITALS ALL SESSIONS value >= 0', 'app_vitals'),
    ('APP_VITALS_ANR_FREE_USERS_PERCENTAGE', 'APP VITALS ANR FREE USERS PERCENTAGE value [0,1]', 'app_vitals'),
    ('APP_VITALS_ANR_FREE_SESSIONS_PERCENTAGE', 'APP VITALS ANR FREE SESSIONS PERCENTAGE value [0,1]', 'app_vitals'),
    ('APP_VITALS_ANR_USERS', 'APP VITALS ANR USERS value >= 0', 'app_vitals'),
    ('APP_VITALS_ANR_SESSIONS', 'APP VITALS ANR SESSIONS value >= 0', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_FREE_USERS_PERCENTAGE', 'APP VITALS NON FATAL FREE USERS PERCENTAGE value [0,1]', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_FREE_SESSIONS_PERCENTAGE', 'APP VITALS NON FATAL FREE SESSIONS PERCENTAGE value [0,1]', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_USERS', 'APP VITALS NON FATAL USERS value >= 0', 'app_vitals'),
    ('APP_VITALS_NON_FATAL_SESSIONS', 'APP VITALS NON FATAL SESSIONS value >= 0', 'app_vitals');

-- Insert Screen scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('SCREEN_DAILY_USERS', 'SCREEN DAILY USERS value >= 0', 'screen'),
    ('SCREEN_ERROR_RATE', 'SCREEN ERROR RATE value [0,1]', 'screen'),
    ('SCREEN_TIME', 'SCREEN TIME value >= 0', 'screen');

-- Insert network_api scope metrics
INSERT INTO Alert_Metrics (name, label, scope) VALUES
    ('NET_0', 'NET 0 value >= 0', 'network_api'),
    ('NET_2XX', 'NET 2XX value >= 0', 'network_api'),
    ('NET_3XX', 'NET 3XX value >= 0', 'network_api'),
    ('NET_4XX', 'NET 4XX value >= 0', 'network_api'),
    ('NET_5XX', 'NET 5XX value >= 0', 'network_api'),
    ('NET_4XX_RATE', 'NET 4XX RATE value [0,1]', 'network_api'),
    ('NET_5XX_RATE', 'NET 5XX RATE value [0,1]', 'network_api');

-- Grant privileges (adjust as needed for your environment)
-- GRANT ALL PRIVILEGES ON pulse_db.* TO 'pulse_user'@'%' IDENTIFIED BY 'pulse_password';
-- FLUSH PRIVILEGES;

-- Display summary
SELECT 'Database initialization completed successfully!' AS status;
SELECT COUNT(*) AS total_tables FROM information_schema.tables WHERE table_schema = 'pulse_db';
