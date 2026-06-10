-- Seed demo funnel rows for local hybrid testing (project_id = demo-streaming, ids 1/3/4/5/7/8).
-- Synthetic data only — safe to re-run (upsert by primary key id).

USE pulse_db;

-- Older local volumes may predate funnel/journey tables in mysql-init.sql.
CREATE TABLE IF NOT EXISTS funnel (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        VARCHAR(64)  NOT NULL,
    name              VARCHAR(255) NOT NULL,
    description       TEXT         NULL,
    funnel_type       VARCHAR(32)  NOT NULL DEFAULT 'AUTO',
    step_order_type   VARCHAR(32)  NOT NULL DEFAULT 'ORDERED',
    steps_json        JSON         NOT NULL,
    window_seconds    BIGINT       NOT NULL DEFAULT 86400,
    mode              VARCHAR(32)  NOT NULL DEFAULT 'UNIQUE_USERS',
    filters_json      JSON         NULL,
    date_range        INT          NULL DEFAULT 7,
    start_time        TIMESTAMP    NULL,
    end_time          TIMESTAMP    NULL,
    expiry            TIMESTAMP    NULL,
    created_at        TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by        VARCHAR(255) NULL,
    INDEX idx_funnel_project (project_id),
    INDEX idx_funnel_updated (updated_at),
    INDEX idx_funnel_project_updated (project_id, updated_at),
    FULLTEXT INDEX idx_funnel_name_fts (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Demo aggregate project (project_id "demo-streaming").
INSERT INTO projects (project_id, tenant_id, name, description, slug, is_active, created_by)
VALUES ('demo-streaming', 'demo-streaming', 'Demo Streaming', 'Demo streaming aggregate project', 'demo-streaming', TRUE, 'system')
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  tenant_id = VALUES(tenant_id),
  is_active = TRUE;

INSERT INTO tenants (tenant_id, name, description, is_active, gcp_tenant_id, domain_name)
VALUES ('demo-streaming', 'Demo Streaming', 'Demo sports streaming platform', TRUE, 'demo-streaming-1', 'demo-streaming.example.com')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO tenants (tenant_id, name, description, is_active)
VALUES ('DemoStore', 'Demo Store', 'Demo store tenant (local seed)', TRUE)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO projects (project_id, tenant_id, name, description, slug, is_active, created_by)
VALUES (
  'DemoStoreApp-0a1b2c3d',
  'DemoStore',
  'Demo Store App',
  'Demo store mobile app (local seed)',
  'demo-store-app',
  TRUE,
  'system'
)
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO funnel (
  id, project_id, name, description, funnel_type, step_order_type, steps_json,
  window_seconds, mode, filters_json, date_range, start_time, end_time, expiry,
  created_at, updated_at, created_by
) VALUES
(
  1, 'demo-streaming', 'MatchCardToMatchDetail', NULL, 'AUTO', 'ORDERED',
  CAST('[{"eventName":"MatchCardClicked"},{"eventName":"MatchPageLoaded"},{"eventName":"ClickedBuyPass"},{"eventName":"OrderInitiated"},{"eventName":"OrderSuccessful"}]' AS JSON),
  86400, 'UNIQUE_USERS', NULL, 7, NULL, NULL, '2026-05-30 18:30:00',
  '2026-04-28 08:45:25', '2026-04-28 08:45:25', 'analyst@example.com'
),
(
  3, 'demo-streaming', 'HomeLoadedToSearchIconDisplayed', 'HomeLoadedToSearchIconDisplayed', 'ONCE', 'ORDERED',
  CAST('[{"eventName":"HomeLoaded"},{"eventName":"SearchIconDisplayed"}]' AS JSON),
  86400, 'UNIQUE_USERS', NULL, 7, '2026-05-15 18:30:00', '2026-05-30 18:30:00', NULL,
  '2026-04-29 15:47:48', '2026-05-19 18:10:47', 'pm@example.com'
),
(
  4, 'demo-streaming', 'PaymentFunnel', NULL, 'AUTO', 'ORDERED',
  CAST('[{"eventName":"OrderInitiated"},{"eventName":"OrderSuccessful"}]' AS JSON),
  86400, 'UNIQUE_USERS', NULL, 1, NULL, NULL, '2026-05-30 18:30:00',
  '2026-04-30 07:39:45', '2026-05-20 11:00:22', 'analyst@example.com'
),
(
  5, 'demo-streaming', 'SectionContentClickedToPLAY', NULL, 'AUTO', 'ORDERED',
  CAST('[{"eventName":"SectionContentClicked"},{"eventName":"PLAY"}]' AS JSON),
  86400, 'UNIQUE_USERS', NULL, 7, NULL, NULL, NULL,
  '2026-04-30 08:21:03', '2026-05-14 09:36:55', 'analyst@example.com'
),
(
  7, 'demo-streaming', 'RevenueFunnelTesting', 'Revenue funnel analysis', 'ONCE', 'ORDERED',
  CAST('[{"eventName":"paymentSdkInit"},{"eventName":"add_to_cart"},{"eventName":"PaymentCompleteActionStart"},{"eventName":"LogPaymentCompleteActionEnd"},{"eventName":"OrderSuccessful"}]' AS JSON),
  86400, 'UNIQUE_USERS', NULL, 7, '2026-04-14 18:30:00', '2026-05-14 18:30:00', NULL,
  '2026-05-19 15:37:24', '2026-05-19 15:37:24', 'growth@example.com'
),
(
  8, 'DemoStoreApp-0a1b2c3d', 'Checkout', 'checkout funnel', 'AUTO', 'ORDERED',
  CAST('[{"eventName":"view_cart"},{"eventName":"begin_checkout"},{"eventName":"purchase"}]' AS JSON),
  86400, 'UNIQUE_USERS', NULL, 7, NULL, NULL, '2026-05-30 18:30:00',
  '2026-05-22 07:35:26', '2026-05-22 07:35:26', 'store-admin@example.com'
)
ON DUPLICATE KEY UPDATE
  project_id = VALUES(project_id),
  name = VALUES(name),
  description = VALUES(description),
  funnel_type = VALUES(funnel_type),
  step_order_type = VALUES(step_order_type),
  steps_json = VALUES(steps_json),
  window_seconds = VALUES(window_seconds),
  mode = VALUES(mode),
  filters_json = VALUES(filters_json),
  date_range = VALUES(date_range),
  start_time = VALUES(start_time),
  end_time = VALUES(end_time),
  expiry = VALUES(expiry),
  updated_at = VALUES(updated_at),
  created_by = VALUES(created_by);

ALTER TABLE funnel AUTO_INCREMENT = 9;
