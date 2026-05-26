package org.dreamhorizon.pulsespark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.dreamhorizon.pulsespark.model.FunnelDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.apache.spark.sql.functions.*;

public final class OtelS3Reader {

  private static final Logger log = LoggerFactory.getLogger(OtelS3Reader.class);

  private OtelS3Reader() {}

  /**
   * Read projection from the new OTel parquet schema (PascalCase columns shipped by the
   * pulse-s3-archiver). Each entry is {@code {parquetColumn, projectedAlias}}; aliases use
   * the lowercase names that downstream funnel / journey / event-catalog logic already
   * depends on, so flipping the storage layout doesn't ripple through the codebase.
   *
   * <p>Layout: {@code s3://pulse-otel-ingestion/<projectId>/otel_logs/year=YYYY/month=MM/day=DD/*.parquet}
   * (replacing the legacy {@code vector-logs/} sink).
   *
   * <p>Schema source: {@code backend/pulse-s3-archiver/src/main/resources/schemas/otel_logs.avsc}.
   */
  static final String[][] READ_COL_MAPPING = {
    {SparkConstants.RawColumns.EVENT_NAME,       SparkConstants.Columns.EVENT_NAME},
    {SparkConstants.RawColumns.PROJECT_ID,       SparkConstants.Columns.PROJECT_ID},
    {SparkConstants.RawColumns.SESSION_ID,       SparkConstants.Columns.SESSION_ID},
    // Timestamp is handled separately below — see buildReadExprs. The parquet-mr 1.14.1 writer
    // emits an avro `timestamp-micros` logical type that Spark 3.5.x sometimes decodes to
    // epoch zero (depending on session config). We bypass Spark's logical-type interpretation
    // entirely by reading the column as Long microseconds and rebuilding TimestampType ourselves.
    {SparkConstants.RawColumns.PULSE_TYPE,       SparkConstants.Columns.PULSE_TYPE},            // needed for the custom_event filter
    {SparkConstants.RawColumns.PLATFORM,         SparkConstants.Columns.OS_NAME},               // OTel `Platform` carries the OS name
    {SparkConstants.RawColumns.OS_VERSION,       SparkConstants.Columns.OS_VERSION},
    {SparkConstants.RawColumns.APP_VERSION,      SparkConstants.Columns.APP_BUILD_NAME},        // downstream "app_build_name" alias kept for catalog filters
    {SparkConstants.RawColumns.PLATFORM,         SparkConstants.Columns.DEVICE_MANUFACTURER},  // legacy distinction collapsed in OTel — same source
    {SparkConstants.RawColumns.DEVICE_MODEL,     SparkConstants.Columns.DEVICE_MODEL_IDENTIFIER},
    {SparkConstants.RawColumns.NETWORK_PROVIDER, SparkConstants.Columns.NETWORK_CARRIER_ICC},
    {SparkConstants.RawColumns.SCREEN_NAME,      SparkConstants.Columns.SCREEN_NAME},
    {SparkConstants.RawColumns.SERVICE_NAME,     SparkConstants.Columns.SERVICE_NAME},
  };

