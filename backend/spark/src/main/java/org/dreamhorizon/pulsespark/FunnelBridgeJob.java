package org.dreamhorizon.pulsespark;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.dreamhorizon.pulsespark.model.FunnelDefinition;
import org.dreamhorizon.pulsespark.model.FunnelStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.countDistinct;
import static org.apache.spark.sql.functions.first;
import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.min;
import static org.apache.spark.sql.functions.min_by;
import static org.apache.spark.sql.functions.row_number;
import static org.apache.spark.sql.functions.unix_timestamp;
import static org.apache.spark.sql.functions.when;

/**
 * Writes funnel_session_state and funnel_user_state from Vector parquet using multi-attempt ordered funnel
 * semantics aligned with {@link FunnelComputeJob}.
 */
public final class FunnelBridgeJob {

  private static final Logger log = LoggerFactory.getLogger(FunnelBridgeJob.class);
  private static final DateTimeFormatter CH_DT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
  private static final int INSERT_CHUNK = 500;

  private FunnelBridgeJob() {}

  /**
   * Logs whether a frame has at least one row (single cheap action). Use for funnel bridge diagnostics.
   */
  static void logRowPresence(long funnelId, String stage, Dataset<Row> ds) {
    boolean empty = ds.isEmpty();
    log.info("Funnel {} - {}: {}", funnelId, stage, empty ? "no rows" : "has rows");
  }

  /** Full row count (triggers a Spark action); use sparingly for diagnostics. */
  static void logRowCount(long funnelId, String stage, Dataset<Row> ds) {
    long n = ds.count();
    log.info("Funnel {} - {}: {} rows", funnelId, stage, n);
  }

  private static String bridgeGrainLabel(String identityCol) {
    return SparkConstants.OtelLogColumn.SESSION_ID.equals(identityCol) ? "session grain" : "user grain";
  }

  public static void runBridge(SparkSession spark, MysqlRepository mysql, ClickHouseClient ch,
      Long referenceId, String otelLogsBucket, String runTime) throws Exception {
    List<FunnelDefinition> funnels = mysql.fetchFunnels(referenceId);
    if (funnels.isEmpty()) {
      log.info("No funnels for funnel bridge job");
      return;
    }
    log.info("Funnel bridge: {} funnel(s), referenceId={}", funnels.size(), referenceId);

    if (referenceId != null) {
      var funnel = funnels.get(0);
      if (funnel.startTime() == null || funnel.endTime() == null) {
        throw new IllegalArgumentException(
            "FUNNEL_BRIDGE requires start_time and end_time for funnel_id=" + funnel.id());
      }
      var startDt = funnel.startTime().toLocalDateTime();
      var endDt = funnel.endTime().toLocalDateTime();
      var s3Base = SparkConstants.OtelLogsS3.basePath(otelLogsBucket, funnel.projectId());
      log.info("Funnel bridge {} window [{} -> {}]", funnel.id(), startDt, endDt);
      log.info("Funnel bridge {} S3 otel_logs base: {}", funnel.id(), s3Base);
      Dataset<Row> raw = FunnelComputeJob.readS3ByHours(spark, s3Base, startDt, endDt);
      if (raw == null) {
        log.warn("No S3 data for funnel bridge {}", funnel.id());
        return;
      }
      raw = FunnelComputeJob.prepareRaw(raw, funnel.projectId()).cache();
      try {
        long startEpoch = startDt.toEpochSecond(ZoneOffset.UTC);
        long endEpoch = endDt.toEpochSecond(ZoneOffset.UTC);
        processOneFunnel(ch, funnel, raw, runTime, startEpoch, endEpoch);
      } finally {
        raw.unpersist();
      }
    } else {
      Map<String, List<FunnelDefinition>> byProject = new HashMap<>();
      for (var f : funnels) {
        byProject.computeIfAbsent(f.projectId(), k -> new ArrayList<>()).add(f);
      }
      for (var entry : byProject.entrySet()) {
        processProjectBulk(spark, ch, entry.getKey(), entry.getValue(), otelLogsBucket, runTime);
      }
    }
  }

