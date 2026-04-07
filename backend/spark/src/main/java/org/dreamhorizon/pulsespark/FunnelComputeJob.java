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
        var identityCol = "UNIQUE_USERS".equals(funnel.mode()) ? "user_id" : "session_id";
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

        Dataset<Row> current = stepEvents(df, steps.get(0), identityCol)
                .groupBy(col("identity"))
                .agg(min(col("ts")).alias("ts0"))
                .withColumn("ts_prev", col("ts0"))
                .cache();

        long[] counts = new long[numSteps];
        counts[0] = current.count();
        log.info("Funnel {} step 0 ({}) -> {} identities", funnel.id(), steps.get(0).eventName(), counts[0]);

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

            counts[i] = current.count();
            log.info("Funnel {} step {} ({}) -> {} identities", funnel.id(), i, steps.get(i).eventName(), counts[i]);
        }
        current.unpersist();

        long step0Count = counts[0];
        var results = new ArrayList<FunnelResult>(numSteps);
        for (int i = 0; i < numSteps; i++) {
            double pct = step0Count > 0 ? (double) counts[i] / step0Count * 100.0 : 0.0;
            results.add(new FunnelResult(
                    funnel.id(), funnel.projectId(), runTime,
                    i, steps.get(i).eventName(),
                    counts[i],
                    Math.round(pct * 10_000.0) / 10_000.0
            ));
        }
        return results;
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
            var fieldCol = col(filter.field());
            var vals     = filter.value().toArray();
            Column cond  = switch (filter.operator()) {
                case "=", "IN"      -> fieldCol.isin(vals);
                case "!=", "NOT IN" -> not(fieldCol.isin(vals));
                case "CONTAINS"     -> filter.value().stream()
                        .map(fieldCol::contains)
                        .reduce(Column::or)
                        .orElse(lit(true));
                default             -> lit(true);
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
