-- =============================================================================
-- Athena External Table DDL for Pulse OTEL Events
-- =============================================================================
-- One table per project. The bucket name encodes the project:
--   s3://pulse-otel-{project_id}/vector-logs/YYYY-MM-DD/HH/
--
-- Usage: Replace {PROJECT_ID} with the actual project ID before executing.
--   e.g. project_id = 42  →  bucket = pulse-otel-42
--
-- Partition layout (matches Vector key_prefix "vector-logs/%Y-%m-%d/%H/"):
--   date  STRING  (e.g. '2026-02-23')
--   hour  STRING  (e.g. '08')
--
-- Partition projection is enabled so Athena discovers partitions automatically
-- without running MSCK REPAIR TABLE.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS pulse_athena_db
  COMMENT 'Pulse OTEL event data stored in S3';

-- -----------------------------------------------------------------------------
-- Replace {PROJECT_ID} with the numeric project identifier before running.
-- The table is named otel_data_{PROJECT_ID} so each project gets its own
-- isolated table in the shared pulse_athena_db database.
--   e.g. project 42  →  pulse_athena_db.otel_data_42
-- -----------------------------------------------------------------------------

CREATE EXTERNAL TABLE IF NOT EXISTS pulse_athena_db.otel_data_{PROJECT_ID} (
  event_name              STRING,
  project_id              STRING,
  user_id                 STRING,
  installation_id         STRING,
  android_os_api_level    STRING,
  os_version              STRING,
  app_build_id            STRING,
  app_build_name          STRING,
  device_manufacturer     STRING,
  device_model_identifier STRING,
  os_name                 STRING,
  service_name            STRING,
  session_id              STRING,
  screen_name             STRING,
  network_carrier_mcc     STRING,
  network_carrier_mnc     STRING,
  network_carrier_icc     STRING,
  pulse_app_state         STRING,
  span_id                 STRING,
  trace_id                STRING,
  `timestamp`             TIMESTAMP,
  vector_observed_timestamp TIMESTAMP,
  scope_name              STRING,
  flags                   INT,
  props                   STRING
)
PARTITIONED BY (
  date  STRING,
  hour  STRING
)
ROW FORMAT SERDE
  'org.apache.hadoop.hive.ql.io.parquet.serde.ParquetHiveSerDe'
STORED AS INPUTFORMAT
  'org.apache.hadoop.hive.ql.io.parquet.MapredParquetInputFormat'
OUTPUTFORMAT
  'org.apache.hadoop.hive.ql.io.parquet.MapredParquetOutputFormat'
LOCATION 's3://pulse-otel-{PROJECT_ID}/vector-logs/'
TBLPROPERTIES (
  -- Partition projection: auto-discovers date/hour partitions without MSCK REPAIR
  'projection.enabled'            = 'true',

  'projection.date.type'          = 'date',
  'projection.date.format'        = 'yyyy-MM-dd',
  'projection.date.range'         = '2024-01-01,NOW',
  'projection.date.interval'      = '1',
  'projection.date.interval.unit' = 'DAYS',

  'projection.hour.type'          = 'integer',
  'projection.hour.digits'        = '2',
  'projection.hour.interval'      = '1',
  'projection.hour.range'         = '0,23',
  'storage.location.template'     = 's3://pulse-otel-{PROJECT_ID}/vector-logs/${date}/${hour}/',

  'classification'                = 'parquet'
);