  private static void processProjectBulk(SparkSession spark, ClickHouseClient ch,
      String projectId, List<FunnelDefinition> funnels, String otelLogsBucket, String runTime) {
    int maxDays = funnels.stream().mapToInt(FunnelDefinition::dateRange).max().orElse(7);
    var endDate = java.time.LocalDate.parse(runTime.substring(0, 10));
    var startDate = endDate.minusDays(maxDays - 1L);
    var s3Base = SparkConstants.OtelLogsS3.basePath(otelLogsBucket, projectId);
    log.info("Funnel bridge project {} reading S3 [{} -> {}] for {} funnel(s)",
        projectId, startDate, endDate, funnels.size());
    log.info("Funnel bridge project {} S3 otel_logs base: {}", projectId, s3Base);
    Dataset<Row> raw = FunnelComputeJob.readS3ByDateRange(spark, s3Base, startDate, endDate);
    if (raw == null) {
      log.warn("No S3 data for funnel bridge project {}", projectId);
      return;
    }
    raw = FunnelComputeJob.prepareRaw(raw, projectId).cache();
    try {
      for (var funnel : funnels) {
        processOneFunnel(ch, funnel, raw, runTime, null, null);
      }
    } finally {
      raw.unpersist();
    }
  }

  private static void processOneFunnel(ClickHouseClient ch, FunnelDefinition funnel,
      Dataset<Row> raw, String runTime, Long startEpochSeconds, Long endEpochSeconds) {
    if (funnel.isUnordered()) {
      log.info("Funnel {} bridge skipped (unordered funnel)", funnel.id());
      return;
    }
    int numSteps = funnel.steps().size();
    if (numSteps == 0) {
      log.info("Funnel {} bridge skipped (no steps)", funnel.id());
      return;
    }

    Dataset<Row> df = timeSlice(raw, funnel, runTime, startEpochSeconds, endEpochSeconds);
    logRowCount(funnel.id(), "after time slice", df);
    df = FunnelComputeJob.applyGlobalFilters(df, funnel.globalFilters());
    logRowCount(funnel.id(), "after global filters", df);
    df = FunnelComputeJob.narrowToStepEventUnion(df, funnel);
    logRowPresence(funnel.id(), "after upfront step-filter union (any funnel step event)", df);

    // --- Session grain (always): chains keyed by session_id
    Dataset<Row> sessionAttempts = computeAttemptOutcomes(df, funnel,
        SparkConstants.OtelLogColumn.SESSION_ID, funnel.windowSeconds());
    Dataset<Row> sessionRanked = withExplicitSessionId(rankWinnerAttempts(sessionAttempts), true);
    streamSessionInserts(ch, sessionRanked, funnel, runTime, numSteps);

    // --- User grain (UNIQUE_USERS funnels only): chains keyed by installation_id
    boolean uniqueUsers = SparkConstants.DefinitionModes.JOURNEY_UNIQUE_USERS.equalsIgnoreCase(funnel.mode());
    if (uniqueUsers) {
      Dataset<Row> userAttempts = computeAttemptOutcomes(df, funnel,
          SparkConstants.Derived.INSTALLATION_ID, funnel.windowSeconds());
      Dataset<Row> userRanked = withExplicitSessionId(rankWinnerAttempts(userAttempts), false);
      Dataset<Row> attemptsPerUser = df.select(
              col(SparkConstants.Derived.INSTALLATION_ID),
              col(SparkConstants.OtelLogColumn.SESSION_ID))
          .filter(col(SparkConstants.Derived.INSTALLATION_ID).isNotNull()
              .and(col(SparkConstants.Derived.INSTALLATION_ID).notEqual("")))
          .groupBy(col(SparkConstants.Derived.INSTALLATION_ID))
          .agg(countDistinct(col(SparkConstants.OtelLogColumn.SESSION_ID)).alias("session_attempts"))
          .select(
              col(SparkConstants.Derived.INSTALLATION_ID).alias("_inst_join"),
              col("session_attempts"));

      Dataset<Row> userJoined = userRanked.join(
          attemptsPerUser,
          userRanked.col("identity").equalTo(col("_inst_join")),
          "left")
          .drop("_inst_join")
          .withColumn("session_attempts", coalesce(col("session_attempts"), lit(0)));
      streamUserInserts(ch, userJoined, funnel, runTime, numSteps);
    }
  }

