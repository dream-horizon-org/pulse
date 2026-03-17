-- ============================================================================
-- Pulse AI Agent — MySQL Seed Data
-- ============================================================================
-- Populates: interactions, alerts, alert_scope, alert_evaluation_history,
--            notification_channels, severity (if not already present)
--
-- Covers scenarios for all 6 Phase 1A tools:
--   Tool 1: query_interactions (list / detail / filters)
--   Tool 2: query_alerts (list / detail / history)
--
-- Usage:
--   mysql -h 127.0.0.1 -P 3307 -u root pulse_db < mysql_seed.sql
-- ============================================================================

USE pulse_db;

-- ---------------------------------------------------------------------------
-- 0. Ensure prerequisite rows exist (idempotent)
-- ---------------------------------------------------------------------------

-- Severity rows (the init script inserts 1/2/3; this is a safety net)
INSERT IGNORE INTO severity (severity_id, name, description) VALUES
  (1, 1, 'Critical'),
  (2, 2, 'Warning'),
  (3, 3, 'Info');

-- Notification channels (needed by alerts FK)
INSERT INTO notification_channels (notification_channel_id, tenant_id, name, type, config, is_active)
VALUES
  (100, 'default', 'eng-alerts-slack', 'slack', '{"webhook_url":"https://hooks.slack.com/services/T00/B00/xxx","channel":"#eng-alerts"}', TRUE),
  (101, 'default', 'oncall-email', 'email', '{"recipients":["oncall@dreamhorizon.com","em@dreamhorizon.com"]}', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name);


-- ---------------------------------------------------------------------------
-- 1. Interactions — 5 interactions with varied statuses and configs
-- ---------------------------------------------------------------------------
-- Scenarios covered:
--   • list: paginated listing with status/creator filters
--   • detail: full config with events, thresholds, blacklisted events
--   • filters: distinct statuses and createdBy values

-- ContestJoin — Running, healthy (Apdex ~0.85)
INSERT INTO interaction (tenant_id, name, status, details, is_archived, created_by, updated_by)
VALUES ('default', 'ContestJoin', 'RUNNING', '{
  "name": "ContestJoin",
  "description": "User joins a contest from the contest listing page",
  "uptimeLowerLimitInMs": 1000,
  "uptimeMidLimitInMs": 3000,
  "uptimeUpperLimitInMs": 8000,
  "thresholdInMs": 3000,
  "events": [
    {"name": "contest_list_viewed", "isBlacklisted": false, "props": []},
    {"name": "contest_details_loaded", "isBlacklisted": false, "props": []},
    {"name": "join_button_clicked", "isBlacklisted": false, "props": []},
    {"name": "contest_joined_success", "isBlacklisted": false, "props": []}
  ],
  "globalBlacklistedEvents": [
    {"name": "ad_interstitial_shown", "isBlacklisted": true, "props": []}
  ]
}', 0, 'navkash@dreamhorizon.com', 'navkash@dreamhorizon.com')
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- UserLogin — Running, degraded (Apdex ~0.65, elevated errors)
INSERT INTO interaction (tenant_id, name, status, details, is_archived, created_by, updated_by)
VALUES ('default', 'UserLogin', 'RUNNING', '{
  "name": "UserLogin",
  "description": "User login flow from splash screen to home",
  "uptimeLowerLimitInMs": 500,
  "uptimeMidLimitInMs": 2000,
  "uptimeUpperLimitInMs": 5000,
  "thresholdInMs": 2000,
  "events": [
    {"name": "splash_shown", "isBlacklisted": false, "props": []},
    {"name": "login_form_rendered", "isBlacklisted": false, "props": []},
    {"name": "credentials_submitted", "isBlacklisted": false, "props": [{"name": "auth_method", "value": "google_oauth", "operator": "EQUALS"}]},
    {"name": "home_screen_loaded", "isBlacklisted": false, "props": []}
  ],
  "globalBlacklistedEvents": []
}', 0, 'priya@dreamhorizon.com', 'navkash@dreamhorizon.com')
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- PaymentCheckout — Running, problematic (Apdex ~0.35, high errors, crashes)
INSERT INTO interaction (tenant_id, name, status, details, is_archived, created_by, updated_by)
VALUES ('default', 'PaymentCheckout', 'RUNNING', '{
  "name": "PaymentCheckout",
  "description": "End-to-end payment flow from cart to order confirmation",
  "uptimeLowerLimitInMs": 2000,
  "uptimeMidLimitInMs": 5000,
  "uptimeUpperLimitInMs": 12000,
  "thresholdInMs": 5000,
  "events": [
    {"name": "cart_reviewed", "isBlacklisted": false, "props": []},
    {"name": "payment_method_selected", "isBlacklisted": false, "props": []},
    {"name": "payment_processing", "isBlacklisted": false, "props": []},
    {"name": "order_confirmed", "isBlacklisted": false, "props": []}
  ],
  "globalBlacklistedEvents": [
    {"name": "coupon_modal_shown", "isBlacklisted": true, "props": []}
  ]
}', 0, 'navkash@dreamhorizon.com', 'priya@dreamhorizon.com')
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- ProfileLoad — Running, excellent (Apdex ~0.95)
INSERT INTO interaction (tenant_id, name, status, details, is_archived, created_by, updated_by)
VALUES ('default', 'ProfileLoad', 'RUNNING', '{
  "name": "ProfileLoad",
  "description": "User profile screen load",
  "uptimeLowerLimitInMs": 300,
  "uptimeMidLimitInMs": 1000,
  "uptimeUpperLimitInMs": 3000,
  "thresholdInMs": 1000,
  "events": [
    {"name": "profile_tab_clicked", "isBlacklisted": false, "props": []},
    {"name": "profile_data_loaded", "isBlacklisted": false, "props": []}
  ],
  "globalBlacklistedEvents": []
}', 0, 'arjun@dreamhorizon.com', 'arjun@dreamhorizon.com')
ON DUPLICATE KEY UPDATE status = VALUES(status);

