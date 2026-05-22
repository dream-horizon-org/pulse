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
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

import static org.apache.spark.sql.functions.*;

public class FunnelComputeJob {

    private static final Logger log = LoggerFactory.getLogger(FunnelComputeJob.class);

    /** Shared parquet / Avro type for {@link SparkConstants.OtelLogColumn#LOG_ATTRIBUTES}. */
    private static final org.apache.spark.sql.types.DataType LOG_ATTRIBUTES_MAP_TYPE =
        DataTypes.createMapType(DataTypes.StringType, DataTypes.StringType, true);

    /** Top-level OTL field names preserved on read projection; aligns with Pulse OTL parquet schema. */

    public static void runFunnels(SparkSession spark, MysqlRepository mysql, ClickHouseClient ch,
                                   Long referenceId, String otelLogsBucket, String runTime) throws Exception {
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
            var s3Base  = resolveOtelLogsBasePath(otelLogsBucket, funnel.projectId());

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
            } finally {
                raw.unpersist();
            }
        } else {
            Map<String, List<FunnelDefinition>> byProject = new HashMap<>();
            for (var f : funnels) byProject.computeIfAbsent(f.projectId(), k -> new ArrayList<>()).add(f);

            for (var entry : byProject.entrySet()) {
                try {
                    processProjectBulk(spark, ch, entry.getKey(), entry.getValue(), otelLogsBucket, runTime);
                } catch (Exception e) {
                    log.error("Project {} funnels failed: {}", entry.getKey(), e.getMessage(), e);
                    throw e;
                }
            }
        }
    }

    private static void processProjectBulk(SparkSession spark, ClickHouseClient ch,
                                            String projectId, List<FunnelDefinition> funnels,
                                            String otelLogsBucket, String runTime) {
        int maxDays   = funnels.stream().mapToInt(FunnelDefinition::dateRange).max().orElse(7);
        var endDate   = LocalDate.parse(runTime.substring(0, 10));
        var startDate = endDate.minusDays(maxDays - 1L);
        var s3Base    = resolveOtelLogsBasePath(otelLogsBucket, projectId);

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
        // UNIQUE_USERS: AppInstallationId (aliased installation_id); SESSIONS: SessionId.
        var identityCol = SparkConstants.DefinitionModes.FUNNEL_SESSIONS.equalsIgnoreCase(funnel.mode())
                ? SparkConstants.OtelLogColumn.SESSION_ID
                : SparkConstants.Derived.INSTALLATION_ID;
        long windowSecs = funnel.windowSeconds();
        var steps       = funnel.steps();
        int numSteps    = steps.size();

        Dataset<Row> df;
        if (startEpochSeconds != null && endEpochSeconds != null) {
            df = raw.filter(
                    unix_timestamp(col(SparkConstants.OtelLogColumn.TIMESTAMP)).geq(lit(startEpochSeconds))
                            .and(unix_timestamp(col(SparkConstants.OtelLogColumn.TIMESTAMP)).leq(lit(endEpochSeconds)))
            );
        } else {
            var endDate   = LocalDate.parse(runTime.substring(0, 10));
            var startDate = endDate.minusDays(funnel.dateRange() - 1L);
            df = raw.filter(
                    col(SparkConstants.OtelLogColumn.TIMESTAMP).cast(DataTypes.DateType).geq(lit(startDate.toString()))
                            .and(col(SparkConstants.OtelLogColumn.TIMESTAMP).cast(DataTypes.DateType).leq(lit(endDate.toString())))
            );
        }
        df = applyOtelGlobalFilters(df, funnel.globalFilters());
        df = narrowToStepEventUnion(df, funnel);

        if (funnel.isUnordered()) {
            return computeUnorderedFunnel(df, funnel, identityCol, runTime);
        }

        // Step 0: keep ALL occurrences of step A — each is a separate attempt.
        // No groupBy+min: best-attempt evaluates every attempt individually.
        Dataset<Row> current = stepEvents(df, steps.get(0), identityCol)
                .select(
                        col(SparkConstants.AnalyticsDataset.IDENTITY),
                        col(SparkConstants.AnalyticsDataset.TS).alias(SparkConstants.AnalyticsDataset.TS0),
                        col(SparkConstants.AnalyticsDataset.TS).alias(SparkConstants.AnalyticsDataset.TS_PREV))
                .cache();

        long[] counts = new long[numSteps];
        counts[0] = current.select(SparkConstants.AnalyticsDataset.IDENTITY).distinct().count();
        log.info("Funnel {} step 0 ({}) -> {} identities", funnel.id(), steps.get(0).eventName(), counts[0]);

        // stepTs[i] holds a cached {identity, ts0, ts_i} snapshot for attempts that survived to step i.
        // Each entry is cached independently so it survives after the main alive dataset is unpersisted.
        List<Dataset<Row>> stepTs = new ArrayList<>(numSteps);
        stepTs.add(current.select(
                col(SparkConstants.AnalyticsDataset.IDENTITY),
                col(SparkConstants.AnalyticsDataset.TS0)).cache()); // step 0: ts0 itself

        for (int i = 1; i < numSteps; i++) {
            if (counts[i - 1] == 0) break;

            Dataset<Row> stepEvts = stepEvents(df, steps.get(i), identityCol)
                    .select(
                            col(SparkConstants.AnalyticsDataset.IDENTITY).alias(SparkConstants.AnalyticsDataset.ID_I),
                            col(SparkConstants.AnalyticsDataset.TS).alias(SparkConstants.AnalyticsDataset.TS_I));

            Column joinCond = col(SparkConstants.AnalyticsDataset.JOIN_PREV + "." + SparkConstants.AnalyticsDataset.IDENTITY)
                    .equalTo(col(SparkConstants.AnalyticsDataset.JOIN_NEXT + "." + SparkConstants.AnalyticsDataset.ID_I))
                    .and(col(SparkConstants.AnalyticsDataset.JOIN_NEXT + "." + SparkConstants.AnalyticsDataset.TS_I)
                            .gt(col(SparkConstants.AnalyticsDataset.JOIN_PREV + "." + SparkConstants.AnalyticsDataset.TS_PREV)))
                    .and(col(SparkConstants.AnalyticsDataset.JOIN_NEXT + "." + SparkConstants.AnalyticsDataset.TS_I)
                            .leq(col(SparkConstants.AnalyticsDataset.JOIN_PREV + "." + SparkConstants.AnalyticsDataset.TS0)
                                    .plus(windowSecs)));

            Dataset<Row> prevCache = current;
            current = current.alias(SparkConstants.AnalyticsDataset.JOIN_PREV)
                    .join(stepEvts.alias(SparkConstants.AnalyticsDataset.JOIN_NEXT), joinCond, "inner")
                    .groupBy(
                            col(SparkConstants.AnalyticsDataset.JOIN_PREV + "." + SparkConstants.AnalyticsDataset.IDENTITY)
                                    .as(SparkConstants.AnalyticsDataset.IDENTITY),
                            col(SparkConstants.AnalyticsDataset.JOIN_PREV + "." + SparkConstants.AnalyticsDataset.TS0)
                                    .as(SparkConstants.AnalyticsDataset.TS0))
                    .agg(min(col(SparkConstants.AnalyticsDataset.JOIN_NEXT + "." + SparkConstants.AnalyticsDataset.TS_I))
                            .alias(SparkConstants.AnalyticsDataset.TS_PREV))
                    .cache();
            prevCache.unpersist();

            counts[i] = current.select(SparkConstants.AnalyticsDataset.IDENTITY).distinct().count();
            log.info("Funnel {} step {} ({}) -> {} identities", funnel.id(), i, steps.get(i).eventName(), counts[i]);

            stepTs.add(current.select(
                    col(SparkConstants.AnalyticsDataset.IDENTITY),
                    col(SparkConstants.AnalyticsDataset.TS0),
                    col(SparkConstants.AnalyticsDataset.TS_PREV).alias(SparkConstants.AnalyticsDataset.TS_STEP_PREFIX + i))
                    .cache());
        }

        long[] medians = computeMedians(stepTs, numSteps, counts);

        boolean hasRev = funnel.hasRevenueConfig();
        int revenueStepIdx = hasRev ? funnel.effectiveRevenueStepIndex() : -1;
        RevenueAggregate rev = (hasRev && revenueStepIdx >= 0 && revenueStepIdx < stepTs.size()
                && counts[revenueStepIdx] > 0)
                ? aggregateOrderedRevenue(df, funnel, steps.get(revenueStepIdx), identityCol,
                    stepTs.get(revenueStepIdx), revenueStepIdx)
                : RevenueAggregate.EMPTY;

        current.unpersist();
        stepTs.forEach(Dataset::unpersist);

        long step0Count = counts[0];
        Double globalAov = rev.orderCount > 0 ? rev.totalRevenue / rev.orderCount : null;
        var results = new ArrayList<FunnelResult>(numSteps);
        for (int i = 0; i < numSteps; i++) {
            double pct = step0Count > 0 ? (double) counts[i] / step0Count * 100.0 : 0.0;
            Long stepOrderCount = null;
            Double stepRevenue = null;
            Double stepAov = null;
            Double stepLost = null;
            if (hasRev && globalAov != null) {
                stepOrderCount = rev.orderCount;
                stepRevenue = rev.totalRevenue;
                stepAov = globalAov;
                if (i == 0 || i > revenueStepIdx) {
                    stepLost = 0.0;
                } else {
                    long dropped = Math.max(0L, counts[i - 1] - counts[i]);
                    stepLost = dropped * globalAov;
                }
            }
            results.add(new FunnelResult(
                    funnel.id(), funnel.projectId(), runTime,
                    i, steps.get(i).eventName(),
                    counts[i],
                    Math.round(pct * 10_000.0) / 10_000.0,
                    i == 0 ? null : (medians[i] >= 0 ? medians[i] : null),
                    stepOrderCount, stepRevenue, stepAov, stepLost
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
                    .withColumn(SparkConstants.AnalyticsDataset.STEP_IDX, lit(i));
            allStepEvents = (allStepEvents == null) ? stepDf : allStepEvents.union(stepDf);
        }
        allStepEvents = allStepEvents.cache();

        // Sliding window: for each event, collect distinct step indices within [ts, ts + windowSecs].
        WindowSpec rangeWin = Window.partitionBy(SparkConstants.AnalyticsDataset.IDENTITY)
                .orderBy(col(SparkConstants.AnalyticsDataset.TS))
                .rangeBetween(0, windowSecs);

        Dataset<Row> withWindowSteps = allStepEvents
                .withColumn(SparkConstants.AnalyticsDataset.STEPS_IN_WINDOW,
                        size(collect_set(col(SparkConstants.AnalyticsDataset.STEP_IDX)).over(rangeWin)));

        // Per identity, take the best window (max distinct steps achieved).
        Dataset<Row> bestPerIdentity = withWindowSteps
                .groupBy(SparkConstants.AnalyticsDataset.IDENTITY)
                .agg(max(SparkConstants.AnalyticsDataset.STEPS_IN_WINDOW).alias(SparkConstants.AnalyticsDataset.MAX_STEPS))
                .cache();

        // Step i count = identities who completed at least (i+1) distinct steps in their best window.
        long[] counts = new long[numSteps];
        for (int i = 0; i < numSteps; i++) {
            counts[i] = bestPerIdentity.filter(col(SparkConstants.AnalyticsDataset.MAX_STEPS).geq(lit(i + 1))).count();
            log.info("Funnel {} step {} ({}) -> {} identities (unordered)",
                    funnel.id(), i, steps.get(i).eventName(), counts[i]);
        }

        bestPerIdentity.unpersist();
        allStepEvents.unpersist();

        boolean hasRev = funnel.hasRevenueConfig();
        int revenueStepIdx = hasRev ? funnel.effectiveRevenueStepIndex() : -1;
        RevenueAggregate rev = (hasRev && revenueStepIdx >= 0 && revenueStepIdx < numSteps)
                ? aggregateUnorderedRevenue(df, steps.get(revenueStepIdx), identityCol, funnel.revenueAttribute())
                : RevenueAggregate.EMPTY;
        Double globalAov = rev.orderCount > 0 ? rev.totalRevenue / rev.orderCount : null;

        long step0Count = counts[0];
        var results = new ArrayList<FunnelResult>(numSteps);
        for (int i = 0; i < numSteps; i++) {
            double pct = step0Count > 0 ? (double) counts[i] / step0Count * 100.0 : 0.0;
            Long stepOrderCount = null;
            Double stepRevenue = null;
            Double stepAov = null;
            Double stepLost = null;
            if (hasRev && globalAov != null) {
                stepOrderCount = rev.orderCount;
                stepRevenue = rev.totalRevenue;
                stepAov = globalAov;
                if (i == 0 || i > revenueStepIdx) {
                    stepLost = 0.0;
                } else {
                    long dropped = Math.max(0L, counts[i - 1] - counts[i]);
                    stepLost = dropped * globalAov;
                }
            }
            results.add(new FunnelResult(
                    funnel.id(), funnel.projectId(), runTime,
                    i, steps.get(i).eventName(),
                    counts[i],
                    Math.round(pct * 10_000.0) / 10_000.0,
                    null,
                    stepOrderCount, stepRevenue, stepAov, stepLost
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
                    .withColumnRenamed(SparkConstants.AnalyticsDataset.IDENTITY, SparkConstants.AnalyticsDataset.MEDIAN_JOIN_IDENTITY)
                    .withColumnRenamed(SparkConstants.AnalyticsDataset.TS0, SparkConstants.AnalyticsDataset.MEDIAN_JOIN_TS0);
            Column joinCond = col(SparkConstants.AnalyticsDataset.IDENTITY)
                    .equalTo(snap.col(SparkConstants.AnalyticsDataset.MEDIAN_JOIN_IDENTITY))
                    .and(col(SparkConstants.AnalyticsDataset.TS0).equalTo(snap.col(SparkConstants.AnalyticsDataset.MEDIAN_JOIN_TS0)));
            timeline = timeline.join(snap, joinCond, "left")
                    .drop(SparkConstants.AnalyticsDataset.MEDIAN_JOIN_IDENTITY, SparkConstants.AnalyticsDataset.MEDIAN_JOIN_TS0);
        }

        // Score each attempt by farthest step reached (count of non-null ts_i for i>=1).
        Column scoreExpr = lit(0);
        for (int i = 1; i < stepTs.size(); i++) {
            scoreExpr = scoreExpr.plus(when(
                    col(SparkConstants.AnalyticsDataset.TS_STEP_PREFIX + i).isNotNull(), lit(1)).otherwise(lit(0)));
        }
        timeline = timeline.withColumn(SparkConstants.AnalyticsDataset.MEDIAN_SCORE, scoreExpr);

        // Per-user: pick attempt with highest score, then earliest ts0.
        WindowSpec userWin = Window.partitionBy(SparkConstants.AnalyticsDataset.IDENTITY)
                .orderBy(col(SparkConstants.AnalyticsDataset.MEDIAN_SCORE).desc(), col(SparkConstants.AnalyticsDataset.TS0).asc());
        Dataset<Row> bestAttempt = timeline
                .withColumn(SparkConstants.AnalyticsDataset.MEDIAN_RN, row_number().over(userWin))
                .filter(col(SparkConstants.AnalyticsDataset.MEDIAN_RN).equalTo(1))
                .drop(SparkConstants.AnalyticsDataset.MEDIAN_SCORE, SparkConstants.AnalyticsDataset.MEDIAN_RN)
                .cache();

        // Compute median for each step transition.
        for (int i = 1; i < stepTs.size(); i++) {
            if (counts[i] == 0) {
                medians[i] = -1;
                continue;
            }
            String tsCol = SparkConstants.AnalyticsDataset.TS_STEP_PREFIX + i;
            String prevCol = (i == 1)
                    ? SparkConstants.AnalyticsDataset.TS0
                    : SparkConstants.AnalyticsDataset.TS_STEP_PREFIX + (i - 1);
            Dataset<Row> eligible = bestAttempt.filter(col(tsCol).isNotNull());
            Row r = eligible.agg(
                    percentile_approx(col(tsCol).minus(col(prevCol)), lit(0.5), lit(10000))
                            .alias(SparkConstants.AnalyticsDataset.MEDIAN_AGG)
            ).first();
            medians[i] = (r == null || r.isNullAt(0)) ? -1L : r.getLong(0);
        }

        bestAttempt.unpersist();
        return medians;
    }

    static Dataset<Row> narrowToStepEventUnion(Dataset<Row> df, FunnelDefinition funnel) {
        List<FunnelStep> steps = funnel.steps();
        if (steps.isEmpty()) {
            return df;
        }
        Column union = null;
        for (FunnelStep step : steps) {
            Column branch = col(SparkConstants.OtelLogColumn.EVENT_NAME).equalTo(lit(step.eventName()));
            for (FunnelFilter sf : step.stepFilters()) {
                Optional<Column> fc = otelStepFilterPredicate(sf, "[funnel-upfront]");
                if (fc.isPresent()) {
                    branch = branch.and(fc.get());
                }
            }
            union = (union == null) ? branch : union.or(branch);
        }
        return df.filter(union);
    }

    /**
     * Global filters for otel_logs funnel compute ({@link SparkConstants.OtelLogColumn#LOG_ATTRIBUTES}).
     */
    private static Dataset<Row> applyOtelGlobalFilters(Dataset<Row> df, List<FunnelFilter> filters) {
        Dataset<Row> out = df;
        for (var filter : filters) {
            Optional<Column> pred = otelGlobalFilterPredicate(filter, "[global]");
            if (pred.isPresent()) {
                out = out.filter(pred.get());
            }
        }
        return out;
    }

    private static Dataset<Row> applyOtelStepFilters(Dataset<Row> df, List<FunnelFilter> filters) {
        Dataset<Row> out = df;
        for (var filter : filters) {
            Optional<Column> pred = otelStepFilterPredicate(filter, "[step]");
            if (pred.isPresent()) {
                out = out.filter(pred.get());
            }
        }
        return out;
    }

    private static Optional<Column> otelGlobalFilterPredicate(FunnelFilter filter, String logCtx) {
        if (filter.field() == null || filter.field().isBlank()) {
            log.warn("{} Skipping filter: field is null or blank", logCtx);
            return Optional.empty();
        }
        Object[] vals = filter.value().toArray();
        if (vals.length == 0) {
            log.warn("{} Skipping filter: empty value list for field '{}'", logCtx, filter.field());
            return Optional.empty();
        }
        String fldTrim = filter.field().trim();
        if (FilterFieldMapper.isCatalogMapped(filter.field())) {
            String parquetField = FilterFieldMapper.toParquetColumn(filter.field());
            return Optional.of(catalogScalarPredicate(filter, logCtx, col(parquetField)));
        }
        return Optional.of(
            LogAttributesPredicates.predicate(
                SparkConstants.OtelLogColumn.LOG_ATTRIBUTES, fldTrim, filter.operator(), filter.value()));
    }

    private static Optional<Column> otelStepFilterPredicate(FunnelFilter filter, String logCtx) {
        if (filter.field() == null || filter.field().isBlank()) {
            log.warn("{} Skipping filter: field is null or blank", logCtx);
            return Optional.empty();
        }
        Object[] vals = filter.value().toArray();
        if (vals.length == 0) {
            log.warn("{} Skipping filter: empty value list for field '{}'", logCtx, filter.field());
            return Optional.empty();
        }
        String fldTrim = filter.field().trim();
        return Optional.of(LogAttributesPredicates.predicate(
            SparkConstants.OtelLogColumn.LOG_ATTRIBUTES, fldTrim, filter.operator(), filter.value()));
    }

    private static Column catalogScalarPredicate(FunnelFilter filter, String logCtx, Column fieldCol) {
        Object[] vals = filter.value().toArray();
        String op = FunnelFilterOperators.normalize(filter.operator());
        return switch (op) {
            case "EQ", "=", "IN" -> fieldCol.isin((Object[]) vals);
            case "NE", "!=", "NOT_IN" -> not(fieldCol.isin((Object[]) vals));
            case "CONTAINS" -> filter.value().stream()
                    .map(v -> fieldCol.contains(lit(String.valueOf(v))))
                    .reduce(Column::or)
                    .orElse(lit(true));
            default -> {
                log.warn("{} Unknown filter operator '{}' (normalized='{}') for field '{}' — filter skipped",
                        logCtx, filter.operator(), op, filter.field());
                yield lit(true);
            }
        };
    }

    private static Dataset<Row> stepEvents(Dataset<Row> df, FunnelStep step, String identityCol) {
        Dataset<Row> filtered = df.filter(col(SparkConstants.OtelLogColumn.EVENT_NAME).equalTo(step.eventName()));
        filtered = applyOtelStepFilters(filtered, step.stepFilters());
        return filtered
                .select(
                        col(identityCol).alias(SparkConstants.AnalyticsDataset.IDENTITY),
                        unix_timestamp(col(SparkConstants.OtelLogColumn.TIMESTAMP))
                            .alias(SparkConstants.AnalyticsDataset.TS)
                )
                .filter(col(SparkConstants.AnalyticsDataset.IDENTITY).isNotNull().and(col(SparkConstants.AnalyticsDataset.IDENTITY).notEqual("")));
    }

    /**
     * Revenue events for the purchase step. {@code revenueAttribute} is the OTL log-attribute key
     * (e.g. {@code order.value}) read from {@link SparkConstants.OtelLogColumn#LOG_ATTRIBUTES}.
     */
    private static Dataset<Row> revenueStepEvents(Dataset<Row> df, FunnelStep step,
                                                   String identityCol, String revenueAttribute) {
        Dataset<Row> filtered = df.filter(col(SparkConstants.OtelLogColumn.EVENT_NAME).equalTo(step.eventName()));
        filtered = applyOtelStepFilters(filtered, step.stepFilters());
        return filtered
                .select(
                        col(identityCol).alias(SparkConstants.AnalyticsDataset.IDENTITY),
                        unix_timestamp(col(SparkConstants.OtelLogColumn.TIMESTAMP))
                            .alias(SparkConstants.AnalyticsDataset.TS),
                        logAttributeNumeric(revenueAttribute).alias(SparkConstants.AnalyticsDataset.VALUE))
                .filter(col(SparkConstants.AnalyticsDataset.IDENTITY).isNotNull()
                        .and(col(SparkConstants.AnalyticsDataset.IDENTITY).notEqual("")));
    }

    private static Column logAttributeNumeric(String attributeKey) {
        Column raw = element_at(col(SparkConstants.OtelLogColumn.LOG_ATTRIBUTES), lit(attributeKey.trim()));
        return when(raw.isNull().or(trim(raw).equalTo("")), lit(null))
                .otherwise(raw.cast(DataTypes.DoubleType));
    }

    private static RevenueAggregate aggregateOrderedRevenue(Dataset<Row> df, FunnelDefinition funnel,
                                                             FunnelStep revStep, String identityCol,
                                                             Dataset<Row> revStepTs, int revenueStepIdx) {
        Dataset<Row> revEvts = revenueStepEvents(df, revStep, identityCol, funnel.revenueAttribute());

        Dataset<Row> revAtStep = (revenueStepIdx == 0)
                ? revStepTs.withColumn(SparkConstants.AnalyticsDataset.TS_REV, col(SparkConstants.AnalyticsDataset.TS0))
                : revStepTs.withColumnRenamed(
                        SparkConstants.AnalyticsDataset.TS_STEP_PREFIX + revenueStepIdx,
                        SparkConstants.AnalyticsDataset.TS_REV);

        Dataset<Row> joined = revAtStep.alias("a")
                .join(revEvts.alias("e"),
                        col("a." + SparkConstants.AnalyticsDataset.IDENTITY)
                                .equalTo(col("e." + SparkConstants.AnalyticsDataset.IDENTITY))
                                .and(col("a." + SparkConstants.AnalyticsDataset.TS_REV)
                                        .equalTo(col("e." + SparkConstants.AnalyticsDataset.TS))),
                        "inner")
                .select(
                        col("a." + SparkConstants.AnalyticsDataset.IDENTITY).as(SparkConstants.AnalyticsDataset.IDENTITY),
                        col("a." + SparkConstants.AnalyticsDataset.TS0).as(SparkConstants.AnalyticsDataset.TS0),
                        col("e." + SparkConstants.AnalyticsDataset.VALUE).as(SparkConstants.AnalyticsDataset.VALUE))
                .filter(col(SparkConstants.AnalyticsDataset.VALUE).isNotNull());

        WindowSpec firstWin = Window.partitionBy(SparkConstants.AnalyticsDataset.IDENTITY)
                .orderBy(col(SparkConstants.AnalyticsDataset.TS0).asc());
        Dataset<Row> perIdentity = joined
                .withColumn(SparkConstants.AnalyticsDataset.MEDIAN_RN, row_number().over(firstWin))
                .filter(col(SparkConstants.AnalyticsDataset.MEDIAN_RN).equalTo(1))
                .select(SparkConstants.AnalyticsDataset.VALUE);

        return collectRevenueAggregate(perIdentity);
    }

    private static RevenueAggregate aggregateUnorderedRevenue(Dataset<Row> df, FunnelStep revStep,
                                                               String identityCol, String revenueAttribute) {
        Dataset<Row> revEvts = revenueStepEvents(df, revStep, identityCol, revenueAttribute)
                .filter(col(SparkConstants.AnalyticsDataset.VALUE).isNotNull());
        WindowSpec firstWin = Window.partitionBy(SparkConstants.AnalyticsDataset.IDENTITY)
                .orderBy(col(SparkConstants.AnalyticsDataset.TS).asc());
        Dataset<Row> perIdentity = revEvts
                .withColumn(SparkConstants.AnalyticsDataset.MEDIAN_RN, row_number().over(firstWin))
                .filter(col(SparkConstants.AnalyticsDataset.MEDIAN_RN).equalTo(1))
                .select(SparkConstants.AnalyticsDataset.VALUE);
        return collectRevenueAggregate(perIdentity);
    }

    private static RevenueAggregate collectRevenueAggregate(Dataset<Row> perIdentityValues) {
        Row agg = perIdentityValues.agg(
                sum(col(SparkConstants.AnalyticsDataset.VALUE)).alias("total"),
                count(lit(1)).alias("cnt")
        ).first();
        if (agg == null || agg.isNullAt(0)) {
            return RevenueAggregate.EMPTY;
        }
        return new RevenueAggregate(agg.getDouble(0), agg.getLong(1));
    }

    private record RevenueAggregate(double totalRevenue, long orderCount) {
        static final RevenueAggregate EMPTY = new RevenueAggregate(0.0, 0L);
    }

    static Dataset<Row> readS3ByDateRange(SparkSession spark, String s3BasePath,
                                           LocalDate startDate, LocalDate endDate) {
        Dataset<Row> combined = null;
        int attempted = 0, loaded = 0;
        for (var d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
            attempted++;
            String path = SparkConstants.OtelLogsS3.partitionPrefixForDay(s3BasePath, d);
            log.info("Loading parquet from S3 path: {}", path);
            var ds = loadPath(spark, path);
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
        long startEpoch = startDt.toEpochSecond(ZoneOffset.UTC);
        long endEpoch = endDt.toEpochSecond(ZoneOffset.UTC);
        LocalDate dayStart = startDt.toLocalDate();
        LocalDate dayEnd = endDt.toLocalDate();
        Dataset<Row> combined = null;
        int attempted = 0, loaded = 0;
        for (LocalDate d = dayStart; !d.isAfter(dayEnd); d = d.plusDays(1)) {
            attempted++;
            var path = SparkConstants.OtelLogsS3.partitionPrefixForDay(s3BasePath, d);
            log.info("Loading parquet from S3 path: {}", path);
            var ds = loadPath(spark, path);
            if (ds != null) {
                loaded++;
                var ts = unix_timestamp(col(SparkConstants.OtelLogColumn.TIMESTAMP));
                Dataset<Row> slice = ds.filter(ts.geq(lit(startEpoch)).and(ts.leq(lit(endEpoch))));
                combined = combined == null ? slice : combined.union(slice);
            }
        }
        if (combined == null) {
            log.warn("No S3 data for UTC window {} to {} (attempted={}, loaded.partition.days={})",
                    startDt, endDt, attempted, loaded);
        } else {
            log.info("Loaded S3 UTC window {} to {} (attempted.partition.days={}, loaded.days={})",
                    startDt, endDt, attempted, loaded);
        }
        return combined;
    }

    private static Dataset<Row> loadPath(SparkSession spark, String path) {
        try {
            var ds = spark.read()
                    .format("parquet")
                    .option("recursiveFileLookup", "true")
                    .option("pathGlobFilter", "*.parquet")
                    .option("mergeSchema", "true")
                    .load(path);
            return ds.select(buildReadExprs(ds.columns()));
        } catch (Exception e) {
            log.warn("Skipping S3 path {}: {}", path, e.getMessage());
            return null;
        }
    }

    static Dataset<Row> prepareRaw(Dataset<Row> ds, String projectId) {
        return ds.filter(col(SparkConstants.OtelLogColumn.PROJECT_ID).equalTo(projectId));
    }

    /**
     * Global filters for otel_logs (journey + funnel). Catalog-mapped fields use materialized columns;
     * others use {@link SparkConstants.OtelLogColumn#LOG_ATTRIBUTES}.
     */
    public static Dataset<Row> applyGlobalFilters(Dataset<Row> df, List<FunnelFilter> filters) {
        return applyOtelGlobalFilters(df, filters);
    }

    static Dataset<Row> applyStepFilters(Dataset<Row> df, List<FunnelFilter> filters) {
        return applyOtelStepFilters(df, filters);
    }

    private static String findParquetColumnCi(String[] availableColumns, String canonicalName) {
        for (String raw : availableColumns) {
            if (raw != null && canonicalName.equalsIgnoreCase(raw)) {
                return raw;
            }
        }
        return null;
    }

    /**
     * Optional local override: {@code PULSE_SPARK_DATA_BASE=file:///tmp/fancode-test-parquet-otel/}.
     * Default: {@link SparkConstants.OtelLogsS3#basePath}.
     */
    private static String resolveOtelLogsBasePath(String bucket, String projectId) {
        String override = System.getenv("PULSE_SPARK_DATA_BASE");
        if (override != null && !override.isBlank()) {
            return override.endsWith("/") ? override : override + "/";
        }
        return SparkConstants.OtelLogsS3.basePath(bucket, projectId);
    }

    /** Projects otel_logs-style parquet ({@link SparkConstants.OtelLogColumn}) onto stable logical names + identity aliases. */
    static Column[] buildReadExprs(String[] availableColumns) {
        Column instExpr;
        String instPhy = findParquetColumnCi(availableColumns, SparkConstants.OtelLogColumn.APP_INSTALLATION_ID);
        if (instPhy != null) {
            instExpr = col(instPhy);
        } else {
            instExpr = lit(null).cast(DataTypes.StringType);
        }
        var cols = new ArrayList<Column>();
        for (String canon : SparkConstants.OtelLogColumn.PARQUET_READ_COLUMNS) {
            String src = findParquetColumnCi(availableColumns, canon);
            Column exprCol = SparkConstants.OtelLogColumn.APP_INSTALLATION_ID.equals(canon)
                ? instExpr
                : (src != null ? col(src) : lit(null).cast(DataTypes.StringType));
            cols.add(exprCol.alias(canon));
        }
        cols.add(instExpr.alias(SparkConstants.Derived.INSTALLATION_ID));
        cols.add(instExpr.alias(SparkConstants.Derived.USER_ID));
        return cols.toArray(Column[]::new);
    }
}