  private static Dataset<Row> timeSlice(Dataset<Row> raw, FunnelDefinition funnel, String runTime,
      Long startEpochSeconds, Long endEpochSeconds) {
    Dataset<Row> df;
    if (startEpochSeconds != null && endEpochSeconds != null) {
      df = raw.filter(
          unix_timestamp(col(SparkConstants.OtelLogColumn.TIMESTAMP)).geq(lit(startEpochSeconds))
              .and(unix_timestamp(col(SparkConstants.OtelLogColumn.TIMESTAMP)).leq(lit(endEpochSeconds))));
    } else {
      var endDate = java.time.LocalDate.parse(runTime.substring(0, 10));
      var startDate = endDate.minusDays(funnel.dateRange() - 1L);
      df = raw.filter(
          col(SparkConstants.OtelLogColumn.TIMESTAMP).cast(DataTypes.DateType).geq(lit(startDate.toString()))
              .and(col(SparkConstants.OtelLogColumn.TIMESTAMP).cast(DataTypes.DateType).leq(lit(endDate.toString()))));
    }
    return df;
  }

  /**
   * Emits one row per multi-attempt chain outcome (survivors + deaths at each step transition).
   */
  static Dataset<Row> computeAttemptOutcomes(Dataset<Row> df, FunnelDefinition funnel,
      String identityCol, long windowSecs) {
    List<FunnelStep> steps = funnel.steps();
    int numSteps = steps.size();
    Dataset<Row> step0 = FunnelComputeJob.stepEventsWithSession(df, steps.get(0), identityCol);
    logRowPresence(
        funnel.id(),
        "after step filters (step 0: %s, %s)".formatted(steps.get(0).eventName(), bridgeGrainLabel(identityCol)),
        step0);
    Dataset<Row> current = step0.select(
            col(SparkConstants.AnalyticsDataset.IDENTITY).alias("identity"),
            col(SparkConstants.AnalyticsDataset.TS).alias("ts0"),
            col(SparkConstants.AnalyticsDataset.TS).alias("ts_prev"),
            col(SparkConstants.AnalyticsDataset.BRIDGE_EVT_SESSION_ID).alias("sess_prev"),
            col(SparkConstants.AnalyticsDataset.BRIDGE_USER_ID_COL).alias("bridge_user_id"))
        .cache();

    if (numSteps == 1) {
      Dataset<Row> out = current.select(
          col("identity"),
          col("ts0"),
          lit(0).alias("lastReachedStep"),
          lit(true).alias("converted"),
          col("ts_prev").alias("lastReachedAtTs"),
          col("sess_prev").alias("lastSessionId"),
          col("bridge_user_id").alias("bridge_user_id"));
      current.unpersist();
      return out;
    }

    List<Dataset<Row>> parts = new ArrayList<>();

    for (int i = 1; i < numSteps; i++) {
      Dataset<Row> stepEvtsFull = FunnelComputeJob.stepEventsWithSession(df, steps.get(i), identityCol);
      logRowPresence(
          funnel.id(),
          "after step filters (step %d: %s, %s)".formatted(i, steps.get(i).eventName(), bridgeGrainLabel(identityCol)),
          stepEvtsFull);
      Dataset<Row> stepEvts = stepEvtsFull
          .select(
              col(SparkConstants.AnalyticsDataset.IDENTITY).alias("bid"),
              col(SparkConstants.AnalyticsDataset.TS).alias("bts"),
              col(SparkConstants.AnalyticsDataset.BRIDGE_EVT_SESSION_ID).alias("bsess"));

      Column joinCond = col("prev.identity").equalTo(col("next.bid"))
          .and(col("next.bts").gt(col("prev.ts_prev")))
          .and(col("next.bts").leq(col("prev.ts0").plus(windowSecs)));

      Dataset<Row> joined = current.alias("prev")
          .join(stepEvts.alias("next"), joinCond, "inner");

      Dataset<Row> survived = joined.groupBy(col("prev.identity"), col("prev.ts0"))
          .agg(
              min(col("next.bts")).alias("ts_prev"),
              min_by(col("next.bsess"), col("next.bts")).alias("sess_prev"),
              first(col("prev.bridge_user_id"), true).alias("bridge_user_id"));

      Dataset<Row> survKeys = survived.select(
          col("identity").alias("_match_identity"),
          col("ts0").alias("_match_ts0"));
      Dataset<Row> dead = current.join(
          survKeys,
          current.col("identity").equalTo(col("_match_identity"))
              .and(current.col("ts0").equalTo(col("_match_ts0"))),
          "leftanti");

      Dataset<Row> deadOut = dead.select(
          col("identity"),
          col("ts0"),
          lit(i - 1).alias("lastReachedStep"),
          lit(false).alias("converted"),
          col("ts_prev").alias("lastReachedAtTs"),
          col("sess_prev").alias("lastSessionId"),
          col("bridge_user_id").alias("bridge_user_id"));
      parts.add(deadOut);

      Dataset<Row> prevCur = current;
      current = survived.select(
              col("identity"),
              col("ts0"),
              col("ts_prev"),
              col("sess_prev"),
              col("bridge_user_id"))
          .cache();
      prevCur.unpersist();
    }

    Dataset<Row> finalSurvivors = current.select(
        col("identity"),
        col("ts0"),
        lit(numSteps - 1).alias("lastReachedStep"),
        lit(true).alias("converted"),
        col("ts_prev").alias("lastReachedAtTs"),
        col("sess_prev").alias("lastSessionId"),
        col("bridge_user_id").alias("bridge_user_id"));
    current.unpersist();

    Dataset<Row> all = parts.get(0);
    for (int k = 1; k < parts.size(); k++) {
      all = all.union(parts.get(k));
    }
    all = all.union(finalSurvivors);
    return all;
  }