  /**
   * Explicit read schema for the otel_logs parquet partitions. Forces ALL columns to a known
   * type so Spark's parquet reader doesn't apply its own logical-type interpretation — in
   * particular {@code Timestamp} is read as raw {@link DataTypes#LongType LongType} (the
   * parquet column's physical type is {@code INT64} with avro logical
   * {@code timestamp-micros}). The parquet-mr 1.14.1 writer ↔ Spark 3.5.x vectorized reader
   * combination on EMR Serverless silently decodes the auto-inferred TimestampType to epoch 0
   * for every row; reading raw int64 and rebuilding the TimestampType in
   * {@link #buildReadExprs} avoids the bug entirely.
   *
   * <p>Columns the existing funnel / journey / event-catalog logic depends on are listed here.
   * Any partition file missing a column gets {@code NULL} for that column (no schema-merge
   * gymnastics needed).
   */
  static final org.apache.spark.sql.types.StructType READ_SCHEMA =
    new org.apache.spark.sql.types.StructType()
      .add(SparkConstants.RawColumns.EVENT_NAME,          DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.PROJECT_ID,          DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.SESSION_ID,          DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.PULSE_TYPE,          DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.PLATFORM,            DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.OS_VERSION,          DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.APP_VERSION,         DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.DEVICE_MODEL,        DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.NETWORK_PROVIDER,    DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.SCREEN_NAME,         DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.SERVICE_NAME,        DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.APP_INSTALLATION_ID, DataTypes.StringType, true)
      .add(SparkConstants.RawColumns.USER_ID,             DataTypes.StringType, true)
      // Read as raw Long micros — DO NOT change to TimestampType, that triggers the
      // parquet-mr 1.14.1 ↔ Spark 3.5 decode-to-zero bug.
      .add(SparkConstants.RawColumns.TIMESTAMP, DataTypes.LongType, true)
      // Custom event properties for step/global filter evaluation.
      // Avro map<string> → Parquet MAP → Spark MapType(StringType, StringType).
      .add(SparkConstants.RawColumns.LOG_ATTRIBUTES,
        org.apache.spark.sql.types.DataTypes.createMapType(
          DataTypes.StringType, DataTypes.StringType, true),
        true);

  /**
   * Sets the Spark session-level configuration needed to correctly read parquet files written
   * by parquet-mr 1.14.1 (the pulse-s3-archiver's Java writer).
   *
   * <p><b>Background.</b> Spark 3.5.6's <i>vectorized</i> parquet reader has a known
   * incompatibility with parquet-mr 1.14.1's encoding of
   * {@code TIMESTAMP(MICROS, isAdjustedToUTC=true)} columns: every value decodes to epoch 0
   * regardless of whether the caller requests {@link DataTypes#TimestampType TimestampType}
   * or {@link DataTypes#LongType LongType} via an explicit read schema. The non-vectorized
   * (row-based) reader handles the logical type correctly. Disabling the vectorized reader
   * here makes the timestamp filter actually filter on real timestamps instead of all-zeros.
   */
  public static void configureSparkForParquetMrTimestamps(SparkSession spark) {
    spark.conf().set(SparkConstants.SparkConfig.PARQUET_VECTORIZED_READER, "false");
    spark.conf().set(SparkConstants.SparkConfig.SESSION_TIME_ZONE, SparkConstants.SparkConfig.UTC);
    log.info("Spark parquet read config: vectorizedReader=false, timeZone=UTC " +
      "(workaround for parquet-mr 1.14.1 TIMESTAMP(MICROS) decode-to-zero bug in Spark 3.5.x)");
  }

  /**
   * Joins an S3 bucket prefix with project + table into a canonical
   * {@code s3a://<bucket>/<projectId>/<tableName>/} URL, normalising trailing/missing
   * slashes so it doesn't matter whether {@code s3BucketPrefix} ends with {@code /} or not.
   */
  public static String buildS3Base(String s3BucketPrefix, String projectId, String tableName) {
    String prefix = s3BucketPrefix == null ? "" : s3BucketPrefix;
    if (!prefix.isEmpty() && !prefix.endsWith("/")) {
      prefix = prefix + "/";
    }
    return SparkConstants.SparkConfig.S3A_PREFIX + prefix + projectId + "/" + tableName + "/";
  }

  /**
   * Reads parquet partitions over an inclusive date range. The new
   * pulse-s3-archiver layout partitions by {@code year=YYYY/month=MM/day=DD},
   * not by date-string folders or hour subdirs.
   */
  public static Dataset<Row> readS3ByDateRange(SparkSession spark, String s3BasePath,
                                               LocalDate startDate, LocalDate endDate) {
    Dataset<Row> combined = null;
    int attempted = 0, loaded = 0;
    for (var d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
      attempted++;
      var ds = loadPath(spark, s3BasePath + dayPartition(d));
      if (ds != null) {
        loaded++;
        combined = combined == null ? ds : combined.union(ds);
      }
    }
    if (combined == null) {
      log.warn("No S3 data for date range {} to {} (attempted={}, loaded={})",
        startDate, endDate, attempted, loaded);
    } else {
      log.info("Loaded S3 date range {} to {} (attempted={}, loaded={})",
        startDate, endDate, attempted, loaded);
    }
    return combined;
  }

