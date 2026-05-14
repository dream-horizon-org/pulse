package org.dreamhorizon.pulsespark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.dreamhorizon.pulsespark.model.FunnelDefinition;
import org.dreamhorizon.pulsespark.model.FunnelFilter;
import org.dreamhorizon.pulsespark.model.FunnelResult;
import org.dreamhorizon.pulsespark.model.FunnelStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
            Set<String> propKeys = FunnelFilterPropKeys.collectFromFunnel(funnel);
            Dataset<Row> raw = readS3ByHours(spark, s3Base, startDt, endDt, propKeys);
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
        Set<String> propKeys = FunnelFilterPropKeys.collectFromFunnels(funnels);
        Dataset<Row> raw = readS3ByDateRange(spark, s3Base, startDate, endDate, propKeys);
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
        df = applyGlobalFilters(df, funnel.globalFilters());
        df = narrowToStepEventUnion(df, funnel);

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

    private static Dataset<Row> narrowToStepEventUnion(Dataset<Row> df, FunnelDefinition funnel) {
        List<FunnelStep> steps = funnel.steps();
        if (steps.isEmpty()) {
            return df;
        }
        Column union = null;
        for (FunnelStep step : steps) {
            Column branch = col("event_name").equalTo(lit(step.eventName()));
            for (FunnelFilter sf : step.stepFilters()) {
                Optional<Column> fc = filterToPredicate(sf, "[funnel-upfront]");
                if (fc.isPresent()) {
                    branch = branch.and(fc.get());
                }
            }
            union = (union == null) ? branch : union.or(branch);
        }
        return df.filter(union);
    }

    /**
     * Applies attribute filters from funnel definitions against parquet columns or uplifted
     * {@code props} fields (same resolution as read projection).
     */
    public static Dataset<Row> applyGlobalFilters(Dataset<Row> df, List<FunnelFilter> filters) {
        return applyFiltersPhase(df, filters, "[global]");
    }

    static Dataset<Row> applyStepFilters(Dataset<Row> df, List<FunnelFilter> filters) {
        return applyFiltersPhase(df, filters, "[step]");
    }

    private static Dataset<Row> applyFiltersPhase(Dataset<Row> df, List<FunnelFilter> filters, String logCtx) {
        Dataset<Row> out = df;
        for (var filter : filters) {
            Optional<Column> pred = filterToPredicate(filter, logCtx);
            if (pred.isPresent()) {
                out = out.filter(pred.get());
            }
        }
        return out;
    }

    /**
     * Single-condition predicate for one {@link FunnelFilter}; empty when the filter is skipped (warn logged).
     */
    private static Optional<Column> filterToPredicate(FunnelFilter filter, String logCtx) {
        if (filter.field() == null || filter.field().isBlank()) {
            log.warn("{} Skipping filter: field is null or blank", logCtx);
            return Optional.empty();
        }
        String parquetField = FilterFieldMapper.toParquetColumn(filter.field());
        Column fieldCol = col(parquetField);
        Object[] vals = filter.value().toArray();
        if (vals.length == 0) {
            log.warn("{} Skipping filter: empty value list for field '{}' (resolved='{}')",
                    logCtx, filter.field(), parquetField);
            return Optional.empty();
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
                log.warn("{} Unknown filter operator '{}' (normalized='{}') for field '{}' — filter skipped",
                        logCtx, filter.operator(), op, filter.field());
                yield lit(true);
            }
        };
        return Optional.of(cond);
    }

    private static Dataset<Row> stepEvents(Dataset<Row> df, FunnelStep step, String identityCol) {
        Dataset<Row> filtered = df.filter(col("event_name").equalTo(step.eventName()));
        filtered = applyStepFilters(filtered, step.stepFilters());
        return filtered
                .select(
                        col(identityCol).alias("identity"),
                        unix_timestamp(col("timestamp")).alias("ts")
                )
                .filter(col("identity").isNotNull().and(col("identity").notEqual("")));
    }

    static Dataset<Row> readS3ByDateRange(SparkSession spark, String s3BasePath,
                                           LocalDate startDate, LocalDate endDate,
                                           Set<String> propKeys) {
        Dataset<Row> combined = null;
        int attempted = 0, loaded = 0;
        for (var d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            attempted++;
            var ds = loadPath(spark, s3BasePath + d + "/", propKeys);
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
                                       LocalDateTime startDt, LocalDateTime endDt,
                                       Set<String> propKeys) {
        var cursor  = startDt.withMinute(0).withSecond(0).withNano(0);
        var endHour = endDt.withMinute(59).withSecond(59).withNano(0);
        Dataset<Row> combined = null;
        int attempted = 0, loaded = 0;
        while (!cursor.isAfter(endHour)) {
            attempted++;
            var path = s3BasePath + cursor.toLocalDate() + "/" + String.format("%02d", cursor.getHour()) + "/";
            var ds = loadPath(spark, path, propKeys);
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

    private static Dataset<Row> loadPath(SparkSession spark, String path, Set<String> propKeys) {
        try {
            var ds = spark.read()
                    .format("parquet")
                    .option("recursiveFileLookup", "true")
                    .option("mergeSchema", "true")
                    .load(path);
            return ds.select(buildReadExprs(ds.columns(), propKeys));
        } catch (Exception e) {
            log.warn("Skipping S3 path {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static Dataset<Row> prepareRaw(Dataset<Row> ds, String projectId) {
        return ds.filter(col("project_id").equalTo(projectId));
    }

    static Column[] buildReadExprs(String[] availableColumns, Set<String> propKeys) {
        var available = new java.util.HashSet<>(java.util.Arrays.asList(availableColumns));
        boolean hasProps = available.contains("props");
        Column propsExpr = hasProps ? col("props") : lit(null).cast(DataTypes.StringType);
        var cols = new ArrayList<Column>();
        for (var c : READ_COLS) {
            cols.add(available.contains(c) ? col(c) : lit(null).cast(DataTypes.StringType).alias(c));
        }
        cols.add(coalesce(
                get_json_object(propsExpr, "$.user_id"),
                get_json_object(propsExpr, "$['app.installation.id']")
        ).alias("user_id"));
        for (String key : propKeys) {
            cols.add(get_json_object(propsExpr, PropsJsonPath.forTopLevelKey(key)).alias(key));
        }
        return cols.toArray(Column[]::new);
    }
}
