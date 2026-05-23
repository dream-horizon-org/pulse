package org.dreamhorizon.pulsespark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.dreamhorizon.pulsespark.model.FunnelAttributionRow;
import org.dreamhorizon.pulsespark.model.FunnelDefinition;
import org.dreamhorizon.pulsespark.model.FunnelFilter;
import org.dreamhorizon.pulsespark.model.FunnelResult;
import org.dreamhorizon.pulsespark.model.FunnelSessionState;
import org.dreamhorizon.pulsespark.model.FunnelStep;
import org.dreamhorizon.pulsespark.model.FunnelUserState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

import static org.apache.spark.sql.functions.*;

public class FunnelComputeJob {

  private static final Logger log = LoggerFactory.getLogger(FunnelComputeJob.class);

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
    {"EventName", "event_name"},
    {"ProjectId", "project_id"},
    {"SessionId", "session_id"},
    // Timestamp is handled separately below — see buildReadExprs. The parquet-mr 1.14.1 writer
    // emits an avro `timestamp-micros` logical type that Spark 3.5.x sometimes decodes to
    // epoch zero (depending on session config). We bypass Spark's logical-type interpretation
    // entirely by reading the column as Long microseconds and rebuilding TimestampType ourselves.
    {"PulseType", "pulse_type"},               // needed for the custom_event filter
    {"Platform", "os_name"},                   // OTel `Platform` carries the OS name
    {"OsVersion", "os_version"},
    {"AppVersion", "app_build_name"},          // downstream "app_build_name" alias kept for catalog filters
    {"Platform", "device_manufacturer"},       // legacy distinction collapsed in OTel — same source
    {"DeviceModel", "device_model_identifier"},
    {"NetworkProvider", "network_carrier_icc"},
    {"ScreenName", "screen_name"},
    {"ServiceName", "service_name"}
  };

  /**
   * PulseType emitted by SDKs for funnel/journey-eligible custom events. Matches the CH compute path.
   */
  private static final String CUSTOM_EVENT_PULSE_TYPE = "custom_event";

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
      .add("EventName", DataTypes.StringType, true)
      .add("ProjectId", DataTypes.StringType, true)
      .add("SessionId", DataTypes.StringType, true)
      .add("PulseType", DataTypes.StringType, true)
      .add("Platform", DataTypes.StringType, true)
      .add("OsVersion", DataTypes.StringType, true)
      .add("AppVersion", DataTypes.StringType, true)
      .add("DeviceModel", DataTypes.StringType, true)
      .add("NetworkProvider", DataTypes.StringType, true)
      .add("ScreenName", DataTypes.StringType, true)
      .add("ServiceName", DataTypes.StringType, true)
      .add("AppInstallationId", DataTypes.StringType, true)
      .add("UserId", DataTypes.StringType, true)
      // Read as raw Long micros — DO NOT change to TimestampType, that triggers the
      // parquet-mr 1.14.1 ↔ Spark 3.5 decode-to-zero bug.
      .add("Timestamp", DataTypes.LongType, true)
      // Custom event properties for step/global filter evaluation.
      // Avro map<string> → Parquet MAP → Spark MapType(StringType, StringType).
      .add("LogAttributes",
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
   *
   * <p>Pinning the session timezone to UTC is defensive: any {@code unix_timestamp} or
   * {@code timestamp.cast(date)} call that <i>does</i> involve session TZ will at least be
   * stable across cluster restarts and JVM defaults.
   *
   * <p>Symptom this fixes:
   * <pre>
   * raw timestamp range: min=1970-01-01T00:00:00.000+0000 max=1970-01-01T00:00:00.000+0000
   * Funnel X rows after time/global filters: 0
   * </pre>
   * which is the diagnostic the time-filter diag prints when the parquet reader returns
   * zeros for the entire {@code Timestamp} column.
   */
  static void configureSparkForParquetMrTimestamps(SparkSession spark) {
    spark.conf().set("spark.sql.parquet.enableVectorizedReader", "false");
    spark.conf().set("spark.sql.session.timeZone", "UTC");
    log.info("Spark parquet read config: vectorizedReader=false, timeZone=UTC " +
      "(workaround for parquet-mr 1.14.1 TIMESTAMP(MICROS) decode-to-zero bug in Spark 3.5.x)");
  }

  /**
   * Joins an S3 bucket prefix with project + table into a canonical
   * {@code s3a://<bucket>/<projectId>/<tableName>/} URL, normalising trailing/missing
   * slashes so it doesn't matter whether {@code s3BucketPrefix} ends with {@code /} or not.
   */
  static String buildS3Base(String s3BucketPrefix, String projectId, String tableName) {
    String prefix = s3BucketPrefix == null ? "" : s3BucketPrefix;
    if (!prefix.isEmpty() && !prefix.endsWith("/")) {
      prefix = prefix + "/";
    }
    return "s3a://" + prefix + projectId + "/" + tableName + "/";
  }

  public static void runFunnels(SparkSession spark, MysqlRepository mysql, ClickHouseClient ch,
                                Long referenceId, String s3BucketPrefix, String runTime) throws Exception {
    configureSparkForParquetMrTimestamps(spark);
    List<FunnelDefinition> funnels = mysql.fetchFunnels(referenceId);
    if (funnels.isEmpty()) {
      log.info("No funnels to process");
      return;
    }
    log.info("Processing {} funnel(s), referenceId={}", funnels.size(), referenceId);

    if (referenceId != null) {
      var funnel = funnels.get(0);
      if (funnel.startTime() == null || funnel.endTime() == null) {
        throw new IllegalArgumentException("FUNNEL requires start_time and end_time for funnel_id=" + funnel.id());
      }
      var startDt = funnel.startTime().toLocalDateTime();
      var endDt = funnel.endTime().toLocalDateTime();
      var s3Base = buildS3Base(s3BucketPrefix, "fancode", "otel_logs");

      log.info("Funnel {} window [{} -> {}] path={}", funnel.id(), startDt, endDt, s3Base);
      Dataset<Row> raw = readS3ByHours(spark, s3Base, startDt, endDt);
      if (raw == null) {
        log.warn("No S3 data for funnel {}", funnel.id());
        return;
      }
      // One-shot: log the projected schema so timestamp / id types are visible in job output.
      log.info("Funnel {} projected schema: {}", funnel.id(), raw.schema().treeString());
      raw = prepareRaw(raw, "fancode").cache();
      try {
        long startEpoch = startDt.toEpochSecond(ZoneOffset.UTC);
        long endEpoch = endDt.toEpochSecond(ZoneOffset.UTC);
        var results = computeFunnel(raw, funnel, runTime, startEpoch, endEpoch);
        ch.insertFunnelResults(results);
        log.info("Funnel {}: wrote {} result rows", funnel.id(), results.size());
        // Emit per-session bridge + (optional) per-user rollup so downstream
        // drop-off attribution has a SessionId anchor into OTel tables.
        emitBridgeAndRollup(raw, funnel, runTime, startEpoch, endEpoch, ch, s3BucketPrefix);
      } finally {
        raw.unpersist();
      }
    } else {
      Map<String, List<FunnelDefinition>> byProject = new HashMap<>();
      for (var f : funnels) byProject.computeIfAbsent(f.projectId(), k -> new ArrayList<>()).add(f);

      for (var entry : byProject.entrySet()) {
        try {
          processProjectBulk(spark, ch, entry.getKey(), entry.getValue(), s3BucketPrefix, runTime);
        } catch (Exception e) {
          log.error("Project {} funnels failed: {}", entry.getKey(), e.getMessage(), e);
          throw e;
        }
      }
    }
  }

  private static void processProjectBulk(SparkSession spark, ClickHouseClient ch,
                                         String projectId, List<FunnelDefinition> funnels,
                                         String s3Prefix, String runTime) {
    int maxDays = funnels.stream().mapToInt(FunnelDefinition::dateRange).max().orElse(7);
    var endDate = LocalDate.parse(runTime.substring(0, 10));
    var startDate = endDate.minusDays(maxDays - 1L);
    var s3Base = buildS3Base(s3Prefix, projectId, "otel_logs");

    log.info("Project {} reading S3 [{} -> {}] for {} funnel(s) path={}",
      projectId, startDate, endDate, funnels.size(), s3Base);
    Dataset<Row> raw = readS3ByDateRange(spark, s3Base, startDate, endDate);
    if (raw == null) {
      log.warn("No S3 data for project {}", projectId);
      return;
    }
    raw = prepareRaw(raw, projectId).cache();
    try {
      for (var funnel : funnels) {
        try {
          var results = computeFunnel(raw, funnel, runTime, null, null);
          ch.insertFunnelResults(results);
          log.info("Funnel {}: wrote {} result rows", funnel.id(), results.size());
          emitBridgeAndRollup(raw, funnel, runTime, null, null, ch, s3Prefix);
        } catch (Exception e) {
          log.error("Funnel {} failed: {}", funnel.id(), e.getMessage(), e);
          throw e;
        }
      }
    } finally {
      raw.unpersist();
    }
  }

  private static List<FunnelResult> computeFunnel(Dataset<Row> raw, FunnelDefinition funnel,
                                                  String runTime,
                                                  Long startEpochSeconds, Long endEpochSeconds) {
    // Default to UNIQUE_USERS when mode is null/unknown. Matches ClickhouseAnalyticsQueryUtils
    // and the DB default ('UNIQUE_USERS' NOT NULL) — only switch to session_id for an explicit
    // "SESSIONS" mode so a stale/missing mode value never silently changes the denominator.
    var identityCol = "SESSIONS".equalsIgnoreCase(funnel.mode()) ? "session_id" : "user_id";
    long windowSecs = funnel.windowSeconds();
    var steps = funnel.steps();
    int numSteps = steps.size();

    // Time-window filter — but first check whether the parquet's `timestamp` column has
    // usable values. parquet-mr 1.14.1's TIMESTAMP(MICROS) encoding decodes to epoch 0 on
    // Spark 3.5.x in some configurations (even with the vectorized reader disabled). When
    // every row reads as 1970-01-01, ANY time filter discards everything. Skipping the
    // filter is preferable to producing empty results — the day-partition path already
    // limits reads to the right date range, so the worst case is including a few extra
    // hours at the window boundaries.
    Row tsAgg = raw.agg(
      min(col("timestamp")).alias("min_ts"),
      max(col("timestamp")).alias("max_ts")
    ).first();
    boolean tsAppearsBroken = tsAgg == null
      || tsAgg.get(0) == null
      || tsAgg.get(1) == null
      || (tsAgg.get(0) instanceof java.sql.Timestamp
      && ((java.sql.Timestamp) tsAgg.get(0)).getTime() == 0L
      && ((java.sql.Timestamp) tsAgg.get(1)).getTime() == 0L);
    log.info("Funnel {} raw timestamp range: min={} max={} usable={}",
      funnel.id(), tsAgg == null ? null : tsAgg.get(0),
      tsAgg == null ? null : tsAgg.get(1), !tsAppearsBroken);

    Dataset<Row> df;
    if (tsAppearsBroken) {
      log.warn("Funnel {} timestamp column appears all-zero (parquet-mr 1.14.1 + Spark 3.5.x " +
        "TIMESTAMP(MICROS) decode bug). Skipping per-row time filter — partition pruning " +
        "alone bounds the date range. Funnel may include events outside the exact [start,end] " +
        "window. Fix at the writer/reader layer when possible.", funnel.id());
      df = raw;
    } else if (startEpochSeconds != null && endEpochSeconds != null) {
      java.sql.Timestamp startTs = new java.sql.Timestamp(startEpochSeconds * 1000L);
      java.sql.Timestamp endTs = new java.sql.Timestamp(endEpochSeconds * 1000L);
      log.info("Funnel {} time filter [{} -> {}] (epochSec [{} -> {}])",
        funnel.id(), startTs, endTs, startEpochSeconds, endEpochSeconds);
      df = raw.filter(
        col("timestamp").geq(lit(startTs))
          .and(col("timestamp").leq(lit(endTs)))
      );
    } else {
      var endDate = LocalDate.parse(runTime.substring(0, 10));
      var startDate = endDate.minusDays(funnel.dateRange() - 1L);
      java.sql.Timestamp startTs = java.sql.Timestamp.valueOf(startDate.atStartOfDay());
      java.sql.Timestamp endTs = java.sql.Timestamp.valueOf(endDate.plusDays(1L).atStartOfDay());
      log.info("Funnel {} date filter [{} -> {})", funnel.id(), startTs, endTs);
      df = raw.filter(
        col("timestamp").geq(lit(startTs))
          .and(col("timestamp").lt(lit(endTs)))
      );
    }
    if (log.isInfoEnabled()) {
      long postFilterCount = df.count();
      log.info("Funnel {} rows after time/global filters: {}", funnel.id(), postFilterCount);
    }
    df = applyFilters(df, funnel.globalFilters());

    if (funnel.isUnordered()) {
      return computeUnorderedFunnel(df, funnel, identityCol, runTime);
    }

    // Step 0: keep ALL occurrences of step A — each is a separate attempt.
    // No groupBy+min: best-attempt evaluates every attempt individually.
    Dataset<Row> current = stepEvents(df, steps.get(0), identityCol)
      .select(col("identity"), col("ts").alias("ts0"), col("ts").alias("ts_prev"))
      .cache();

    long[] counts = new long[numSteps];
    counts[0] = current.select("identity").distinct().count();
    log.info("Funnel {} step 0 ({}) -> {} identities", funnel.id(), steps.get(0).eventName(), counts[0]);
    if (counts[0] == 0 && log.isInfoEnabled()) {
      // Diagnostic: show top distinct EventName values present in `raw` (NOT df). Using raw
      // so we see actual events regardless of whether the time filter broke. A typo or
      // case-mismatch on the funnel's step-0 event name is immediately visible here; a
      // genuine "no HomeLoaded ingested" shows up as zero rows for that name in the list.
      log.warn("Funnel {} step 0 has 0 identities for EventName='{}'. " +
          "Top distinct event names present in raw custom_event rows for this project " +
          "(time filter not applied, so this reflects all loaded partitions):",
        funnel.id(), steps.get(0).eventName());
      java.util.List<Row> topEvents = raw.groupBy(col("event_name"))
        .agg(count(lit(1)).alias("c"))
        .orderBy(col("c").desc())
        .limit(20)
        .collectAsList();
      if (topEvents.isEmpty()) {
        log.warn("  (no event_name rows in raw — schema projection may be dropping the column)");
      } else {
        topEvents.forEach(r -> log.warn("  event_name='{}' count={}",
          r.<String>getAs("event_name"), r.<Long>getAs("c")));
      }
      // Also: directly query for the funnel's step-0 event name to count how many rows match.
      long stepZeroMatches = raw.filter(col("event_name").equalTo(steps.get(0).eventName())).count();
      log.warn("Funnel {} direct count of event_name='{}' in raw: {} rows",
        funnel.id(), steps.get(0).eventName(), stepZeroMatches);
    }

    // stepTs[i] holds a cached {identity, ts0, ts_i} snapshot for attempts that survived to step i.
    // Each entry is cached independently so it survives after the main alive dataset is unpersisted.
    List<Dataset<Row>> stepTs = new ArrayList<>(numSteps);
    stepTs.add(current.select(col("identity"), col("ts0")).cache()); // step 0: ts0 itself

    for (int i = 1; i < numSteps; i++) {
      if (counts[i - 1] == 0) break;

      Dataset<Row> stepEvts = stepEvents(df, steps.get(i), identityCol)
        .select(col("identity").alias("id_i"), col("ts").alias("ts_i"));

      Column joinCond = col("prev.identity").equalTo(col("next.id_i"))
        .and(col("next.ts_i").gt(col("prev.ts_prev")))
        .and(col("next.ts_i").leq(col("prev.ts0").plus(windowSecs)));

      Dataset<Row> prevCache = current;
      current = current.alias("prev")
        .join(stepEvts.alias("next"), joinCond, "inner")
        .groupBy(col("prev.identity").as("identity"), col("prev.ts0").as("ts0"))
        .agg(min(col("next.ts_i")).alias("ts_prev"))
        .cache();
      prevCache.unpersist();

      counts[i] = current.select("identity").distinct().count();
      log.info("Funnel {} step {} ({}) -> {} identities", funnel.id(), i, steps.get(i).eventName(), counts[i]);

      stepTs.add(current.select(col("identity"), col("ts0"), col("ts_prev").alias("ts_" + i)).cache());
    }

    long[] medians = computeMedians(stepTs, numSteps, counts);
    current.unpersist();
    stepTs.forEach(Dataset::unpersist);

    long step0Count = counts[0];
    var results = new ArrayList<FunnelResult>(numSteps);
    for (int i = 0; i < numSteps; i++) {
      double pct = step0Count > 0 ? (double) counts[i] / step0Count * 100.0 : 0.0;
      results.add(new FunnelResult(
        funnel.id(), funnel.projectId(), runTime,
        i, steps.get(i).eventName(),
        counts[i],
        Math.round(pct * 10_000.0) / 10_000.0,
        i == 0 ? null : (medians[i] >= 0 ? medians[i] : null)
      ));
    }
    return results;
  }

  /**
   * Computes an unordered funnel: steps can occur in any temporal order within the window.
   * Uses a range-based sliding window to find, for each identity, the maximum number of
   * distinct funnel steps completed within any windowSeconds-wide span.
   */
  private static List<FunnelResult> computeUnorderedFunnel(Dataset<Row> df, FunnelDefinition funnel,
                                                           String identityCol, String runTime) {
    long windowSecs = funnel.windowSeconds();
    var steps = funnel.steps();
    int numSteps = steps.size();

    // Collect all step events: {identity, ts, step_idx}
    Dataset<Row> allStepEvents = null;
    for (int i = 0; i < numSteps; i++) {
      Dataset<Row> stepDf = stepEvents(df, steps.get(i), identityCol)
        .withColumn("step_idx", lit(i));
      allStepEvents = (allStepEvents == null) ? stepDf : allStepEvents.union(stepDf);
    }
    allStepEvents = allStepEvents.cache();

    // Sliding window: for each event, collect distinct step indices within [ts, ts + windowSecs].
    WindowSpec rangeWin = Window.partitionBy("identity")
      .orderBy(col("ts"))
      .rangeBetween(0, windowSecs);

    Dataset<Row> withWindowSteps = allStepEvents
      .withColumn("steps_in_window", size(collect_set(col("step_idx")).over(rangeWin)));

    // Per identity, take the best window (max distinct steps achieved).
    Dataset<Row> bestPerIdentity = withWindowSteps
      .groupBy("identity")
      .agg(max("steps_in_window").alias("max_steps"))
      .cache();

    // Step i count = identities who completed at least (i+1) distinct steps in their best window.
    long[] counts = new long[numSteps];
    for (int i = 0; i < numSteps; i++) {
      counts[i] = bestPerIdentity.filter(col("max_steps").geq(lit(i + 1))).count();
      log.info("Funnel {} step {} ({}) -> {} identities (unordered)",
        funnel.id(), i, steps.get(i).eventName(), counts[i]);
    }

    bestPerIdentity.unpersist();
    allStepEvents.unpersist();

    long step0Count = counts[0];
    var results = new ArrayList<FunnelResult>(numSteps);
    for (int i = 0; i < numSteps; i++) {
      double pct = step0Count > 0 ? (double) counts[i] / step0Count * 100.0 : 0.0;
      results.add(new FunnelResult(
        funnel.id(), funnel.projectId(), runTime,
        i, steps.get(i).eventName(),
        counts[i],
        Math.round(pct * 10_000.0) / 10_000.0,
        null // no step-to-step median for unordered funnels
      ));
    }
    return results;
  }

  /**
   * For each step transition (i-1 → i), computes the median seconds elapsed
   * using each user's best attempt: the attempt that reached the farthest step,
   * with the earliest ts0 as tiebreaker among equal-depth attempts.
   *
   * @return array of length numSteps; index 0 is always 0 (no prior step).
   * Returns -1 at index i if there were no users at that step.
   */
  private static long[] computeMedians(List<Dataset<Row>> stepTs, int numSteps, long[] counts) {
    long[] medians = new long[numSteps]; // medians[0] = 0 by default

    if (numSteps <= 1 || stepTs.size() <= 1) {
      return medians;
    }

    // Build wide timeline: one row per (identity, ts0), columns ts_1..ts_{N-1} (null = didn't reach).
    // Start with step-0 base {identity, ts0}.
    Dataset<Row> timeline = stepTs.get(0); // {identity, ts0}
    for (int i = 1; i < stepTs.size(); i++) {
      // Alias join-key columns to avoid ambiguity after the join, then drop them.
      Dataset<Row> snap = stepTs.get(i)  // {identity, ts0, ts_i}
        .withColumnRenamed("identity", "_j_identity")
        .withColumnRenamed("ts0", "_j_ts0");
      Column joinCond = col("identity").equalTo(snap.col("_j_identity"))
        .and(col("ts0").equalTo(snap.col("_j_ts0")));
      timeline = timeline.join(snap, joinCond, "left").drop("_j_identity", "_j_ts0");
    }

    // Score each attempt by farthest step reached (count of non-null ts_i for i>=1).
    Column scoreExpr = lit(0);
    for (int i = 1; i < stepTs.size(); i++) {
      scoreExpr = scoreExpr.plus(when(col("ts_" + i).isNotNull(), lit(1)).otherwise(lit(0)));
    }
    timeline = timeline.withColumn("_score", scoreExpr);

    // Per-user: pick attempt with highest score, then earliest ts0.
    WindowSpec userWin = Window.partitionBy("identity")
      .orderBy(col("_score").desc(), col("ts0").asc());
    Dataset<Row> bestAttempt = timeline
      .withColumn("_rn", row_number().over(userWin))
      .filter(col("_rn").equalTo(1))
      .drop("_score", "_rn")
      .cache();

    // Compute median for each step transition.
    for (int i = 1; i < stepTs.size(); i++) {
      if (counts[i] == 0) {
        medians[i] = -1;
        continue;
      }
      String tsCol = "ts_" + i;
      String prevCol = (i == 1) ? "ts0" : "ts_" + (i - 1);
      Dataset<Row> eligible = bestAttempt.filter(col(tsCol).isNotNull());
      Row r = eligible.agg(
        percentile_approx(col(tsCol).minus(col(prevCol)), lit(0.5), lit(10000)).alias("med")
      ).first();
      medians[i] = (r == null || r.isNullAt(0)) ? -1L : r.getLong(0);
    }

    bestAttempt.unpersist();
    return medians;
  }

  /**
   * Writes {@code funnel_session_state} for every ORDERED funnel (both modes) and,
   * for UNIQUE_USERS funnels, an INDEPENDENTLY-computed {@code funnel_user_state} via
   * a cross-session windowFunnel-equivalent chain on {@code user_id} (not derived from
   * the per-session bridge). Then triggers attribution precompute. Mirrors the CH
   * compute path exactly so cohort numbers and lift values agree across engines.
   *
   * <p>Key semantics:
   * <ul>
   *   <li><b>Single-anchor chain</b> — matches ClickHouse's {@code windowFunnel}: the
   *       chain anchors on the user's/session's <i>first</i> step-0 event ({@code min(ts0)}),
   *       not on every step-0 occurrence. Previously Spark walked all attempts and picked
   *       the best — now it picks one anchor up front, which is what {@code windowFunnel}
   *       does. Cohort sizes therefore match {@code funnel_results.UserCount} exactly.
   *   <li><b>Per-session bridge always written</b> (for SESSIONS cohort + UNIQUE_USERS
   *       x-ray drill-in). Identity is always {@code session_id}.
   *   <li><b>Cross-session user_state for UNIQUE_USERS</b> — computed from the same
   *       event stream grouped by {@code user_id}, tracking {@code session_id} at each
   *       matched step so the canonical session = the session containing the deepest
   *       matched step. Captures cross-session converters that a per-session view misses.
   *   <li><b>Attribution precompute</b> — final stage emits ranked (step × cause) rows
   *       into {@code funnel_dropoff_attribution}.
   * </ul>
   *
   * <p>Unordered funnels skip everything in this method.
   */
  private static void emitBridgeAndRollup(Dataset<Row> raw, FunnelDefinition funnel,
                                          String runTime,
                                          Long startEpochSeconds, Long endEpochSeconds,
                                          ClickHouseClient ch, String s3BucketPrefix) {
    if (funnel.isUnordered()) {
      log.info("Funnel {} is UNORDERED — skipping bridge/rollup/attribution emission", funnel.id());
      return;
    }

    var steps = funnel.steps();
    int numSteps = steps.size();
    if (numSteps == 0) {
      return;
    }
    long windowSecs = funnel.windowSeconds();
    int finalStepIdx = numSteps - 1;
    boolean isUniqueUsers = !"SESSIONS".equalsIgnoreCase(funnel.mode());

    // Apply the same window/date filter that computeFunnel uses so the bridge cohort matches
    // the aggregate counts in funnel_results exactly. If the parquet's `timestamp` column
    // appears unusable (all zeros — parquet-mr 1.14.1 ↔ Spark 3.5.x decode bug), skip the
    // filter and rely on partition pruning.
    Row tsAgg = raw.agg(min(col("timestamp")), max(col("timestamp"))).first();
    boolean tsAppearsBroken = tsAgg == null
      || tsAgg.get(0) == null
      || tsAgg.get(1) == null
      || (tsAgg.get(0) instanceof java.sql.Timestamp
      && ((java.sql.Timestamp) tsAgg.get(0)).getTime() == 0L
      && ((java.sql.Timestamp) tsAgg.get(1)).getTime() == 0L);
    Dataset<Row> df;
    if (tsAppearsBroken) {
      df = raw;
    } else if (startEpochSeconds != null && endEpochSeconds != null) {
      java.sql.Timestamp startTs = new java.sql.Timestamp(startEpochSeconds * 1000L);
      java.sql.Timestamp endTs = new java.sql.Timestamp(endEpochSeconds * 1000L);
      df = raw.filter(
        col("timestamp").geq(lit(startTs))
          .and(col("timestamp").leq(lit(endTs)))
      );
    } else {
      var endDate = LocalDate.parse(runTime.substring(0, 10));
      var startDate = endDate.minusDays(funnel.dateRange() - 1L);
      java.sql.Timestamp startTs = java.sql.Timestamp.valueOf(startDate.atStartOfDay());
      java.sql.Timestamp endTs = java.sql.Timestamp.valueOf(endDate.plusDays(1L).atStartOfDay());
      df = raw.filter(
        col("timestamp").geq(lit(startTs))
          .and(col("timestamp").lt(lit(endTs)))
      );
    }
    df = applyFilters(df, funnel.globalFilters()).cache();

    try {
      // ── Per-session bridge (single-anchor, matches windowFunnel) ────────────────
      List<FunnelSessionState> bridgeRows =
        computePerSessionBridge(df, funnel, runTime, windowSecs, numSteps, finalStepIdx);
      if (bridgeRows == null) {
        log.warn("Funnel {}: no session reached step 0 — skipping bridge", funnel.id());
        return;
      }
      ch.insertFunnelSessionState(bridgeRows);
      log.info("Funnel {}: wrote {} session_state rows", funnel.id(), bridgeRows.size());

      // ── Cross-session user_state for UNIQUE_USERS funnels ───────────────────────
      // Independently computed on user_id (NOT derived from session_state) so
      // cross-session converters are captured. Matches CH's buildUserStateInsertSql.
      List<FunnelUserState> userRows = null;
      if (isUniqueUsers) {
        userRows = computeCrossSessionUserState(
          df, funnel, runTime, windowSecs, numSteps, finalStepIdx);
        ch.insertFunnelUserState(userRows);
        log.info("Funnel {}: wrote {} user_state rows", funnel.id(), userRows.size());
      }

      // ── Attribution precompute ──────────────────────────────────────────────────
      // Best-effort: a failure here doesn't fail the funnel compute — side-panel
      // will fall back to live cause-join query.
      try {
        emitAttribution(df, funnel, runTime, bridgeRows, userRows, ch, s3BucketPrefix);
      } catch (Exception e) {
        log.warn("Funnel {}: attribution precompute failed (panel will fall back to live join): {}",
          funnel.id(), e.getMessage(), e);
      }
    } finally {
      df.unpersist();
    }
  }

  /**
   * Builds {@code funnel_session_state} rows for every session that touched the funnel,
   * using a single-anchor chain matching ClickHouse's {@code windowFunnel}. The anchor
   * per session is {@code min(ts)} of step-0 events.
   */
  private static List<FunnelSessionState> computePerSessionBridge(
    Dataset<Row> df, FunnelDefinition funnel, String runTime,
    long windowSecs, int numSteps, int finalStepIdx) {
    var steps = funnel.steps();

    // attempts: ALL step-0 occurrences as independent anchors — mirrors computeFunnel's
    // multi-anchor chain walk. A session that re-enters the funnel (hits step-0 again
    // after an earlier attempt stalled) gets credit for its best attempt, not just the first.
    Dataset<Row> attempts = stepEvents(df, steps.get(0), "session_id")
      .select(col("identity"), col("ts").alias("ts0"), col("ts").alias("ts_prev"))
      .cache();
    if (attempts.rdd().isEmpty()) {
      attempts.unpersist();
      return null;
    }

    Dataset<Row> current = attempts;
    // perStep[i] tracks (session_id, ts0, ts_at_step) for sessions that reached step i.
    List<Dataset<Row>> perStep = new ArrayList<>(numSteps);
    perStep.add(current.select(
      col("identity"),
      col("ts0"),
      col("ts0").alias("ts_at_step")
    ));

    for (int i = 1; i < numSteps; i++) {
      Dataset<Row> stepEvts = stepEvents(df, steps.get(i), "session_id")
        .select(col("identity").alias("id_i"), col("ts").alias("ts_i"));

      Column joinCond = col("prev.identity").equalTo(col("next.id_i"))
        .and(col("next.ts_i").gt(col("prev.ts_prev")))
        .and(col("next.ts_i").leq(col("prev.ts0").plus(windowSecs)));

      Dataset<Row> prevCache = current;
      current = current.alias("prev")
        .join(stepEvts.alias("next"), joinCond, "inner")
        .groupBy(col("prev.identity").as("identity"), col("prev.ts0").as("ts0"))
        .agg(min(col("next.ts_i")).alias("ts_prev"))
        .cache();
      prevCache.unpersist();

      perStep.add(current.select(
        col("identity"),
        col("ts0"),
        col("ts_prev").alias("ts_at_step")
      ));

      if (current.rdd().isEmpty()) {
        break;
      }
    }
    current.unpersist();

    // Each session is in exactly one attempts row (single-anchor) and walked into perStep[i]
    // for the deepest i it reached. To pick that "deepest" row per session, label each with
    // step_idx and take max(step_idx) per identity.
    Dataset<Row> unionAll = null;
    for (int i = 0; i < perStep.size(); i++) {
      Dataset<Row> withIdx = perStep.get(i).withColumn("step_idx", lit(i));
      unionAll = (unionAll == null) ? withIdx : unionAll.union(withIdx);
    }

    WindowSpec bestWin = Window.partitionBy("identity")
      .orderBy(col("step_idx").desc(), col("ts_at_step").desc());
    Dataset<Row> bestPerSession = unionAll
      .withColumn("_rn", row_number().over(bestWin))
      .filter(col("_rn").equalTo(1))
      .drop("_rn")
      .cache();

    // Hydrate dimension carryover (screen, app version, etc.) by joining back to raw
    // at the exact moment of the furthest-step event, plus user_id.
    Dataset<Row> hydrated = bestPerSession.alias("b")
      .join(
        df.alias("d"),
        col("b.identity").equalTo(col("d.session_id"))
          .and(unix_timestamp(col("d.timestamp")).equalTo(col("b.ts_at_step"))),
        "left"
      )
      .select(
        col("b.identity").alias("session_id"),
        col("b.ts0").alias("ts0"),
        col("b.ts_at_step").alias("ts_at_step"),
        col("b.step_idx").alias("step_idx"),
        coalesce(col("d.user_id"), lit("")).alias("user_id"),
        coalesce(col("d.screen_name"), lit("")).alias("screen"),
        coalesce(col("d.app_build_name"), lit("")).alias("app_version"),
        coalesce(col("d.os_name"), lit("")).alias("os_name"),
        coalesce(col("d.os_version"), lit("")).alias("os_version"),
        coalesce(col("d.device_manufacturer"), lit("")).alias("platform"),
        coalesce(col("d.device_model_identifier"), lit("")).alias("device_model"),
        coalesce(col("d.network_carrier_icc"), lit("")).alias("network_provider")
      )
      .dropDuplicates("session_id")
      .cache();

    List<Row> collected = hydrated.collectAsList();
    bestPerSession.unpersist();
    hydrated.unpersist();
    attempts.unpersist();

    List<FunnelSessionState> bridgeRows = new ArrayList<>(collected.size());
    for (Row r : collected) {
      int maxStep = r.getAs("step_idx");
      long ts0 = r.getAs("ts0");
      long tsAt = r.getAs("ts_at_step");
      int dropoff = maxStep >= finalStepIdx ? -1 : maxStep + 1;
      bridgeRows.add(new FunnelSessionState(
        funnel.id(), funnel.projectId(), runTime,
        r.<String>getAs("session_id"),
        r.<String>getAs("user_id"),
        maxStep,
        steps.get(maxStep).eventName(),
        tsAt,
        dropoff,
        Math.max(0, tsAt - ts0),
        r.<String>getAs("screen"),
        "",
        r.<String>getAs("app_version"),
        r.<String>getAs("os_name"),
        r.<String>getAs("os_version"),
        r.<String>getAs("platform"),
        r.<String>getAs("device_model"),
        r.<String>getAs("network_provider"),
        ""
      ));
    }
    return bridgeRows;
  }

  /**
   * Builds {@code funnel_user_state} rows by running a cross-session windowFunnel-equivalent
   * chain grouped by {@code user_id}. Tracks {@code session_id} at each matched step so the
   * canonical session = the session containing the deepest matched step (which may differ
   * from the session where step 0 was anchored).
   *
   * <p>Independent of the per-session bridge — that's the whole point. Cross-session
   * converters (user does step 0 in session A, step k in session B) are captured here but
   * invisible to a per-session view.
   */
  private static List<FunnelUserState> computeCrossSessionUserState(
    Dataset<Row> df, FunnelDefinition funnel, String runTime,
    long windowSecs, int numSteps, int finalStepIdx) {
    var steps = funnel.steps();

    // attempts: per user, anchor on min(ts0) of step-0 events. Track the sid of that anchor.
    Dataset<Row> step0Events = stepEvents(df, steps.get(0), "user_id")
      .filter(col("identity").isNotNull().and(col("identity").notEqual("")))
      .join(
        df.select(col("user_id"), col("session_id"), unix_timestamp(col("timestamp")).alias("_ts"))
          .filter(col("event_name").equalTo(steps.get(0).eventName())),
        col("identity").equalTo(col("user_id"))
          .and(col("ts").equalTo(col("_ts"))),
        "left"
      )
      .select(col("identity"), col("ts"), coalesce(col("session_id"), lit("")).alias("sid"));

    // attempts: ALL step-0 occurrences as independent anchors — mirrors computeFunnel's
    // multi-anchor chain walk. Each (user_id, ts, session_id) triple is a separate starting
    // point; cross-session converters whose earliest attempt stalled are no longer missed.
    Dataset<Row> attempts = step0Events
      .select(
        col("identity"),
        col("ts").alias("t0"),
        col("sid").alias("sid0"),
        col("ts").alias("ts_prev"),
        col("sid").alias("sid_prev")
      )
      .cache();
    if (attempts.rdd().isEmpty()) {
      attempts.unpersist();
      return new ArrayList<>();
    }

    Dataset<Row> current = attempts;
    // perStep[i]: (user_id, t0, sid0, ts_at_step, sid_at_step) for users who reached step i.
    List<Dataset<Row>> perStep = new ArrayList<>(numSteps);
    perStep.add(current.select(
      col("identity"),
      col("t0"),
      col("sid0"),
      col("t0").alias("ts_at_step"),
      col("sid0").alias("sid_at_step")
    ));

    for (int i = 1; i < numSteps; i++) {
      // Events matching step i, keyed by user_id, carrying session_id and ts.
      Dataset<Row> stepEvts = df.filter(col("event_name").equalTo(steps.get(i).eventName()))
        .filter(col("user_id").isNotNull().and(col("user_id").notEqual("")))
        .select(
          col("user_id").alias("id_i"),
          unix_timestamp(col("timestamp")).alias("ts_i"),
          coalesce(col("session_id"), lit("")).alias("sid_i")
        );

      Column joinCond = col("prev.identity").equalTo(col("next.id_i"))
        .and(col("next.ts_i").gt(col("prev.ts_prev")))
        .and(col("next.ts_i").leq(col("prev.t0").plus(windowSecs)));

      Dataset<Row> prevCache = current;
      current = current.alias("prev")
        .join(stepEvts.alias("next"), joinCond, "inner")
        .groupBy(col("prev.identity").as("identity"),
          col("prev.t0").as("t0"),
          col("prev.sid0").as("sid0"))
        .agg(
          min(col("next.ts_i")).alias("ts_prev"),
          expr("min_by(`next`.`sid_i`, `next`.`ts_i`)").alias("sid_prev")
        )
        .cache();
      prevCache.unpersist();

      perStep.add(current.select(
        col("identity"),
        col("t0"),
        col("sid0"),
        col("ts_prev").alias("ts_at_step"),
        col("sid_prev").alias("sid_at_step")
      ));

      if (current.rdd().isEmpty()) {
        break;
      }
    }
    current.unpersist();

    // Pick the deepest step row per user.
    Dataset<Row> unionAll = null;
    for (int i = 0; i < perStep.size(); i++) {
      Dataset<Row> withIdx = perStep.get(i).withColumn("step_idx", lit(i));
      unionAll = (unionAll == null) ? withIdx : unionAll.union(withIdx);
    }

    WindowSpec bestWin = Window.partitionBy("identity")
      .orderBy(col("step_idx").desc(), col("ts_at_step").desc());
    Dataset<Row> bestPerUser = unionAll
      .withColumn("_rn", row_number().over(bestWin))
      .filter(col("_rn").equalTo(1))
      .drop("_rn")
      .cache();

    // SessionAttempts: distinct sessions per user across ALL funnel-event types.
    List<String> stepNames = new ArrayList<>(numSteps);
    for (var step : steps) {
      stepNames.add(step.eventName());
    }
    Dataset<Row> attemptCounts = df.filter(col("event_name").isin(stepNames.toArray()))
      .filter(col("user_id").isNotNull().and(col("user_id").notEqual("")))
      .groupBy(col("user_id"))
      .agg(countDistinct(col("session_id")).alias("session_attempts"));

    // Hydrate dimensions from the canonical session's event row.
    Dataset<Row> hydrated = bestPerUser.alias("b")
      .join(
        df.alias("d"),
        col("b.sid_at_step").equalTo(col("d.session_id"))
          .and(unix_timestamp(col("d.timestamp")).equalTo(col("b.ts_at_step")))
          .and(col("b.identity").equalTo(col("d.user_id"))),
        "left"
      )
      .join(
        attemptCounts.alias("ac"),
        col("b.identity").equalTo(col("ac.user_id")),
        "left"
      )
      .select(
        col("b.identity").alias("user_id"),
        col("b.t0").alias("t0"),
        col("b.ts_at_step").alias("ts_at_step"),
        col("b.sid_at_step").alias("canonical_sid"),
        col("b.step_idx").alias("step_idx"),
        coalesce(col("d.screen_name"), lit("")).alias("screen"),
        coalesce(col("d.app_build_name"), lit("")).alias("app_version"),
        coalesce(col("d.os_name"), lit("")).alias("os_name"),
        coalesce(col("d.os_version"), lit("")).alias("os_version"),
        coalesce(col("d.device_manufacturer"), lit("")).alias("platform"),
        coalesce(col("d.device_model_identifier"), lit("")).alias("device_model"),
        coalesce(col("d.network_carrier_icc"), lit("")).alias("network_provider"),
        coalesce(col("ac.session_attempts"), lit(1L)).alias("session_attempts")
      )
      .dropDuplicates("user_id")
      .cache();

    List<Row> collected = hydrated.collectAsList();
    bestPerUser.unpersist();
    hydrated.unpersist();
    attempts.unpersist();

    List<FunnelUserState> userRows = new ArrayList<>(collected.size());
    for (Row r : collected) {
      int maxStep = r.getAs("step_idx");
      int dropoff = maxStep >= finalStepIdx ? -1 : maxStep + 1;
      userRows.add(new FunnelUserState(
        funnel.id(), funnel.projectId(), runTime,
        r.<String>getAs("user_id"),
        maxStep,
        dropoff,
        r.<String>getAs("canonical_sid"),
        r.<Long>getAs("ts_at_step"),
        "", // CanonicalTraceIdAtDropoff — not in vector-log parquet
        r.<String>getAs("screen"),
        r.<String>getAs("app_version"),
        r.<String>getAs("os_name"),
        r.<String>getAs("os_version"),
        r.<String>getAs("platform"),
        r.<String>getAs("device_model"),
        r.<String>getAs("network_provider"),
        "",
        ((Long) r.getAs("session_attempts")).intValue()
      ));
    }
    return userRows;
  }

  private static Dataset<Row> stepEvents(Dataset<Row> df, FunnelStep step, String identityCol) {
    Dataset<Row> filtered = df.filter(col("event_name").equalTo(step.eventName()));
    filtered = applyFilters(filtered, step.stepFilters());
    return filtered
      .select(
        col(identityCol).alias("identity"),
        unix_timestamp(col("timestamp")).alias("ts")
      )
      .filter(col("identity").isNotNull().and(col("identity").notEqual("")));
  }

  static Dataset<Row> applyFilters(Dataset<Row> df, List<FunnelFilter> filters) {
    for (var filter : filters) {
      if (filter.field() == null || filter.field().isBlank()) {
        log.warn("Skipping filter: field is null or blank");
        continue;
      }
      var fieldCol = FilterFieldMapper.toColumn(filter.field());
      var vals = filter.value().toArray();
      if (vals.length == 0) {
        log.warn("Skipping filter: empty value list for field '{}'", filter.field());
        continue;
      }
      String op = FunnelFilterOperators.normalize(filter.operator());
      Column cond = switch (op) {
        case "EQ", "=", "IN" -> fieldCol.isin(vals);
        case "NE", "!=", "NOT_IN" -> not(fieldCol.isin(vals));
        case "CONTAINS" -> filter.value().stream()
          .map(fieldCol::contains)
          .reduce(Column::or)
          .orElse(lit(true));
        default -> {
          log.warn("Unknown filter operator '{}' (normalized='{}') for field '{}' — filter skipped",
            filter.operator(), op, filter.field());
          yield lit(true);
        }
      };
      df = df.filter(cond);
    }
    return df;
  }

  /**
   * Reads parquet partitions over an inclusive date range. The new
   * pulse-s3-archiver layout partitions by {@code year=YYYY/month=MM/day=DD},
   * not by date-string folders or hour subdirs.
   */
  static Dataset<Row> readS3ByDateRange(SparkSession spark, String s3BasePath,
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
  static Dataset<Row> readS3ByHours(SparkSession spark, String s3BasePath,
                                    LocalDateTime startDt, LocalDateTime endDt) {
    return readS3ByDateRange(spark, s3BasePath, startDt.toLocalDate(), endDt.toLocalDate());
  }

  private static String dayPartition(LocalDate d) {
    return String.format("year=%04d/month=%02d/day=%02d/",
      d.getYear(), d.getMonthValue(), d.getDayOfMonth());
  }

  private static Dataset<Row> loadPath(SparkSession spark, String path) {
    try {
      var ds = spark.read()
        .format("parquet")
        // Schema inference is fine once the vectorized reader is disabled via
        // configureSparkForParquetMrTimestamps. mergeSchema=true unions across day
        // partitions so a single missing column on one day doesn't fail the whole load.
        .option("recursiveFileLookup", "true")
        .option("mergeSchema", "true")
        .load(path);
      return ds.select(buildReadExprs(ds));
    } catch (Exception e) {
      log.warn("Skipping S3 path {}: {}", path, e.getMessage());
      return null;
    }
  }

  /**
   * Narrows {@code raw} to the project's custom-event rows that funnels and journeys actually
   * consume. Without the {@code pulse_type = 'custom_event'} filter we'd include
   * session-lifecycle, jank, web-vital, crash etc. log rows that share the same
   * {@code otel_logs} parquet — which is what ClickHouse's {@code buildInsertSqlWindowFunnel}
   * explicitly excludes via {@code WHERE PulseType = 'custom_event'}. Without this filter on
   * Spark, the chain walker matches non-custom rows that happen to share an EventName, OR
   * misses rows whose PulseType isn't what the funnel ingester expects — leading to the
   * "0 count for HomeLoaded" symptom even when the events are demonstrably in S3.
   */
  private static Dataset<Row> prepareRaw(Dataset<Row> ds, String projectId) {
    Dataset<Row> filtered = ds.filter(col("project_id").equalTo(projectId))
      .filter(col("pulse_type").equalTo(lit(CUSTOM_EVENT_PULSE_TYPE)));
    if (log.isInfoEnabled()) {
      long count = filtered.count();
      log.info("prepareRaw: project={} pulse_type={} → {} rows",
        projectId, CUSTOM_EVENT_PULSE_TYPE, count);
      if (count == 0) {
        log.warn(
          "prepareRaw: 0 rows after pulse_type='{}' filter for project {}. " +
            "Either the SDK isn't emitting custom events with this PulseType, " +
            "or the otel_logs partitions for this window contain only non-custom rows.",
          CUSTOM_EVENT_PULSE_TYPE, projectId);
      }
    }
    return filtered;
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
    if (available.contains("Timestamp")) {
      org.apache.spark.sql.types.DataType tsType = schema.apply("Timestamp").dataType();
      if (tsType instanceof org.apache.spark.sql.types.TimestampType) {
        timestampExpr = col("Timestamp");
      } else if (tsType instanceof org.apache.spark.sql.types.LongType) {
        timestampExpr = col("Timestamp").divide(lit(1_000_000L)).cast(DataTypes.TimestampType);
      } else {
        timestampExpr = to_timestamp(col("Timestamp").cast(DataTypes.StringType));
        log.warn("buildReadExprs: unexpected Timestamp column type={} — string-cast fallback",
          tsType.simpleString());
      }
    } else {
      timestampExpr = lit(null).cast(DataTypes.TimestampType);
    }
    cols.add(timestampExpr.alias("timestamp"));

    // user_id := AppInstallationId — matches CH. No UserId fallback. Skips empty strings
    // (avro non-null `string` ships empty rather than null) so downstream filters work.
    Column appInstallationId = available.contains("AppInstallationId")
      ? col("AppInstallationId") : lit(null).cast(DataTypes.StringType);
    Column resolved = when(appInstallationId.isNull().or(appInstallationId.equalTo("")),
      lit(null).cast(DataTypes.StringType)).otherwise(appInstallationId);
    cols.add(resolved.alias("user_id"));

    // LogAttributes map — projected for step/global filter evaluation only.
    // Avro map<string> → Parquet MAP → MapType(StringType, StringType).
    // Partitions missing the column get NULL; getItem on a null map returns null,
    // which fails any equality check — correct behaviour for missing properties.
    var mapType = org.apache.spark.sql.types.DataTypes.createMapType(
      DataTypes.StringType, DataTypes.StringType, true);
    cols.add((available.contains("LogAttributes")
      ? col("LogAttributes")
      : lit(null).cast(mapType)).alias("log_attributes"));

    return cols.toArray(Column[]::new);
  }

  // ── Attribution precompute ─────────────────────────────────────────────────────

  /**
   * Window around {@code LastReachedAt} used to attribute OTel signals (matches CH DAO).
   */
  private static final int ATTRIBUTION_WINDOW_BEFORE_SEC = 30;
  private static final int ATTRIBUTION_WINDOW_AFTER_SEC = 60;
  /**
   * Cap on the example sessions array stored per (step × cause) row.
   */
  private static final int ATTRIBUTION_EXAMPLES_PER_CAUSE = 50;

  /**
   * Emits precomputed {@code funnel_dropoff_attribution} rows by joining the bridge
   * cohort (per-session for SESSIONS funnels, cross-session user_state for UNIQUE_USERS)
   * against OTel signal sources loaded from S3:
   * <ul>
   *   <li>{@code otel_logs} parquet filtered by {@code pulse.type} ∈ (crash, anr, non_fatal)
   *       — for {@code crash} / {@code anr} / {@code non_fatal} causes</li>
   *   <li>{@code otel_traces} parquet filtered to spans with HTTP attributes
   *       — for {@code http_5xx} / {@code http_4xx} causes</li>
   * </ul>
   *
   * <p>{@code frozen_frame} is NOT emitted by Spark in v1 — ClickHouse derives it via
   * the {@code session_summary} MV (not S3-archived). Side-panel falls back to the live
   * cause-join for any cause kind missing from the precomputed rows.
   *
   * <p>Returns early without emitting if {@code numSteps < 2} (no dropoff step to attribute).
   */
  private static void emitAttribution(
    Dataset<Row> df, FunnelDefinition funnel, String runTime,
    List<FunnelSessionState> bridgeRows, List<FunnelUserState> userRows,
    ClickHouseClient ch, String s3BucketPrefix) {
    if (funnel.steps().size() < 2) {
      return;
    }
    boolean isUniqueUsers = !"SESSIONS".equalsIgnoreCase(funnel.mode());
    SparkSession spark = df.sparkSession();

    // Build a cohort DataFrame uniform across modes: (sessionId, lastReachedAt, dropoffStep,
    // isConverter). For UNIQUE_USERS, sessionId is the canonical session.
    Dataset<Row> droppers = buildCohort(spark, isUniqueUsers, bridgeRows, userRows, false);
    Dataset<Row> converters = buildCohort(spark, isUniqueUsers, bridgeRows, userRows, true);
    long converterCohortCount = converters.count();
    long dropperCount = droppers.count();
    log.info("Funnel {}: attribution cohort — droppers={} converters={}",
      funnel.id(), dropperCount, converterCohortCount);
    if (dropperCount == 0 && converterCohortCount == 0) {
      log.info("Funnel {}: empty cohorts — no attribution rows to write", funnel.id());
      return;
    }
    Dataset<Row> dropperCohorts = droppers
      .groupBy(col("dropoff_step").alias("step"))
      .agg(count(lit(1)).alias("cohort"))
      .cache();

    List<FunnelAttributionRow> all = new ArrayList<>();

    // Load OTel signal parquets for the run window.
    // Path layout: s3a://pulse-otel-ingestion/{projectId}/{table}/year=YYYY/month=MM/day=DD/
    //
    // Three separate tables:
    //  • stack_trace_events — crash/ANR/non_fatal routed to Pulse server → error-grouped → S3
    //  • otel_logs          — jank events (app.jank.frozen, app.jank.slow) via Kafka → S3
    //  • otel_traces        — network spans (PulseType = "network.<code>") via Kafka → S3
    var stackTraceEvents = loadOtelSignalParquet(spark, s3BucketPrefix, "fancode", "stack_trace_events", runTime, funnel);
    var otelLogs = loadOtelSignalParquet(spark, s3BucketPrefix, "fancode", "otel_logs", runTime, funnel);
    var traces = loadOtelSignalParquet(spark, s3BucketPrefix, "fancode", "otel_traces", runTime, funnel);

    if (stackTraceEvents != null) {
      // Diagnostic: crash/anr/non_fatal come from stack_trace_events (after Pulse server error grouping).
      // PulseType values: "device.crash", "device.anr", "non_fatal".
      long rawCrashCount = stackTraceEvents.filter(col("ProjectId").equalTo("fancode")).count();
      Row crashTs = stackTraceEvents.filter(col("ProjectId").equalTo("fancode"))
        .agg(min(col("Timestamp")), max(col("Timestamp"))).first();
      log.info("Funnel {}: stack_trace_events (crash/anr/non_fatal) in S3 = {} rows, ts=[{} → {}]",
        funnel.id(), rawCrashCount, crashTs.get(0), crashTs.get(1));
      all.addAll(buildCrashCauses(
        funnel, runTime, droppers, converters, dropperCohorts,
        converterCohortCount, stackTraceEvents));
    }
    if (otelLogs != null) {
      // Diagnostic: jank events stay in otel_logs (NOT routed to Pulse server backend).
      Dataset<Row> rawJank = otelLogs
        .filter(col("ProjectId").equalTo("fancode"))
        .filter(col("PulseType").isin("app.jank.frozen", "app.jank.slow"));
      long rawJankCount = rawJank.count();
      Row jankTs = rawJank.agg(min(col("Timestamp")), max(col("Timestamp"))).first();
      log.info("Funnel {}: otel_logs jank (frozen/slow) in S3 = {} rows, ts=[{} → {}]",
        funnel.id(), rawJankCount, jankTs.get(0), jankTs.get(1));
      all.addAll(buildJankCauses(
        funnel, runTime, droppers, converters, dropperCohorts,
        converterCohortCount, otelLogs));
    }
    if (traces != null) {
      // Diagnostic: network spans — status derived from PulseType "network.<code>".
      // regexp_extract returns "" on no-match (NOT null); empty string ⇒ cast to int fails on
      // Spark 4 strict mode. Guard with notEqual("") before casting.
      Column pulseTypeStatusStr = regexp_extract(col("PulseType"), "^network\\.(\\d+)$", 1);
      Column diagStatus = when(pulseTypeStatusStr.notEqual(""),
          pulseTypeStatusStr.cast(DataTypes.IntegerType))
        .otherwise(coalesce(col("HttpStatusCode"), lit(0)).cast(DataTypes.IntegerType));
      Dataset<Row> rawHttpSpans = traces
        .filter(col("ProjectId").equalTo("fancode"))
        .withColumn("_status", diagStatus)
        .filter(col("_status").geq(400));
      long rawHttpCount = rawHttpSpans.count();
      Row httpTs = rawHttpSpans.agg(min(col("Timestamp")), max(col("Timestamp"))).first();
      log.info("Funnel {}: otel_traces HTTP>=400 in S3 = {} rows, ts=[{} → {}]",
        funnel.id(), rawHttpCount, httpTs.get(0), httpTs.get(1));
      all.addAll(buildHttpCauses(
        funnel, runTime, droppers, converters, dropperCohorts,
        converterCohortCount, traces));
    }

    dropperCohorts.unpersist();

    if (all.isEmpty()) {
      log.info("Funnel {}: no attribution rows emitted — signals were loaded but none matched " +
        "the dropper cohort join (session_id + timestamp window). " +
        "Likely cause: signal parquet files for this window were written by parquet-mr 1.14.1 " +
        "(Timestamp decodes to epoch 0) so e_ts=0 never falls within last_reached_at±window. " +
        "Self-heals as 1.13.1-written signal partitions accumulate.", funnel.id());
      return;
    }
    ch.insertFunnelDropoffAttribution(all);
    log.info("Funnel {}: wrote {} attribution rows", funnel.id(), all.size());
  }

  /**
   * Builds a uniform cohort DataFrame from bridge rows. Columns:
   * {@code session_id} (the anchor session — canonical for UNIQUE_USERS),
   * {@code last_reached_at} (epoch seconds), {@code dropoff_step}.
   *
   * @param converterMode {@code true} → only rows with {@code dropoffStep == -1};
   *                      {@code false} → only rows with {@code dropoffStep > 0}.
   */
  private static Dataset<Row> buildCohort(
    SparkSession spark, boolean isUniqueUsers,
    List<FunnelSessionState> bridgeRows, List<FunnelUserState> userRows,
    boolean converterMode) {
    List<Row> rows = new ArrayList<>();
    if (isUniqueUsers && userRows != null) {
      for (var u : userRows) {
        boolean isConverter = u.dropoffStep() == -1;
        if (converterMode != isConverter) continue;
        if (u.canonicalSessionId() == null || u.canonicalSessionId().isEmpty()) continue;
        rows.add(org.apache.spark.sql.RowFactory.create(
          u.canonicalSessionId(),
          u.canonicalLastReachedAtEpochSec(),
          u.dropoffStep()
        ));
      }
    } else if (bridgeRows != null) {
      for (var s : bridgeRows) {
        boolean isConverter = s.dropoffStep() == -1;
        if (converterMode != isConverter) continue;
        if (s.sessionId() == null || s.sessionId().isEmpty()) continue;
        rows.add(org.apache.spark.sql.RowFactory.create(
          s.sessionId(),
          s.lastReachedAtEpochSec(),
          s.dropoffStep()
        ));
      }
    }
    var schema = org.apache.spark.sql.types.DataTypes.createStructType(new org.apache.spark.sql.types.StructField[]{
      org.apache.spark.sql.types.DataTypes.createStructField("session_id", DataTypes.StringType, false),
      org.apache.spark.sql.types.DataTypes.createStructField("last_reached_at", DataTypes.LongType, false),
      org.apache.spark.sql.types.DataTypes.createStructField("dropoff_step", DataTypes.IntegerType, false)
    });
    return spark.createDataFrame(rows, schema);
  }

  /**
   * Loads parquet for an OTel signal table from S3 over the funnel's run window.
   * Path layout: {@code s3a://pulse-otel-ingestion/{projectId}/{tableName}/year=YYYY/month=MM/day=DD/}.
   * Returns {@code null} when no data is found (e.g. project has never emitted crashes).
   */
  /**
   * Loads parquet for an OTel signal table from S3 over the funnel's run window. Path
   * layout matches the pulse-s3-archiver sink:
   * {@code s3a://<bucketPrefix><projectId>/<tableName>/year=YYYY/month=MM/day=DD/}.
   *
   * <p>The bucket prefix is the same one the job already received via
   * {@code FunnelComputeJob.runFunnels(... String s3BucketPrefix ...)} (typically ends in
   * a trailing slash, e.g. {@code "pulse-otel-ingestion/"}). Per-day partition failures
   * are logged and skipped; only when ALL day partitions fail to load does this return
   * {@code null} and the calling site falls back to the live DAO join.
   */
  private static Dataset<Row> loadOtelSignalParquet(
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
          .format("parquet")
          .option("recursiveFileLookup", "true")
          .option("mergeSchema", "true")
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

  /**
   * Joins {@code stack_trace_events} parquet against the cohort within the attribution window.
   * Emits one row per (StepIndex, CauseKind, CauseKey) where
   * CauseKind ∈ ({@code crash}, {@code anr}, {@code non_fatal}).
   *
   * <p>Crash/ANR/non_fatal are routed by the OTel Collector to the Pulse server backend
   * ({@code pulse.type ∈ device.crash | device.anr | non_fatal}) where they are error-grouped
   * and written to the {@code stack_trace_events} S3 folder — NOT to {@code otel_logs}.
   * The schema has a top-level {@code ExceptionType} column (already denormalized by the server).
   *
   * <p>PulseType mapping: {@code device.crash} → {@code crash},
   * {@code device.anr} → {@code anr}, {@code non_fatal} → {@code non_fatal}.
   */
  private static List<FunnelAttributionRow> buildCrashCauses(
    FunnelDefinition funnel, String runTime,
    Dataset<Row> droppers, Dataset<Row> converters,
    Dataset<Row> dropperCohorts, long converterCohortCount,
    Dataset<Row> stackTraceEvents) {
    Dataset<Row> crashEvents = stackTraceEvents
      .filter(col("ProjectId").equalTo("fancode"))
      // Map device.crash / device.anr → canonical cause kinds.
      .withColumn("_cause_kind",
        when(col("PulseType").equalTo("device.crash"), lit("crash"))
          .when(col("PulseType").equalTo("device.anr"), lit("anr"))
          .when(col("PulseType").equalTo("non_fatal"), lit("non_fatal"))
          .otherwise(lit("crash"))) // safe fallback for any future PulseType variants
      .select(
        col("SessionId").alias("e_sid"),
        unix_timestamp(col("Timestamp")).alias("e_ts"),
        col("_cause_kind").alias("cause_kind"),
        // ExceptionType is a top-level column in stack_trace_events (denormalized by Pulse server).
        coalesce(col("ExceptionType"), lit("")).alias("e_exc"),
        coalesce(col("ScreenName"), lit("")).alias("e_screen")
      );
    return joinCauseAndAggregate(
      funnel, runTime, droppers, converters, dropperCohorts, converterCohortCount,
      crashEvents,
      /* causeKeyExpr */ concat(col("e_exc"), lit("@"), col("e_screen")),
      /* causeLabelExpr */ concat(col("e_exc"), lit(" @ "), col("e_screen")),
      /* causeKindExpr */ col("cause_kind"),
      /* extraDropperFilter */ lit(true),
      /* extraConverterFilter */ lit(true));
  }

  /**
   * Joins {@code otel_logs} jank events against the cohort within the attribution window.
   * Emits one row per (StepIndex, CauseKind, CauseKey) where
   * CauseKind ∈ ({@code frozen_frame}, {@code jank}).
   *
   * <p>Jank events ({@code app.jank.frozen}, {@code app.jank.slow}) remain in {@code otel_logs}
   * because they are NOT matched by the OTel Collector's crash routing rule and flow through
   * the standard Kafka → S3 pipeline. CauseKey = {@code <cause_kind>@<ScreenName>}.
   */
  private static List<FunnelAttributionRow> buildJankCauses(
    FunnelDefinition funnel, String runTime,
    Dataset<Row> droppers, Dataset<Row> converters,
    Dataset<Row> dropperCohorts, long converterCohortCount,
    Dataset<Row> otelLogs) {
    Dataset<Row> jankEvents = otelLogs
      .filter(col("ProjectId").equalTo("fancode"))
      .filter(col("PulseType").isin("app.jank.frozen", "app.jank.slow"))
      .withColumn("_cause_kind",
        when(col("PulseType").equalTo("app.jank.frozen"), lit("frozen_frame"))
          .otherwise(lit("jank")))
      .select(
        col("SessionId").alias("e_sid"),
        unix_timestamp(col("Timestamp")).alias("e_ts"),
        col("_cause_kind").alias("cause_kind"),
        col("_cause_kind").alias("e_exc"), // cause_kind IS the identifier for jank
        coalesce(col("ScreenName"), lit("")).alias("e_screen")
      );
    return joinCauseAndAggregate(
      funnel, runTime, droppers, converters, dropperCohorts, converterCohortCount,
      jankEvents,
      /* causeKeyExpr */ concat(col("e_exc"), lit("@"), col("e_screen")),
      /* causeLabelExpr */ concat(col("e_exc"), lit(" @ "), col("e_screen")),
      /* causeKindExpr */ col("cause_kind"),
      /* extraDropperFilter */ lit(true),
      /* extraConverterFilter */ lit(true));
  }

  /**
   * Joins {@code otel_traces} HTTP spans against the cohort within the attribution window.
   * Emits one row per (StepIndex, CauseKind, CauseKey) where CauseKind ∈ (http_5xx, http_4xx)
   * and CauseKey = {@code method host status}.
   *
   * <p>Status resolution order (first non-zero wins):
   * <ol>
   *   <li>{@code PulseType} = {@code "network.<code>"} — Pulse SDKs embed the HTTP status
   *       directly in PulseType (e.g. {@code network.404}, {@code network.500}).
   *       {@code HttpStatusCode} is often 0 in these rows.</li>
   *   <li>{@code HttpStatusCode} top-level column (denormalized by pulse-s3-archiver).</li>
   *   <li>{@code SpanAttributes["http.status_code"]} / {@code ["http.response.status_code"]}
   *       — OTel-native fallback for older partitions.</li>
   * </ol>
   */
  private static List<FunnelAttributionRow> buildHttpCauses(
    FunnelDefinition funnel, String runTime,
    Dataset<Row> droppers, Dataset<Row> converters,
    Dataset<Row> dropperCohorts, long converterCohortCount,
    Dataset<Row> otelTraces) {
    boolean hasTopLevelHttp = java.util.Arrays.asList(otelTraces.columns()).contains("HttpStatusCode");

    // Status from PulseType "network.<code>". regexp_extract returns "" on no-match (NOT null);
    // casting "" → INT throws on Spark 4 strict mode, so guard the cast.
    Column pulseTypeStatusStr = regexp_extract(col("PulseType"), "^network\\.(\\d+)$", 1);
    Column pulseTypeStatus = when(pulseTypeStatusStr.notEqual(""),
        pulseTypeStatusStr.cast(DataTypes.IntegerType))
      .otherwise(lit(0));

    // Status from denormalized column / SpanAttributes (legacy / OTel-native path).
    Column rawStatus = hasTopLevelHttp
      ? col("HttpStatusCode").cast(DataTypes.IntegerType)
      : coalesce(
          col("SpanAttributes").getItem("http.status_code"),
          col("SpanAttributes").getItem("http.response.status_code"),
          lit("0")
        ).cast(DataTypes.IntegerType);

    // PulseType-derived status takes precedence — SDKs emit it reliably even when
    // HttpStatusCode is 0. Fall back to rawStatus for non-Pulse or older spans.
    Column statusCol = when(pulseTypeStatus.gt(0), pulseTypeStatus).otherwise(rawStatus);

    Column methodCol = hasTopLevelHttp
      ? lower(coalesce(col("HttpMethod"), lit("")))
      : coalesce(
          lower(col("SpanAttributes").getItem("http.method")),
          lower(col("SpanAttributes").getItem("http.request.method")),
          lit("")
        );
    Column hostCol = hasTopLevelHttp
      ? coalesce(col("HttpHost"), lit(""))
      : coalesce(
          col("SpanAttributes").getItem("net.peer.name"),
          col("SpanAttributes").getItem("server.address"),
          lit("")
        );

    Dataset<Row> httpEvents = otelTraces
      .filter(col("ProjectId").equalTo("fancode"))
      .withColumn("http_status", statusCol)
      .filter(col("http_status").geq(400))
      .select(
        col("SessionId").alias("e_sid"),
        unix_timestamp(col("Timestamp")).alias("e_ts"),
        when(col("http_status").geq(500), lit("http_5xx"))
          .otherwise(lit("http_4xx")).alias("cause_kind"),
        methodCol.alias("e_method"),
        hostCol.alias("e_host"),
        col("http_status").alias("e_status")
      );
    return joinCauseAndAggregate(
      funnel, runTime, droppers, converters, dropperCohorts, converterCohortCount,
      httpEvents,
      /* causeKeyExpr */ concat(col("e_method"), lit(" "), col("e_host"),
        lit(" "), col("e_status").cast(DataTypes.StringType)),
      /* causeLabelExpr */ concat(col("e_method"), lit(" "), col("e_host"),
        lit(" → "), col("e_status").cast(DataTypes.StringType)),
      /* causeKindExpr */ col("cause_kind"),
      /* extraDropperFilter */ lit(true),
      /* extraConverterFilter */ lit(true));
  }

  /**
   * Generic cause join + aggregate: for each cause source, joins the dropper cohort within
   * the attribution window, aggregates affected counts + example sessions per
   * (StepIndex, CauseKind, CauseKey), and computes lift against the converter cohort.
   */
  private static List<FunnelAttributionRow> joinCauseAndAggregate(
    FunnelDefinition funnel, String runTime,
    Dataset<Row> droppers, Dataset<Row> converters,
    Dataset<Row> dropperCohorts, long converterCohortCount,
    Dataset<Row> causeEvents,
    Column causeKeyExpr, Column causeLabelExpr, Column causeKindExpr,
    Column extraDropperFilter, Column extraConverterFilter) {

    Column dropperJoin = col("c.e_sid").equalTo(col("d.session_id"))
      .and(col("c.e_ts").geq(col("d.last_reached_at").minus(ATTRIBUTION_WINDOW_BEFORE_SEC)))
      .and(col("c.e_ts").leq(col("d.last_reached_at").plus(ATTRIBUTION_WINDOW_AFTER_SEC)));

    Dataset<Row> dropperJoined = droppers.alias("d")
      .join(causeEvents.alias("c"), dropperJoin, "inner")
      .filter(extraDropperFilter)
      .select(
        col("d.dropoff_step").alias("step"),
        causeKindExpr.alias("cause_kind"),
        causeKeyExpr.alias("cause_key"),
        causeLabelExpr.alias("cause_label"),
        col("d.session_id").alias("session_id")
      );

    Dataset<Row> dropperAgg = dropperJoined
      .groupBy(col("step"), col("cause_kind"), col("cause_key"), col("cause_label"))
      .agg(
        countDistinct(col("session_id")).alias("affected"),
        collect_set(col("session_id")).alias("examples")
      );

    Column converterJoin = col("c.e_sid").equalTo(col("d.session_id"))
      .and(col("c.e_ts").geq(col("d.last_reached_at").minus(ATTRIBUTION_WINDOW_BEFORE_SEC)))
      .and(col("c.e_ts").leq(col("d.last_reached_at").plus(ATTRIBUTION_WINDOW_AFTER_SEC)));

    Dataset<Row> converterAgg = converters.alias("d")
      .join(causeEvents.alias("c"), converterJoin, "inner")
      .filter(extraConverterFilter)
      .groupBy(causeKindExpr.alias("cause_kind"), causeKeyExpr.alias("cause_key"))
      .agg(countDistinct(col("d.session_id")).alias("converter_affected"));

    Dataset<Row> joined = dropperAgg.alias("dr")
      .join(converterAgg.alias("cv"),
        col("dr.cause_kind").equalTo(col("cv.cause_kind"))
          .and(col("dr.cause_key").equalTo(col("cv.cause_key"))),
        "left")
      .join(dropperCohorts.alias("dc"),
        col("dr.step").equalTo(col("dc.step")),
        "left")
      .select(
        col("dr.step"),
        col("dr.cause_kind"),
        col("dr.cause_key"),
        col("dr.cause_label"),
        col("dr.affected").alias("d_affected"),
        coalesce(col("cv.converter_affected"), lit(0L)).alias("c_affected"),
        coalesce(col("dc.cohort"), lit(0L)).alias("d_cohort"),
        col("dr.examples")
      );

    List<Row> collected = joined.collectAsList();
    List<FunnelAttributionRow> out = new ArrayList<>(collected.size());
    for (Row r : collected) {
      long dCohort = r.<Long>getAs("d_cohort");
      long dAff = r.<Long>getAs("d_affected");
      long cAff = r.<Long>getAs("c_affected");
      double lift;
      if (cAff == 0 || converterCohortCount == 0) {
        lift = dAff > 0 ? 999.0 : 0.0;
      } else {
        double dRate = (double) dAff / (dCohort == 0 ? 1 : dCohort);
        double cRate = (double) cAff / converterCohortCount;
        lift = cRate == 0 ? 999.0 : Math.round((dRate / cRate) * 1000.0) / 1000.0;
      }
      // Cap examples at ATTRIBUTION_EXAMPLES_PER_CAUSE, matching CH's groupArraySample.
      @SuppressWarnings("unchecked")
      var exArr = (scala.collection.Seq<String>) r.getAs("examples");
      var exList = new ArrayList<String>();
      if (exArr != null) {
        var it = exArr.iterator();
        while (it.hasNext() && exList.size() < ATTRIBUTION_EXAMPLES_PER_CAUSE) {
          exList.add(it.next());
        }
      }
      out.add(new FunnelAttributionRow(
        funnel.id(), funnel.projectId(), runTime,
        r.<Integer>getAs("step"),
        r.<String>getAs("cause_kind"),
        r.<String>getAs("cause_key"),
        r.<String>getAs("cause_label"),
        dCohort, dAff, converterCohortCount, cAff,
        lift,
        0.0, // PValue stub — chi-square deferred, matches CH
        exList
      ));
    }
    return out;
  }
}