-- FeedRefresh — Paused (no recent data expected)
INSERT INTO interaction (tenant_id, name, status, details, is_archived, created_by, updated_by)
VALUES ('default', 'FeedRefresh', 'PAUSED', '{
  "name": "FeedRefresh",
  "description": "Pull-to-refresh on the main feed",
  "uptimeLowerLimitInMs": 500,
  "uptimeMidLimitInMs": 1500,
  "uptimeUpperLimitInMs": 4000,
  "thresholdInMs": 1500,
  "events": [
    {"name": "pull_gesture_detected", "isBlacklisted": false, "props": []},
    {"name": "feed_content_refreshed", "isBlacklisted": false, "props": []}
  ],
  "globalBlacklistedEvents": []
}', 0, 'priya@dreamhorizon.com', 'priya@dreamhorizon.com')
ON DUPLICATE KEY UPDATE status = VALUES(status);


-- ---------------------------------------------------------------------------
-- 2. Alerts — 3 alerts with different states
-- ---------------------------------------------------------------------------
-- Scenarios covered:
--   • list: paginated listing
--   • detail: single alert with scopes, notification config
--   • history: evaluation history entries

-- Alert 1: APDEX drop on ContestJoin — NORMAL state
INSERT INTO alerts (id, tenant_id, name, description, scope, dimension_filter, condition_expression, severity_id, notification_channel_id, evaluation_period, evaluation_interval, created_by, is_active)
VALUES (200, 'default', 'ContestJoin Apdex Drop', 'Fires when ContestJoin Apdex drops below 0.7', 'interaction', NULL, 'APDEX < 0.7', 2, 100, 300, 60, 'navkash@dreamhorizon.com', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO alert_scope (id, alert_id, name, conditions, state, is_active)
VALUES (300, 200, 'ContestJoin', '[{"metric":"APDEX","operator":"LT","threshold":0.7}]', 'NORMAL', TRUE)
ON DUPLICATE KEY UPDATE state = VALUES(state);

-- Alert 2: Crash count on PaymentCheckout — FIRING state
INSERT INTO alerts (id, tenant_id, name, description, scope, dimension_filter, condition_expression, severity_id, notification_channel_id, evaluation_period, evaluation_interval, created_by, is_active)
VALUES (201, 'default', 'PaymentCheckout Crash Spike', 'Fires when PaymentCheckout has more than 5 crashes in evaluation window', 'interaction', NULL, 'CRASH > 5', 1, 100, 600, 120, 'priya@dreamhorizon.com', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO alert_scope (id, alert_id, name, conditions, state, is_active)
VALUES (301, 201, 'PaymentCheckout', '[{"metric":"CRASH","operator":"GT","threshold":5}]', 'FIRING', TRUE)
ON DUPLICATE KEY UPDATE state = VALUES(state);

-- Alert 3: Latency P99 on UserLogin — SNOOZED state (snoozed for 24 hours from now)
INSERT INTO alerts (id, tenant_id, name, description, scope, dimension_filter, condition_expression, severity_id, notification_channel_id, evaluation_period, evaluation_interval, created_by, updated_by, is_active, snoozed_from, snoozed_until, last_snoozed_at)
VALUES (202, 'default', 'UserLogin Latency Spike', 'Fires when UserLogin P99 latency exceeds 8000ms', 'interaction', NULL, 'DURATION_P99 > 8000', 2, 101, 300, 60, 'navkash@dreamhorizon.com', 'navkash@dreamhorizon.com', TRUE, NOW(), DATE_ADD(NOW(), INTERVAL 24 HOUR), NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO alert_scope (id, alert_id, name, conditions, state, is_active)
VALUES (302, 202, 'UserLogin', '[{"metric":"DURATION_P99","operator":"GT","threshold":8000}]', 'NORMAL', TRUE)
ON DUPLICATE KEY UPDATE state = VALUES(state);


-- ---------------------------------------------------------------------------
-- 3. Alert Evaluation History — sample evaluations for alert detail/history
-- ---------------------------------------------------------------------------
-- Scenarios covered:
--   • query_alerts(scope="history"): returns time-series of evaluation results

-- History for Alert 200 (ContestJoin Apdex) — 5 recent evaluations, all NORMAL
INSERT INTO alert_evaluation_history (scope_id, evaluation_result, state, evaluated_at) VALUES
  (300, '{"reading":"0.87","success_interaction_count":128,"error_interaction_count":22,"total_interaction_count":150,"evaluation_time":0.45,"min_success_interactions":10,"min_error_interactions":0,"min_total_interactions":20,"threshold":0.7}', 'NORMAL', DATE_SUB(NOW(), INTERVAL 5 MINUTE)),
  (300, '{"reading":"0.85","success_interaction_count":115,"error_interaction_count":20,"total_interaction_count":135,"evaluation_time":0.38,"min_success_interactions":10,"min_error_interactions":0,"min_total_interactions":20,"threshold":0.7}', 'NORMAL', DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
  (300, '{"reading":"0.86","success_interaction_count":100,"error_interaction_count":18,"total_interaction_count":118,"evaluation_time":0.42,"min_success_interactions":10,"min_error_interactions":0,"min_total_interactions":20,"threshold":0.7}', 'NORMAL', DATE_SUB(NOW(), INTERVAL 15 MINUTE)),
  (300, '{"reading":"0.83","success_interaction_count":92,"error_interaction_count":16,"total_interaction_count":108,"evaluation_time":0.39,"min_success_interactions":10,"min_error_interactions":0,"min_total_interactions":20,"threshold":0.7}', 'NORMAL', DATE_SUB(NOW(), INTERVAL 20 MINUTE)),
  (300, '{"reading":"0.88","success_interaction_count":140,"error_interaction_count":20,"total_interaction_count":160,"evaluation_time":0.41,"min_success_interactions":10,"min_error_interactions":0,"min_total_interactions":20,"threshold":0.7}', 'NORMAL', DATE_SUB(NOW(), INTERVAL 25 MINUTE));

-- History for Alert 201 (PaymentCheckout Crash) — 3 evaluations, last one FIRING
INSERT INTO alert_evaluation_history (scope_id, evaluation_result, state, evaluated_at) VALUES
  (301, '{"reading":"8","success_interaction_count":35,"error_interaction_count":45,"total_interaction_count":80,"evaluation_time":0.52,"min_success_interactions":10,"min_error_interactions":0,"min_total_interactions":20,"threshold":5}', 'FIRING', DATE_SUB(NOW(), INTERVAL 2 MINUTE)),
  (301, '{"reading":"6","success_interaction_count":38,"error_interaction_count":42,"total_interaction_count":80,"evaluation_time":0.48,"min_success_interactions":10,"min_error_interactions":0,"min_total_interactions":20,"threshold":5}', 'FIRING', DATE_SUB(NOW(), INTERVAL 4 MINUTE)),
  (301, '{"reading":"3","success_interaction_count":40,"error_interaction_count":40,"total_interaction_count":80,"evaluation_time":0.44,"min_success_interactions":10,"min_error_interactions":0,"min_total_interactions":20,"threshold":5}', 'NORMAL', DATE_SUB(NOW(), INTERVAL 6 MINUTE));

-- History for Alert 202 (UserLogin Latency) — 2 evaluations
INSERT INTO alert_evaluation_history (scope_id, evaluation_result, state, evaluated_at) VALUES
  (302, '{"reading":"7200","success_interaction_count":90,"error_interaction_count":30,"total_interaction_count":120,"evaluation_time":0.35,"min_success_interactions":10,"min_error_interactions":0,"min_total_interactions":20,"threshold":8000}', 'NORMAL', DATE_SUB(NOW(), INTERVAL 10 MINUTE)),
  (302, '{"reading":"6800","success_interaction_count":85,"error_interaction_count":35,"total_interaction_count":120,"evaluation_time":0.33,"min_success_interactions":10,"min_error_interactions":0,"min_total_interactions":20,"threshold":8000}', 'NORMAL', DATE_SUB(NOW(), INTERVAL 20 MINUTE));

-- ---------------------------------------------------------------------------
-- Done. Verify counts:
-- ---------------------------------------------------------------------------
SELECT 'Interactions' AS entity, COUNT(*) AS count FROM interaction WHERE is_archived = 0
UNION ALL
SELECT 'Alerts', COUNT(*) FROM alerts WHERE is_active = TRUE
UNION ALL
SELECT 'Alert Scopes', COUNT(*) FROM alert_scope WHERE is_active = TRUE
UNION ALL
SELECT 'Eval History', COUNT(*) FROM alert_evaluation_history
UNION ALL
SELECT 'Notification Channels', COUNT(*) FROM notification_channels WHERE is_active = TRUE;