  /**
   * Canonical session id for ClickHouse session/user state: always {@code session_id} on the ranked
   * frame. When {@code identity} is session grain, fall back to {@code identity} if {@code lastSessionId}
   * is null; for UNIQUE_USERS grain, {@code identity} is installation id so never use it here.
   */
  private static Dataset<Row> withExplicitSessionId(Dataset<Row> ranked, boolean identityIsSessionId) {
    if (identityIsSessionId) {
      return ranked.withColumn(SparkConstants.OtelLogColumn.SESSION_ID,
          coalesce(col("lastSessionId"), col("identity")));
    }
    return ranked.withColumn(SparkConstants.OtelLogColumn.SESSION_ID, col("lastSessionId"));
  }

  /**
   * Pick winning attempt per identity: prefer converter, then deepest partial, then earliest ts0.
   */
  static Dataset<Row> rankWinnerAttempts(Dataset<Row> attempts) {
    WindowSpec win = Window.partitionBy(col("identity"))
        .orderBy(col("converted").desc(), col("lastReachedStep").desc(), col("ts0").asc());
    return attempts
        .withColumn("_rn", row_number().over(win))
        .filter(col("_rn").equalTo(lit(1)))
        .drop("_rn");
  }

  static Column stepNameForIndex(Column idxCol, List<FunnelStep> steps) {
    Column expr = lit(null).cast(DataTypes.StringType);
    for (int i = steps.size() - 1; i >= 0; i--) {
      expr = when(idxCol.equalTo(lit(i)), lit(steps.get(i).eventName())).otherwise(expr);
    }
    return expr;
  }

