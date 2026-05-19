-- ============================================================================
-- default-project — critical interaction seeds (single canonical copy).
-- Do not duplicate in deploy/db or backend/db/*/mysql-init.sql — those SOURCE this file (Docker).
--
--   interaction_id 1..5  — ecommerce demo; matches
--   pulse-web-otel/examples/ecommerce-demo/public/interaction-config.mock.json
--   BasicInteraction + FullShopping — legacy samples (auto ids 6, 7)
--   interaction_id 100..116 — INT-P / lottery-demo + SDK auto-events
--   interaction_id 201..554 etc. — Next.js demo E2E mirrors; matches
--   pulse-web-otel/examples/nextjs-demo/e2e/nextjs-demo.spec.ts
--   (ID map: 501/502 click-bridge, 551 single-event, 554 apdex excellent,
--   544 user mid, 545 middle-required; context 301 / reverse network 304;
--   branch 414 = E125; overlap B = 430.)
-- ============================================================================

INSERT INTO interaction (interaction_id, project_id, name, status, details, is_archived, created_by, updated_by)
VALUES
(1, 'default-project', 'Checkout Happy Path', 'RUNNING', JSON_OBJECT(
    'description', 'Checkout Happy Path',
    'thresholdInMs', 3000,
    'uptimeLowerLimitInMs', 700,
    'uptimeMidLimitInMs', 1400,
    'uptimeUpperLimitInMs', 2500,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'checkout_step_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'checkout_step_2', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'checkout_step_3', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY(
        JSON_OBJECT('name', 'ad_impression', 'props', JSON_ARRAY(), 'isBlacklisted', true)
    )
), 0, 'system', 'system'),

(2, 'default-project', 'Cart Open To Checkout Click', 'RUNNING', JSON_OBJECT(
    'description', 'Cart Open To Checkout Click',
    'thresholdInMs', 2500,
    'uptimeLowerLimitInMs', 500,
    'uptimeMidLimitInMs', 1000,
    'uptimeUpperLimitInMs', 1800,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'cart_open', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT(
            'name', 'cart_checkout_click',
            'props', JSON_ARRAY(
                JSON_OBJECT('name', 'item_count', 'value', '0', 'operator', 'NOTEQUALS')
            ),
            'isBlacklisted', false
        )
    ),
    'globalBlacklistedEvents', JSON_ARRAY(
        JSON_OBJECT('name', 'device.crash', 'props', JSON_ARRAY(), 'isBlacklisted', true)
    )
), 0, 'system', 'system'),

(3, 'default-project', 'Product List Quick Add', 'RUNNING', JSON_OBJECT(
    'description', 'Product List Quick Add',
    'thresholdInMs', 2000,
    'uptimeLowerLimitInMs', 350,
    'uptimeMidLimitInMs', 900,
    'uptimeUpperLimitInMs', 1600,
    'events', JSON_ARRAY(
        JSON_OBJECT(
            'name', 'product_item_visible',
            'props', JSON_ARRAY(
                JSON_OBJECT('name', 'source', 'value', 'product_list', 'operator', 'EQUALS')
            ),
            'isBlacklisted', false
        ),
        JSON_OBJECT(
            'name', 'add_to_cart',
            'props', JSON_ARRAY(
                JSON_OBJECT('name', 'source', 'value', 'product_list', 'operator', 'EQUALS')
            ),
            'isBlacklisted', false
        )
    ),
    'globalBlacklistedEvents', JSON_ARRAY(
        JSON_OBJECT('name', 'error_demo_throw_uncaught', 'props', JSON_ARRAY(), 'isBlacklisted', true)
    )
), 0, 'system', 'system'),

(4, 'default-project', 'Product Detail Add To Cart', 'RUNNING', JSON_OBJECT(
    'description', 'Product Detail Add To Cart',
    'thresholdInMs', 3500,
    'uptimeLowerLimitInMs', 800,
    'uptimeMidLimitInMs', 1500,
    'uptimeUpperLimitInMs', 2800,
    'events', JSON_ARRAY(
        JSON_OBJECT(
            'name', 'product_detail_open',
            'props', JSON_ARRAY(
                JSON_OBJECT('name', 'path', 'value', '/products/', 'operator', 'STARTSWITH')
            ),
            'isBlacklisted', false
        ),
        JSON_OBJECT(
            'name', 'add_to_cart',
            'props', JSON_ARRAY(
                JSON_OBJECT('name', 'source', 'value', 'product_detail', 'operator', 'EQUALS')
            ),
            'isBlacklisted', false
        )
    ),
    'globalBlacklistedEvents', JSON_ARRAY(
        JSON_OBJECT('name', 'ad_impression', 'props', JSON_ARRAY(), 'isBlacklisted', true)
    )
), 0, 'system', 'system'),

(5, 'default-project', 'Cart Remove Item Then Checkout', 'RUNNING', JSON_OBJECT(
    'description', 'Cart Remove Item Then Checkout',
    'thresholdInMs', 2600,
    'uptimeLowerLimitInMs', 600,
    'uptimeMidLimitInMs', 1200,
    'uptimeUpperLimitInMs', 2100,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'cart_remove_item', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT(
            'name', 'cart_checkout_click',
            'props', JSON_ARRAY(
                JSON_OBJECT('name', 'item_count', 'value', '0', 'operator', 'NOTEQUALS')
            ),
            'isBlacklisted', false
        )
    ),
    'globalBlacklistedEvents', JSON_ARRAY(
        JSON_OBJECT('name', 'device.crash', 'props', JSON_ARRAY(), 'isBlacklisted', true)
    )
), 0, 'system', 'system');

INSERT INTO interaction (project_id, name, status, details, is_archived, created_by, updated_by)
VALUES
('default-project', 'BasicInteraction', 'RUNNING', JSON_OBJECT(
    'description', 'NewInteraction',
    'uptimeLowerLimitInMs', 16,
    'uptimeMidLimitInMs', 50,
    'uptimeUpperLimitInMs', 100,
    'thresholdInMs', 20000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'Go shopping', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'Telescope selected', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

('default-project', 'FullShopping', 'RUNNING', JSON_OBJECT(
    'description', 'FullShopping',
    'uptimeLowerLimitInMs', 16,
    'uptimeMidLimitInMs', 50,
    'uptimeUpperLimitInMs', 100,
    'thresholdInMs', 20000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'Go shopping', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'Telescope selected', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'Add to cart', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system')
ON DUPLICATE KEY UPDATE name = name;

-- INT-P test configs (interaction_id 100..116) on project default-project
-- Events map to real Pulse.trackEvent() calls in lottery-demo + SDK auto-events.
-- Same INSERT continues below with Next.js demo rows (after a section comment).
-- ============================================================================
INSERT INTO interaction (interaction_id, project_id, name, status, details, is_archived, created_by, updated_by)
VALUES
-- INT-P01/P08/P13/P22/P27/P30/P31/P32/P38/P39 — basic 2-step happy path
(100, 'default-project', 'INT-P01 Basic Happy Path', 'RUNNING', JSON_OBJECT(
    'description', 'lottery_card_clicked → ticket_purchased. 60s threshold, 60s lower → always Excellent unless timed out.',
    'thresholdInMs', 60000,
    'uptimeLowerLimitInMs', 60000,
    'uptimeMidLimitInMs', 120000,
    'uptimeUpperLimitInMs', 180000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P02 — Excellent apdex (t < lower limit; lower=300s so any human speed is Excellent)
(101, 'default-project', 'INT-P02 Excellent Apdex', 'RUNNING', JSON_OBJECT(
    'description', 'lottery_card_clicked → ticket_purchased. lower=300s so always Excellent.',
    'thresholdInMs', 600000,
    'uptimeLowerLimitInMs', 300000,
    'uptimeMidLimitInMs', 600000,
    'uptimeUpperLimitInMs', 900000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P03 — Good/Tolerable apdex (lower=100ms < human speed < mid=300s)
(102, 'default-project', 'INT-P03 Good Apdex', 'RUNNING', JSON_OBJECT(
    'description', 'lottery_card_clicked → ticket_purchased. lower=100ms, mid=300s → any normal action lands in Good.',
    'thresholdInMs', 600000,
    'uptimeLowerLimitInMs', 100,
    'uptimeMidLimitInMs', 300000,
    'uptimeUpperLimitInMs', 600000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P04 — Poor apdex (t >= mid=500ms; any human action takes >500ms → Poor)
(103, 'default-project', 'INT-P04 Poor Apdex', 'RUNNING', JSON_OBJECT(
    'description', 'lottery_card_clicked → ticket_purchased. lower=100ms, mid=500ms → any normal action lands in Poor.',
    'thresholdInMs', 600000,
    'uptimeLowerLimitInMs', 100,
    'uptimeMidLimitInMs', 500,
    'uptimeUpperLimitInMs', 1000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P05 — Timeout (trigger otp_sent, wait 10s without completing)
(104, 'default-project', 'INT-P05 Timeout Flow', 'RUNNING', JSON_OBJECT(
    'description', 'otp_sent → otp_verified. 10s threshold. Trigger otp_sent then wait → timeout.',
    'thresholdInMs', 10000,
    'uptimeLowerLimitInMs', 5000,
    'uptimeMidLimitInMs', 8000,
    'uptimeUpperLimitInMs', 10000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'otp_sent', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'otp_verified', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P06 — Event-level blacklist (middle event skipped)
(105, 'default-project', 'INT-P06 Event Blacklist', 'RUNNING', JSON_OBJECT(
    'description', '3-step: card→choose_screen(blacklisted)→purchased. Middle event is skipped; flow still completes.',
    'thresholdInMs', 60000,
    'uptimeLowerLimitInMs', 60000,
    'uptimeMidLimitInMs', 120000,
    'uptimeUpperLimitInMs', 180000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'ticket_choose_screen_open', 'props', JSON_ARRAY(), 'isBlacklisted', true),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P07 — Global blacklist (ticket_choose_screen_open does not reset tracker)
(106, 'default-project', 'INT-P07 Global Blacklist', 'RUNNING', JSON_OBJECT(
    'description', 'card→purchased with ticket_choose_screen_open globally blacklisted. Interleaving it does not reset.',
    'thresholdInMs', 60000,
    'uptimeLowerLimitInMs', 60000,
    'uptimeMidLimitInMs', 120000,
    'uptimeUpperLimitInMs', 180000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY(
        JSON_OBJECT('name', 'ticket_choose_screen_open', 'props', JSON_ARRAY(), 'isBlacklisted', true)
    )
), 0, 'system', 'system'),

-- INT-P11/P12 — Error flow (purchase_failed → is_error=true span)
(107, 'default-project', 'INT-P11 Error Flow', 'RUNNING', JSON_OBJECT(
    'description', 'lottery_card_clicked → purchase_failed. Emits error interaction span.',
    'thresholdInMs', 60000,
    'uptimeLowerLimitInMs', 60000,
    'uptimeMidLimitInMs', 120000,
    'uptimeUpperLimitInMs', 180000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'purchase_failed', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P14 — screen_load auto-event fed into trackEvent (step 2 = screen_load)
(108, 'default-project', 'INT-P14 ScreenLoad as Step', 'RUNNING', JSON_OBJECT(
    'description', 'lottery_card_clicked → screen_load. screen_load auto-fires when next page loads.',
    'thresholdInMs', 30000,
    'uptimeLowerLimitInMs', 30000,
    'uptimeMidLimitInMs', 60000,
    'uptimeUpperLimitInMs', 120000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'screen_load', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P15 — app.click auto-event fed into trackEvent (step 2 = app.click)
(109, 'default-project', 'INT-P15 AppClick as Step', 'RUNNING', JSON_OBJECT(
    'description', 'lottery_card_clicked → app.click. Any tap after step 1 completes the flow.',
    'thresholdInMs', 30000,
    'uptimeLowerLimitInMs', 30000,
    'uptimeMidLimitInMs', 60000,
    'uptimeUpperLimitInMs', 120000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'app.click', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P18/P19 Concurrent A — both A and B share same trigger; verify names/ids on mid-flow spans
(110, 'default-project', 'INT-P18/19 Concurrent A', 'RUNNING', JSON_OBJECT(
    'description', 'Concurrent flow A: card→purchased. Pair with flow B (id=111). Check pulse.interaction.names on mid-flow network spans.',
    'thresholdInMs', 60000,
    'uptimeLowerLimitInMs', 60000,
    'uptimeMidLimitInMs', 120000,
    'uptimeUpperLimitInMs', 180000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P18/P19 Concurrent B (same events as A, different config/name)
(111, 'default-project', 'INT-P18/19 Concurrent B', 'RUNNING', JSON_OBJECT(
    'description', 'Concurrent flow B: card→purchased. Pair with flow A (id=110).',
    'thresholdInMs', 60000,
    'uptimeLowerLimitInMs', 60000,
    'uptimeMidLimitInMs', 120000,
    'uptimeUpperLimitInMs', 180000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P20 Distinct Concurrent B (different first event from flow A id=110)
(112, 'default-project', 'INT-P20 Concurrent Distinct B', 'RUNNING', JSON_OBJECT(
    'description', 'Distinct concurrent flow B: cart_checkout_click→purchased. Pair with flow A (id=110).',
    'thresholdInMs', 60000,
    'uptimeLowerLimitInMs', 60000,
    'uptimeMidLimitInMs', 120000,
    'uptimeUpperLimitInMs', 180000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'cart_checkout_click', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P21 — network.200 auto-event as terminal step
(113, 'default-project', 'INT-P21 Network as Step', 'RUNNING', JSON_OBJECT(
    'description', 'lottery_card_clicked → network.200. Any successful fetch after step 1 completes the flow.',
    'thresholdInMs', 10000,
    'uptimeLowerLimitInMs', 10000,
    'uptimeMidLimitInMs', 20000,
    'uptimeUpperLimitInMs', 30000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'network.200', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P23 — prop filter (status=active must match on lottery_card_clicked)
(114, 'default-project', 'INT-P23 Prop Filter', 'RUNNING', JSON_OBJECT(
    'description', 'card[status=EQUALS=active]→purchased. Click active lottery → flow starts. Inactive → no flow.',
    'thresholdInMs', 60000,
    'uptimeLowerLimitInMs', 60000,
    'uptimeMidLimitInMs', 120000,
    'uptimeUpperLimitInMs', 180000,
    'events', JSON_ARRAY(
        JSON_OBJECT(
            'name', 'lottery_card_clicked',
            'props', JSON_ARRAY(
                JSON_OBJECT('name', 'status', 'value', 'active', 'operator', 'EQUALS')
            ),
            'isBlacklisted', false
        ),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P27/P28/P36/P37/P38 — 3-step flow with timing verification
(115, 'default-project', 'INT-P36/37/38 3-Step Timing', 'RUNNING', JSON_OBJECT(
    'description', '3-step: card→checkout→purchased. Verify OTel span events + span start/end times.',
    'thresholdInMs', 60000,
    'uptimeLowerLimitInMs', 60000,
    'uptimeMidLimitInMs', 120000,
    'uptimeUpperLimitInMs', 180000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'cart_checkout_click', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'ticket_purchased', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- INT-P29 — screen_session auto-event as terminal step
(116, 'default-project', 'INT-P29 ScreenSession as Step', 'RUNNING', JSON_OBJECT(
    'description', 'lottery_card_clicked → screen_session. Navigate away after step 1 → screen_session fires → flow completes.',
    'thresholdInMs', 60000,
    'uptimeLowerLimitInMs', 60000,
    'uptimeMidLimitInMs', 120000,
    'uptimeUpperLimitInMs', 180000,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'lottery_card_clicked', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'screen_session', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

-- ─── Next.js demo (nextjs-demo E2E) — same project, unique interaction_ids ───
(501, 'default-project', 'Product Click Flow', 'RUNNING', JSON_OBJECT(
    'description', 'Product Click Flow',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 360,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'app.widget.click', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(502, 'default-project', 'Product Viewed Flow', 'RUNNING', JSON_OBJECT(
    'description', 'Product Viewed Flow',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 360,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'product_viewed', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(201, 'default-project', 'Marker NonFatal Flow', 'RUNNING', JSON_OBJECT(
    'description', 'Marker NonFatal Flow',
    'thresholdInMs', 800,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'marker_step_a', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'marker_step_b', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(202, 'default-project', 'Marker Crash Flow', 'RUNNING', JSON_OBJECT(
    'description', 'Marker Crash Flow',
    'thresholdInMs', 800,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'crash_step_a', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'crash_step_b', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(203, 'default-project', 'Clean Flow', 'RUNNING', JSON_OBJECT(
    'description', 'Clean Flow',
    'thresholdInMs', 800,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'clean_step_a', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'clean_step_b', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(301, 'default-project', 'NX Context Stamp Flow', 'RUNNING', JSON_OBJECT(
    'description', 'NX Context Stamp Flow',
    'thresholdInMs', 5000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_ctx_step_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_ctx_step_2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(302, 'default-project', 'NX Context No-Stamp Flow', 'RUNNING', JSON_OBJECT(
    'description', 'NX Context No-Stamp Flow',
    'thresholdInMs', 5000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_nostamp_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_nostamp_2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(303, 'default-project', 'NX Reverse Screen Flow', 'RUNNING', JSON_OBJECT(
    'description', 'NX Reverse Screen Flow',
    'thresholdInMs', 5000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_checkout_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'screen_load', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(304, 'default-project', 'NX Reverse Network Flow', 'RUNNING', JSON_OBJECT(
    'description', 'NX Reverse Network Flow',
    'thresholdInMs', 5000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_net_step_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'network.200', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(551, 'default-project', 'NX Single Event', 'RUNNING', JSON_OBJECT(
    'description', 'NX Single Event',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_single', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(554, 'default-project', 'NX Apdex Excellent', 'RUNNING', JSON_OBJECT(
    'description', 'NX Apdex Excellent',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_ax_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_ax_2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(305, 'default-project', 'NX Apdex Good', 'RUNNING', JSON_OBJECT(
    'description', 'NX Apdex Good',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_ag_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_ag_2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(306, 'default-project', 'NX Apdex Average', 'RUNNING', JSON_OBJECT(
    'description', 'NX Apdex Average',
    'thresholdInMs', 1200,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_aa_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_aa_2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(307, 'default-project', 'NX Apdex Poor', 'RUNNING', JSON_OBJECT(
    'description', 'NX Apdex Poor',
    'thresholdInMs', 1500,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_ap_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_ap_2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(308, 'default-project', 'NX Repeatable', 'RUNNING', JSON_OBJECT(
    'description', 'NX Repeatable',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_rep', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(310, 'default-project', 'NX Equals Match', 'RUNNING', JSON_OBJECT(
    'description', 'NX Equals Match',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT(
            'name', 'nx_eq_event',
            'props', JSON_ARRAY(JSON_OBJECT('name', 'tier', 'value', 'gold', 'operator', 'EQUALS')),
            'isBlacklisted', false
        )
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(311, 'default-project', 'NX Contains Match', 'RUNNING', JSON_OBJECT(
    'description', 'NX Contains Match',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT(
            'name', 'nx_ct_event',
            'props', JSON_ARRAY(JSON_OBJECT('name', 'label', 'value', 'cart', 'operator', 'CONTAINS')),
            'isBlacklisted', false
        )
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(312, 'default-project', 'NX StartsWith Match', 'RUNNING', JSON_OBJECT(
    'description', 'NX StartsWith Match',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT(
            'name', 'nx_sw_event',
            'props', JSON_ARRAY(JSON_OBJECT('name', 'screen', 'value', 'product', 'operator', 'STARTSWITH')),
            'isBlacklisted', false
        )
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(319, 'default-project', 'NX Sequence Violation', 'RUNNING', JSON_OBJECT(
    'description', 'NX Sequence Violation',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_v1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_v2', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_v3', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(321, 'default-project', 'NX Timeout', 'RUNNING', JSON_OBJECT(
    'description', 'NX Timeout',
    'thresholdInMs', 700,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_t1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_t2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(322, 'default-project', 'NX Global Blacklist', 'RUNNING', JSON_OBJECT(
    'description', 'NX Global Blacklist',
    'thresholdInMs', 2000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_b1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_b2', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_b3', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_noise', 'props', JSON_ARRAY(), 'isBlacklisted', true)
    )
), 0, 'system', 'system'),

(340, 'default-project', 'NX Complete Time', 'RUNNING', JSON_OBJECT(
    'description', 'NX Complete Time',
    'thresholdInMs', 2000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_p40_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_p40_2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(413, 'default-project', 'Branch E123', 'RUNNING', JSON_OBJECT(
    'description', 'Branch E123',
    'thresholdInMs', 5000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_e1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_e2', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_e3', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(414, 'default-project', 'Branch E125', 'RUNNING', JSON_OBJECT(
    'description', 'Branch E125',
    'thresholdInMs', 5000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_e1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_e2', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_e5', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(544, 'default-project', 'NX User Mid Flow', 'RUNNING', JSON_OBJECT(
    'description', 'NX User Mid Flow',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_user_a', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_user_b', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(420, 'default-project', 'NX Stage2 Violation', 'RUNNING', JSON_OBJECT(
    'description', 'NX Stage2 Violation',
    'thresholdInMs', 2000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_s1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_s2', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_s3', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(423, 'default-project', 'NX Local Blacklist', 'RUNNING', JSON_OBJECT(
    'description', 'NX Local Blacklist',
    'thresholdInMs', 1000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_bl_a', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_bl_blocked', 'props', JSON_ARRAY(), 'isBlacklisted', true),
        JSON_OBJECT('name', 'nx_bl_b', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(427, 'default-project', 'NX EQUALS No Match', 'RUNNING', JSON_OBJECT(
    'description', 'NX EQUALS No Match',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT(
            'name', 'nx_props_event',
            'props', JSON_ARRAY(JSON_OBJECT('name', 'plan', 'value', 'pro', 'operator', 'EQUALS')),
            'isBlacklisted', false
        )
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(428, 'default-project', 'NX Timestamp Order', 'RUNNING', JSON_OBJECT(
    'description', 'NX Timestamp Order',
    'thresholdInMs', 700,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_ts_a', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_ts_b', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(429, 'default-project', 'NX Overlap A', 'RUNNING', JSON_OBJECT(
    'description', 'NX Overlap A',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_start', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_finish_a', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(430, 'default-project', 'NX Overlap B', 'RUNNING', JSON_OBJECT(
    'description', 'NX Overlap B',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_start', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_finish_b', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(545, 'default-project', 'NX Middle Required', 'RUNNING', JSON_OBJECT(
    'description', 'NX Middle Required',
    'thresholdInMs', 1000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_mr_start', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_mr_middle', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_mr_end', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(431, 'default-project', 'NX Restart After Violation', 'RUNNING', JSON_OBJECT(
    'description', 'NX Restart After Violation',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_rv_first', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_rv_second', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(432, 'default-project', 'NX Multi Blacklist', 'RUNNING', JSON_OBJECT(
    'description', 'NX Multi Blacklist',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_mb_1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_mb_2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_mb_cancel', 'props', JSON_ARRAY(), 'isBlacklisted', true)
    )
), 0, 'system', 'system'),

(436, 'default-project', 'NX Valid Flow', 'RUNNING', JSON_OBJECT(
    'description', 'NX Valid Flow',
    'thresholdInMs', 600,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_valid_a', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_valid_b', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(437, 'default-project', 'NX Apdex Boundary Lower', 'RUNNING', JSON_OBJECT(
    'description', 'NX Apdex Boundary Lower',
    'thresholdInMs', 2000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 300,
    'uptimeUpperLimitInMs', 600,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_ab_a', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_ab_b', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(438, 'default-project', 'NX Apdex Boundary Upper', 'RUNNING', JSON_OBJECT(
    'description', 'NX Apdex Boundary Upper',
    'thresholdInMs', 2000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 300,
    'uptimeUpperLimitInMs', 600,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_au_a', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_au_b', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(439, 'default-project', 'NX Branch 39A', 'RUNNING', JSON_OBJECT(
    'description', 'NX Branch 39A',
    'thresholdInMs', 5000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_39_e1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_39_e2', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_39_e3', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(440, 'default-project', 'NX Branch 39B', 'RUNNING', JSON_OBJECT(
    'description', 'NX Branch 39B',
    'thresholdInMs', 5000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_39_e1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_39_e2', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_39_e5', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(509, 'default-project', 'NX Step Timestamps', 'RUNNING', JSON_OBJECT(
    'description', 'NX Step Timestamps',
    'thresholdInMs', 5000,
    'uptimeLowerLimitInMs', 120,
    'uptimeMidLimitInMs', 240,
    'uptimeUpperLimitInMs', 420,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_ts_step1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_ts_step2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system'),

(541, 'default-project', 'NX Error Apdex', 'RUNNING', JSON_OBJECT(
    'description', 'NX Error Apdex',
    'thresholdInMs', 400,
    'uptimeLowerLimitInMs', 50,
    'uptimeMidLimitInMs', 100,
    'uptimeUpperLimitInMs', 150,
    'events', JSON_ARRAY(
        JSON_OBJECT('name', 'nx_err_step1', 'props', JSON_ARRAY(), 'isBlacklisted', false),
        JSON_OBJECT('name', 'nx_err_step2', 'props', JSON_ARRAY(), 'isBlacklisted', false)
    ),
    'globalBlacklistedEvents', JSON_ARRAY()
), 0, 'system', 'system')

ON DUPLICATE KEY UPDATE name = name;

ALTER TABLE interaction AUTO_INCREMENT = 600;
