package org.dreamhorizon.pulsespark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
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

    static final String[] READ_COLS = {
            "event_name", "project_id", "session_id", "timestamp",
            "os_name", "os_version", "app_build_name", "device_manufacturer",
            "device_model_identifier", "network_carrier_icc", "screen_name", "service_name"
    };

    public static void runFunnels(SparkSession spark, MysqlRepository mysql, ClickHouseClient ch,
                                   Long referenceId, String s3BucketPrefix, String runTime) throws Exception {
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
            var endDt   = funnel.endTime().toLocalDateTime();
            var s3Base  = "s3a://" + s3BucketPrefix + funnel.projectId() + "/vector-logs/";

            log.info("Funnel {} window [{} -> {}]", funnel.id(), startDt, endDt);
            Dataset<Row> raw = readS3ByHours(spark, s3Base, startDt, endDt);
            if (raw == null) {
                log.warn("No S3 data for funnel {}", funnel.id());
                return;
            }
            raw = prepareRaw(raw, funnel.projectId()).cache();
            try {
                long startEpoch = startDt.toEpochSecond(ZoneOffset.UTC);
                long endEpoch   = endDt.toEpochSecond(ZoneOffset.UTC);
                var results = computeFunnel(raw, funnel, runTime, startEpoch, endEpoch);
                ch.insertFunnelResults(results);
                log.info("Funnel {}: wrote {} result rows", funnel.id(), results.size());
                // Emit per-session bridge + (optional) per-user rollup so downstream
                // drop-off attribution has a SessionId anchor into OTel tables.
                emitBridgeAndRollup(raw, funnel, runTime, startEpoch, endEpoch, ch);
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
        int maxDays   = funnels.stream().mapToInt(FunnelDefinition::dateRange).max().orElse(7);
        var endDate   = LocalDate.parse(runTime.substring(0, 10));
        var startDate = endDate.minusDays(maxDays - 1L);
        var s3Base    = "s3a://" + s3Prefix + projectId + "/vector-logs/";

        log.info("Project {} reading S3 [{} -> {}] for {} funnel(s)", projectId, startDate, endDate, funnels.size());
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
                    emitBridgeAndRollup(raw, funnel, runTime, null, null, ch);
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
        var steps       = funnel.steps();
        int numSteps    = steps.size();

        Dataset<Row> df;
        if (startEpochSeconds != null && endEpochSeconds != null) {
            df = raw.filter(
                    unix_timestamp(col("timestamp")).geq(lit(startEpochSeconds))
                            .and(unix_timestamp(col("timestamp")).leq(lit(endEpochSeconds)))
            );
        } else {
            var endDate   = LocalDate.parse(runTime.substring(0, 10));
            var startDate = endDate.minusDays(funnel.dateRange() - 1L);
            df = raw.filter(
                    col("timestamp").cast(DataTypes.DateType).geq(lit(startDate.toString()))
                            .and(col("timestamp").cast(DataTypes.DateType).leq(lit(endDate.toString())))
            );
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
     *         Returns -1 at index i if there were no users at that step.
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
            String tsCol  = "ts_" + i;
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
     * Writes {@code funnel_session_state} and (for UNIQUE_USERS funnels)
     * {@code funnel_user_state} rows for the given run.
     *
     * <p>The bridge is <b>always computed per-session</b>, regardless of the funnel's
     * {@code mode}. OTel signals (crashes, traces, replay) are inherently per-session
     * in time — a per-user rollup has no single moment to anchor to. For UNIQUE_USERS
     * funnels we derive a per-user row from the session bridge by picking each user's
     * canonical session (the one that reached the furthest step; most recent ts on ties).
     *
     * <p>Unordered funnels currently skip bridge emission — the "farthest-step"
     * concept is well-defined for ordered sequences but needs a separate semantic for
     * any-order funnels. Tracked as a follow-up.
     */
    private static void emitBridgeAndRollup(Dataset<Row> raw, FunnelDefinition funnel,
                                            String runTime,
                                            Long startEpochSeconds, Long endEpochSeconds,
                                            ClickHouseClient ch) {
        if (funnel.isUnordered()) {
            log.info("Funnel {} is UNORDERED — skipping bridge/rollup emission", funnel.id());
            return;
        }

        var steps = funnel.steps();
        int numSteps = steps.size();
        if (numSteps == 0) {
            return;
        }
        long windowSecs = funnel.windowSeconds();

        // Apply the same window/date filter + global filters that computeFunnel uses so the
        // bridge cohort matches the aggregate counts in funnel_results exactly.
        Dataset<Row> df;
        if (startEpochSeconds != null && endEpochSeconds != null) {
            df = raw.filter(
                    unix_timestamp(col("timestamp")).geq(lit(startEpochSeconds))
                            .and(unix_timestamp(col("timestamp")).leq(lit(endEpochSeconds)))
            );
        } else {
            var endDate   = LocalDate.parse(runTime.substring(0, 10));
            var startDate = endDate.minusDays(funnel.dateRange() - 1L);
            df = raw.filter(
                    col("timestamp").cast(DataTypes.DateType).geq(lit(startDate.toString()))
                            .and(col("timestamp").cast(DataTypes.DateType).leq(lit(endDate.toString())))
            );
        }
        df = applyFilters(df, funnel.globalFilters()).cache();

        try {
            // Traversal identical to computeFunnel's ordered path, but identity is ALWAYS
            // session_id — the bridge is a per-session structure even for UNIQUE_USERS funnels.
            Dataset<Row> current = stepEvents(df, steps.get(0), "session_id")
                    .select(col("identity"), col("ts").alias("ts0"), col("ts").alias("ts_prev"))
                    .cache();

            // perStep[i] = distinct (session_id, ts0, ts_at_step_i) that reached step i
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

            // Union perStep with step_idx label, then pick each session's best attempt:
            // max(step_idx), most recent ts_at_step, earliest ts0 on further ties.
            Dataset<Row> unionAll = null;
            for (int i = 0; i < perStep.size(); i++) {
                Dataset<Row> withIdx = perStep.get(i).withColumn("step_idx", lit(i));
                unionAll = (unionAll == null) ? withIdx : unionAll.union(withIdx);
            }
            if (unionAll == null) {
                log.warn("Funnel {}: no session reached step 0 — skipping bridge", funnel.id());
                return;
            }

            WindowSpec bestWin = Window.partitionBy("identity")
                    .orderBy(col("step_idx").desc(), col("ts_at_step").desc(), col("ts0").asc());
            Dataset<Row> bestPerSession = unionAll
                    .withColumn("_rn", row_number().over(bestWin))
                    .filter(col("_rn").equalTo(1))
                    .drop("_rn")
                    .cache();

            // Join back to raw to hydrate dimension carryover (screen, app version, etc.)
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

            int finalStepIdx = numSteps - 1;
            List<Row> collected = hydrated.collectAsList();
            bestPerSession.unpersist();
            hydrated.unpersist();

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
                        "", // TraceIdAtDropoff — not in vector-log parquet yet; hydrate via OTel join if needed
                        r.<String>getAs("app_version"),
                        r.<String>getAs("os_name"),
                        r.<String>getAs("os_version"),
                        r.<String>getAs("platform"),
                        r.<String>getAs("device_model"),
                        r.<String>getAs("network_provider"),
                        "" // GeoCountry — not yet carried on vector-log parquet
                ));
            }
            ch.insertFunnelSessionState(bridgeRows);
            log.info("Funnel {}: wrote {} session_state rows", funnel.id(), bridgeRows.size());

            // Only UNIQUE_USERS funnels get the user rollup. For SESSIONS funnels, the
            // side-panel joins directly on funnel_session_state.
            if (!"SESSIONS".equalsIgnoreCase(funnel.mode())) {
                List<FunnelUserState> userRows = rollupToUsers(bridgeRows, finalStepIdx);
                ch.insertFunnelUserState(userRows);
                log.info("Funnel {}: wrote {} user_state rows", funnel.id(), userRows.size());
            }
        } finally {
            df.unpersist();
        }
    }

    /**
     * Groups session-bridge rows by {@code userId} and picks the canonical session per user:
     * max reached step, most recent {@code lastReachedAt} on ties. Empty user IDs (anonymous
     * sessions) are skipped — the user rollup is only meaningful for identified users.
     */
    private static List<FunnelUserState> rollupToUsers(List<FunnelSessionState> sessionRows, int finalStepIdx) {
        Map<String, FunnelSessionState> bestByUser = new HashMap<>();
        Map<String, Integer> attemptsByUser = new HashMap<>();
        for (var s : sessionRows) {
            if (s.userId() == null || s.userId().isEmpty()) continue;
            attemptsByUser.merge(s.userId(), 1, Integer::sum);
            var incumbent = bestByUser.get(s.userId());
            if (incumbent == null
                    || s.lastReachedStep() > incumbent.lastReachedStep()
                    || (s.lastReachedStep() == incumbent.lastReachedStep()
                            && s.lastReachedAtEpochSec() > incumbent.lastReachedAtEpochSec())) {
                bestByUser.put(s.userId(), s);
            }
        }

        var out = new ArrayList<FunnelUserState>(bestByUser.size());
        for (var entry : bestByUser.entrySet()) {
            var canonical = entry.getValue();
            int maxStep = canonical.lastReachedStep();
            int dropoff = maxStep >= finalStepIdx ? -1 : maxStep + 1;
            out.add(new FunnelUserState(
                    canonical.funnelId(), canonical.projectId(), canonical.runTime(),
                    entry.getKey(),
                    maxStep,
                    dropoff,
                    canonical.sessionId(),
                    canonical.lastReachedAtEpochSec(),
                    canonical.traceIdAtDropoff(),
                    canonical.screenAtDropoff(),
                    canonical.appVersion(),
                    canonical.osName(),
                    canonical.osVersion(),
                    canonical.platform(),
                    canonical.deviceModel(),
                    canonical.networkProvider(),
                    canonical.geoCountry(),
                    attemptsByUser.getOrDefault(entry.getKey(), 1)
            ));
        }
        return out;
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
            String parquetField = FilterFieldMapper.toParquetColumn(filter.field());
            var fieldCol = col(parquetField);
            var vals = filter.value().toArray();
            if (vals.length == 0) {
                log.warn("Skipping filter: empty value list for field '{}' (resolved='{}')", filter.field(), parquetField);
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

    static Dataset<Row> readS3ByDateRange(SparkSession spark, String s3BasePath,
                                           LocalDate startDate, LocalDate endDate) {
        Dataset<Row> combined = null;
        int attempted = 0, loaded = 0;
        for (var d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            attempted++;
            var ds = loadPath(spark, s3BasePath + d + "/");
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

    static Dataset<Row> readS3ByHours(SparkSession spark, String s3BasePath,
                                       LocalDateTime startDt, LocalDateTime endDt) {
        var cursor  = startDt.withMinute(0).withSecond(0).withNano(0);
        var endHour = endDt.withMinute(59).withSecond(59).withNano(0);
        Dataset<Row> combined = null;
        int attempted = 0, loaded = 0;
        while (!cursor.isAfter(endHour)) {
            attempted++;
            var path = s3BasePath + cursor.toLocalDate() + "/" + String.format("%02d", cursor.getHour()) + "/";
            var ds = loadPath(spark, path);
            if (ds != null) {
                loaded++;
                combined = combined == null ? ds : combined.union(ds);
            }
            cursor = cursor.plusHours(1);
        }
        if (combined == null) {
            log.warn("No S3 data for hour range {} to {} (attempted={}, loaded={})",
                    startDt, endDt, attempted, loaded);
        } else {
            log.info("Loaded S3 hour range {} to {} (attempted={}, loaded={})",
                    startDt, endDt, attempted, loaded);
        }
        return combined;
    }

    private static Dataset<Row> loadPath(SparkSession spark, String path) {
        try {
            var ds = spark.read()
                    .format("parquet")
                    .option("recursiveFileLookup", "true")
                    .option("mergeSchema", "true")
                    .load(path);
            return ds.select(buildReadExprs(ds.columns()));
        } catch (Exception e) {
            log.warn("Skipping S3 path {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static Dataset<Row> prepareRaw(Dataset<Row> ds, String projectId) {
        return ds.filter(col("project_id").equalTo(projectId));
    }

    static Column[] buildReadExprs(String[] availableColumns) {
        var available = new java.util.HashSet<>(java.util.Arrays.asList(availableColumns));
        var cols = new ArrayList<Column>();
        for (var c : READ_COLS) {
            cols.add(available.contains(c) ? col(c) : lit(null).cast(DataTypes.StringType).alias(c));
        }
        cols.add(coalesce(
                get_json_object(col("props"), "$.user_id"),
                get_json_object(col("props"), "$['app.installation.id']")
        ).alias("user_id"));
        return cols.toArray(Column[]::new);
    }
}