  private static void streamSessionInserts(ClickHouseClient ch, Dataset<Row> ranked,
      FunnelDefinition funnel, String runTime, int numSteps) {
    Column lr = when(col("converted"), lit(numSteps - 1)).otherwise(col("lastReachedStep"));
    Dataset<Row> projected = ranked
        .withColumn("_lr", lr)
        .withColumn("_sn", stepNameForIndex(col("_lr"), funnel.steps()))
        .withColumn("_drop",
            when(col("converted"), lit(-1)).otherwise(col("lastReachedStep").plus(1)));

    Iterator<Row> it = projected.toLocalIterator();
    List<String> batch = new ArrayList<>(INSERT_CHUNK);
    long n = 0;
    while (it.hasNext()) {
      Row row = it.next();
      batch.add(formatSessionValue(row, funnel.id(), funnel.projectId(), runTime));
      n++;
      if (batch.size() >= INSERT_CHUNK) {
        ch.insertFunnelSessionState(batch, INSERT_CHUNK);
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      ch.insertFunnelSessionState(batch, INSERT_CHUNK);
    }
    log.info("Funnel {} funnel_session_state: inserted {} rows", funnel.id(), n);
  }

  private static void streamUserInserts(ClickHouseClient ch, Dataset<Row> ranked,
      FunnelDefinition funnel, String runTime, int numSteps) {
    Column lr = when(col("converted"), lit(numSteps - 1)).otherwise(col("lastReachedStep"));
    Dataset<Row> projected = ranked
        .withColumn("_lr", lr)
        .withColumn("_drop",
            when(col("converted"), lit(-1)).otherwise(col("lastReachedStep").plus(1)));

    Iterator<Row> it = projected.toLocalIterator();
    List<String> batch = new ArrayList<>(INSERT_CHUNK);
    long n = 0;
    while (it.hasNext()) {
      Row row = it.next();
      batch.add(formatUserValue(row, funnel.id(), funnel.projectId(), runTime));
      n++;
      if (batch.size() >= INSERT_CHUNK) {
        ch.insertFunnelUserState(batch, INSERT_CHUNK);
        batch.clear();
      }
    }
    if (!batch.isEmpty()) {
      ch.insertFunnelUserState(batch, INSERT_CHUNK);
    }
    log.info("Funnel {} funnel_user_state: inserted {} rows", funnel.id(), n);
  }

  private static String formatSessionValue(Row row, long funnelId, String projectId, String runTime) {
    String sid = esc(row.getAs(SparkConstants.OtelLogColumn.SESSION_ID));
    String uid = esc(row.getAs("bridge_user_id"));
    int lr = row.getAs("_lr") instanceof Integer i ? i : ((Number) row.getAs("_lr")).intValue();
    String stepName = esc(row.getAs("_sn"));
    long lastTs = ((Number) row.getAs("lastReachedAtTs")).longValue();
    int drop = row.getAs("_drop") instanceof Integer i ? i : ((Number) row.getAs("_drop")).intValue();
    String tsLit = esc(CH_DT.format(Instant.ofEpochSecond(lastTs)));
    // Parenthesize before .formatted(...): "+" binds looser than ".", otherwise only the NULL
    // tail is formatted and the (%d,'%s',...) prefix is concatenated verbatim.
    return ("(%d,'%s','%s','%s','%s',%d,'%s','%s',%d,"
            + "NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL)")
        .formatted(funnelId, esc(projectId), esc(runTime), sid, uid, lr, stepName, tsLit, drop);
  }

  private static String formatUserValue(Row row, long funnelId, String projectId, String runTime) {
    String uid = esc(row.getAs("identity"));
    int maxReached = row.getAs("_lr") instanceof Integer i ? i : ((Number) row.getAs("_lr")).intValue();
    int drop = row.getAs("_drop") instanceof Integer i ? i : ((Number) row.getAs("_drop")).intValue();
    String canonSess = esc(row.getAs(SparkConstants.OtelLogColumn.SESSION_ID));
    long lastTs = ((Number) row.getAs("lastReachedAtTs")).longValue();
    String tsLit = esc(CH_DT.format(Instant.ofEpochSecond(lastTs)));
    long sessAttempts = row.getAs("session_attempts") instanceof Number nb ? nb.longValue() : 0L;
    return "(%d,'%s','%s','%s',%d,%d,'%s','%s',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,%d)"
        .formatted(funnelId, esc(projectId), esc(runTime), uid, maxReached, drop, canonSess, tsLit,
            sessAttempts);
  }

  private static String esc(String s) {
    if (s == null) {
      return "";
    }
    return s.replace("\\", "\\\\").replace("'", "\\'");
  }
}