  /**
   * Reads parquet for a sub-day window. The s3-archiver layout doesn't carry an hour
   * sub-folder (events have an {@code Hour} column instead), so this loads whole-day
   * partitions covering the window and relies on the downstream {@code Timestamp}
   * filter to narrow to the exact hour range.
   */
  public static Dataset<Row> readS3ByHours(SparkSession spark, String s3BasePath,
                                           LocalDateTime startDt, LocalDateTime endDt) {
    return readS3ByDateRange(spark, s3BasePath, startDt.toLocalDate(), endDt.toLocalDate());
  }

  /**
   * Projects the new OTel parquet schema (PascalCase columns) to the lowercase aliases
   * the funnel / journey / event-catalog logic depends on. Missing columns are filled with
   * {@code NULL} so partitions with schema drift still load via {@code mergeSchema}.
   *
   * <p><b>{@code user_id} alias = {@code AppInstallationId} directly.</b> Matches CH's
   * {@code ClickhouseAnalyticsQueryUtils.resolveMaterializedGroupKey} which uses
   * {@code AppInstallationId} for UNIQUE_USERS mode and explicitly skips {@code UserId}
   * (doc note: "not reliably populated across all SDK versions"). Early-lifecycle events
   * like HomeLoaded fire before the user is identified — {@code UserId} is empty for them
   * but {@code AppInstallationId} is always set by the SDK at init time.
   */
  static Column[] buildReadExprs(Dataset<Row> ds) {
    var schema = ds.schema();
    var available = new java.util.HashSet<>(java.util.Arrays.asList(ds.columns()));
    var cols = new ArrayList<Column>();
    for (var mapping : READ_COL_MAPPING) {
      String parquetCol = mapping[0];
      String alias = mapping[1];
      cols.add(available.contains(parquetCol)
        ? col(parquetCol).alias(alias)
        : lit(null).cast(DataTypes.StringType).alias(alias));
    }

    // Timestamp handling depends on what the parquet reader gave us:
    //   - TimestampType (most common with non-vectorized reader on parquet-mr 1.14.1 output):
    //     use as-is. Values are real instants.
    //   - LongType (would happen if an explicit schema overrode the type): assume the values
    //     are microseconds since epoch (matches the avro `timestamp-micros` logical type),
    //     divide by 1e6 to get seconds, cast to TimestampType.
    //   - Missing: NULL.
    Column timestampExpr;
    if (available.contains(SparkConstants.RawColumns.TIMESTAMP)) {
      org.apache.spark.sql.types.DataType tsType = schema.apply(SparkConstants.RawColumns.TIMESTAMP).dataType();
      if (tsType instanceof org.apache.spark.sql.types.TimestampType) {
        timestampExpr = col(SparkConstants.RawColumns.TIMESTAMP);
      } else if (tsType instanceof org.apache.spark.sql.types.LongType) {
        timestampExpr = col(SparkConstants.RawColumns.TIMESTAMP).divide(lit(1_000_000L)).cast(DataTypes.TimestampType);
      } else {
        timestampExpr = to_timestamp(col(SparkConstants.RawColumns.TIMESTAMP).cast(DataTypes.StringType));
        log.warn("buildReadExprs: unexpected Timestamp column type={} — string-cast fallback",
          tsType.simpleString());
      }
    } else {
      timestampExpr = lit(null).cast(DataTypes.TimestampType);
    }
    cols.add(timestampExpr.alias(SparkConstants.Columns.TIMESTAMP));

    // user_id := AppInstallationId — matches CH. No UserId fallback. Skips empty strings
    // (avro non-null `string` ships empty rather than null) so downstream filters work.
    Column appInstallationId = available.contains(SparkConstants.RawColumns.APP_INSTALLATION_ID)
      ? col(SparkConstants.RawColumns.APP_INSTALLATION_ID) : lit(null).cast(DataTypes.StringType);
    Column resolved = when(appInstallationId.isNull().or(appInstallationId.equalTo("")),
      lit(null).cast(DataTypes.StringType)).otherwise(appInstallationId);
    cols.add(resolved.alias(SparkConstants.Columns.USER_ID));

    // LogAttributes map — projected for step/global filter evaluation only.
    // Avro map<string> → Parquet MAP → MapType(StringType, StringType).
    // Partitions missing the column get NULL; getItem on a null map returns null,
    // which fails any equality check — correct behaviour for missing properties.
    var mapType = org.apache.spark.sql.types.DataTypes.createMapType(
      DataTypes.StringType, DataTypes.StringType, true);
    cols.add((available.contains(SparkConstants.RawColumns.LOG_ATTRIBUTES)
      ? col(SparkConstants.RawColumns.LOG_ATTRIBUTES)
      : lit(null).cast(mapType)).alias(SparkConstants.Columns.LOG_ATTRIBUTES));

    return cols.toArray(Column[]::new);
  }

