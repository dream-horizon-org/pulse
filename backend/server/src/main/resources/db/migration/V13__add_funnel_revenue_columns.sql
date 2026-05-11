-- =============================================================================
-- Adds optional revenue configuration to funnel definitions.
-- Enables per-step revenue / AOV / lost-revenue computation by the Spark and
-- ClickHouse funnel compute paths. Existing funnels remain unaffected — when
-- revenue_attribute is null, the compute paths emit NULL for all revenue cols.
-- =============================================================================
ALTER TABLE funnel
    ADD COLUMN revenue_attribute  VARCHAR(128) NULL
        COMMENT 'Optional key inside event attributes carrying the numeric order value (e.g. order.value)' AFTER date_range,
    ADD COLUMN revenue_step_index INT          NULL
        COMMENT 'Optional 0-based step index of the order/purchase event; defaults to last step when null' AFTER revenue_attribute,
    ADD COLUMN currency           VARCHAR(8)   NULL
        COMMENT 'Display-only ISO-4217 currency code (e.g. INR, USD)' AFTER revenue_step_index;