  /**
   * Loads parquet for an OTel signal table from S3 over the funnel's run window. Path
   * layout matches the pulse-s3-archiver sink:
   * {@code s3a://<bucketPrefix><projectId>/<tableName>/year=YYYY/month=MM/day=DD/}.
   *
   * <p>Per-day partition failures are logged and skipped; only when ALL day partitions fail
   * to load does this return {@code null} and the calling site falls back to the live DAO join.
   */
  static Dataset<Row> loadOtelSignalParquet(
    SparkSession spark, String s3BucketPrefix, String projectId, String tableName,
    String runTime, FunnelDefinition funnel) {
    LocalDate start;
    LocalDate end;
    if (funnel.startTime() != null && funnel.endTime() != null) {
      start = funnel.startTime().toLocalDateTime().toLocalDate();
      end = funnel.endTime().toLocalDateTime().toLocalDate();
    } else {
      end = LocalDate.parse(runTime.substring(0, 10));
      start = end.minusDays(funnel.dateRange() - 1L);
    }
    String basePath = buildS3Base(s3BucketPrefix, projectId, tableName);
    Dataset<Row> combined = null;
    int loaded = 0;
    for (var d = start; !d.isAfter(end); d = d.plusDays(1)) {
      String path = basePath + dayPartition(d);
      try {
        Dataset<Row> ds = spark.read()
          .format(SparkConstants.SparkConfig.PARQUET_FORMAT)
          .option(SparkConstants.SparkConfig.PARQUET_RECURSIVE_LOOKUP, "true")
          .option(SparkConstants.SparkConfig.PARQUET_MERGE_SCHEMA, "true")
          .load(path);
        combined = (combined == null) ? ds : combined.unionByName(ds, true);
        loaded++;
      } catch (Exception e) {
        log.debug("Skipping {} path {}: {}", tableName, path, e.getMessage());
      }
    }
    if (combined == null) {
      log.info("No {} parquet found for project {} window {}..{}",
        tableName, projectId, start, end);
    } else {
      log.info("Loaded {} for project {}: {} day partition(s)", tableName, projectId, loaded);
    }
    return combined;
  }

  private static String dayPartition(LocalDate d) {
    return String.format("year=%04d/month=%02d/day=%02d/",
      d.getYear(), d.getMonthValue(), d.getDayOfMonth());
  }

  private static Dataset<Row> loadPath(SparkSession spark, String path) {
    try {
      var ds = spark.read()
        .format(SparkConstants.SparkConfig.PARQUET_FORMAT)
        // Schema inference is fine once the vectorized reader is disabled via
        // configureSparkForParquetMrTimestamps. mergeSchema=true unions across day
        // partitions so a single missing column on one day doesn't fail the whole load.
        .option(SparkConstants.SparkConfig.PARQUET_RECURSIVE_LOOKUP, "true")
        .option(SparkConstants.SparkConfig.PARQUET_MERGE_SCHEMA, "true")
        .load(path);
      return ds.select(buildReadExprs(ds));
    } catch (Exception e) {
      log.warn("Skipping S3 path {}: {}", path, e.getMessage());
      return null;
    }
  }
}
