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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.sql.functions.*;

/**
 * Pulse Funnel Compute — Spark 3.5 / Java 17
 *
 * <p>Reads saved funnel definitions from MySQL, computes closed+ordered funnel
 * metrics over S3 Parquet custom events, and writes pre-aggregated results to
 * ClickHouse {@code otel.funnel_results}.
 *
 * <p>Algorithm — cascading equi-join (no Scala UDF):
 * <ol>
 *   <li>Step 0: group identities by min(ts) → (identity, ts0, ts_prev=ts0)</li>
 *   <li>Step i: inner-join on identity + ts_i > ts_prev + ts_i ≤ ts0 + windowSeconds</li>
 *   <li>After each join, keep (identity, ts0, ts_prev=min(ts_i)) → count = step i users</li>
 * </ol>
 *
 * <p>Trigger modes:
 * <ul>
 *   <li>{@code daily}   — all funnels; one S3 read per project; run_date = today</li>
 *   <li>{@code on_save} — single {@code --funnel_id}; POSTs job-callback on finish</li>
 * </ul>
 *
 * <p>spark-submit example:
 * <pre>
 *   spark-submit \
 *     --class org.dreamhorizon.pulsespark.FunnelComputeJob \
 *     pulse-spark-jobs-1.0-SNAPSHOT.jar \
 *     --mode daily \
 *     --run_date 2024-01-15 \
 *     --mysql_host localhost --mysql_port 3307 \
 *     --mysql_db pulse --mysql_user pulse --mysql_password secret \
 *     --clickhouse_host localhost --clickhouse_port 8123 \
 *     --s3_bucket_prefix pulse-otel-
 * </pre>
 */
public class FunnelComputeJob {

    private static final Logger log = LoggerFactory.getLogger(FunnelComputeJob.class);

    private static final String[] READ_COLS = {
            "event_name", "project_id", "session_id", "timestamp",
            "os_name", "os_version", "app_build_name", "device_manufacturer",
            "device_model_identifier", "network_carrier_icc", "screen_name", "service_name"
    };

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        var params = parseArgs(args);

        var mode      = require(params, "mode");
        var runDate   = params.getOrDefault("run_date", LocalDate.now().toString());
        var funnelId  = params.get("funnel_id");
        var callbackUrl = params.get("callback_url");

        if ("on_save".equals(mode) && funnelId == null) {
            throw new IllegalArgumentException("--funnel_id is required for on_save mode");
        }

        var mysql = new MysqlRepository(
                require(params, "mysql_host"),
                Integer.parseInt(params.getOrDefault("mysql_port", "3306")),
                require(params, "mysql_db"),
                require(params, "mysql_user"),
                require(params, "mysql_password")
        );
        var ch = new ClickHouseClient(
                require(params, "clickhouse_host"),
                Integer.parseInt(params.getOrDefault("clickhouse_port", "8123")),
                params.getOrDefault("clickhouse_db", "otel"),
                params.getOrDefault("clickhouse_user", "default"),
                params.getOrDefault("clickhouse_password", "")
        );
        var s3Prefix = params.getOrDefault("s3_bucket_prefix", "pulse-otel-");

        List<FunnelDefinition> funnels = mysql.fetchFunnels("on_save".equals(mode) ? funnelId : null);
        if (funnels.isEmpty()) {
            log.info("No funnels found — nothing to do.");
            return;
        }
        log.info("Processing {} funnel(s), mode={}, run_date={}", funnels.size(), mode, runDate);

        var spark = SparkSession.builder()
                .appName("pulse-funnel-%s-%s".formatted(mode, runDate))
                .config("spark.sql.parquet.int96RebaseModeInRead", "CORRECTED")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();
        spark.sparkContext().setLogLevel("WARN");

        // Group funnels by project for a single S3 read per project
        Map<String, List<FunnelDefinition>> byProject = new HashMap<>();
        for (var f : funnels) byProject.computeIfAbsent(f.projectId(), k -> new ArrayList<>()).add(f);

        boolean anyFailed = false;
        for (var entry : byProject.entrySet()) {
            try {
                processProject(spark, ch, entry.getKey(), entry.getValue(), s3Prefix, runDate, callbackUrl);
            } catch (Exception e) {
                log.error("Project {} failed: {}", entry.getKey(), e.getMessage(), e);
                anyFailed = true;
                if ("on_save".equals(mode)) break;
            }
        }

        spark.stop();
        if (anyFailed) System.exit(1);
    }

    // -------------------------------------------------------------------------
    // Project-level processing
    // -------------------------------------------------------------------------

    private static void processProject(SparkSession spark, ClickHouseClient ch,
                                       String projectId, List<FunnelDefinition> funnels,
                                       String s3Prefix, String runDate, String callbackUrl) {
        int maxDays = funnels.stream().mapToInt(FunnelDefinition::dateRangeDays).max().orElse(7);
        var endDate   = LocalDate.parse(runDate);
        var startDate = endDate.minusDays(maxDays - 1L);

        var s3Path = "s3a://" + s3Prefix + projectId + "/vector-logs";
        log.info("Reading S3 {} [{} → {}] for {} funnel(s)", s3Path, startDate, endDate, funnels.size());

        // user_id is embedded in the props JSON blob (no top-level column in the Vector schema)
        // Vector writes Parquet with a .log extension — use explicit format + pathGlobFilter
        Dataset<Row> raw = spark.read()
                .format("parquet")
                .option("recursiveFileLookup", "true")
                .option("pathGlobFilter", "*.log")
                .option("mergeSchema", "true")
                .load(s3Path)
                .select(buildReadExprs())
                .filter(
                        col("project_id").equalTo(projectId)
                                .and(col("timestamp").cast(DataTypes.DateType).geq(lit(startDate.toString())))
                                .and(col("timestamp").cast(DataTypes.DateType).leq(lit(endDate.toString())))
                )
                .cache();

        try {
            for (var funnel : funnels) {
                try {
                    var results = computeFunnel(raw, funnel, runDate);
                    ch.deleteFunnelResults(funnel.funnelId(), runDate);
                    ch.insertFunnelResults(results);
                    log.info("Wrote {} result rows for funnel {}", results.size(), funnel.funnelId());
                    if (callbackUrl != null) {
                        ch.sendCallback(callbackUrl, funnel.funnelId(), "SUCCEEDED", runDate, null);
                    }
                } catch (Exception e) {
                    log.error("Funnel {} failed: {}", funnel.funnelId(), e.getMessage(), e);
                    if (callbackUrl != null) {
                        ch.sendCallback(callbackUrl, funnel.funnelId(), "FAILED", runDate, e.getMessage());
                    }
                    throw e;
                }
            }
        } finally {
            raw.unpersist();
        }
    }

    // -------------------------------------------------------------------------
    // Funnel computation — cascading equi-join
    // -------------------------------------------------------------------------

    private static List<FunnelResult> computeFunnel(Dataset<Row> raw,
                                                     FunnelDefinition funnel,
                                                     String runDate) {
        var identityCol  = "UNIQUE_USERS".equals(funnel.mode()) ? "user_id" : "session_id";
        long windowSecs  = funnel.windowSeconds();
        var steps        = funnel.steps();
        int numSteps     = steps.size();

        // Narrow to this funnel's specific date range (raw may cover a wider window)
        var endDate   = LocalDate.parse(runDate);
        var startDate = endDate.minusDays(funnel.dateRangeDays() - 1L);
        Dataset<Row> df = raw.filter(
                col("timestamp").cast(DataTypes.DateType).geq(lit(startDate.toString()))
                        .and(col("timestamp").cast(DataTypes.DateType).leq(lit(endDate.toString())))
        );

        // Apply global funnel filters
        df = applyFilters(df, funnel.globalFilters());

        // Step 0: (identity, ts0, ts_prev=ts0)
        Dataset<Row> step0Evts = stepEvents(df, steps.get(0), identityCol);
        Dataset<Row> current = step0Evts
                .groupBy(col("identity"))
                .agg(min(col("ts")).alias("ts0"))
                .withColumn("ts_prev", col("ts0"))
                .cache();

        long[] counts = new long[numSteps];
        counts[0] = current.count();
        log.info("Funnel {} step 0 ({}) → {} identities",
                funnel.funnelId(), steps.get(0).eventName(), counts[0]);

        // Steps 1..N: cascading join
        for (int i = 1; i < numSteps; i++) {
            if (counts[i - 1] == 0) {
                // No-one reached the previous step; remaining counts stay 0
                break;
            }

            Dataset<Row> stepEvts = stepEvents(df, steps.get(i), identityCol)
                    .select(
                            col("identity").alias("id_i"),
                            col("ts").alias("ts_i")
                    );

            Dataset<Row> prev = current.alias("prev");
            Dataset<Row> next = stepEvts.alias("next");

            Column joinCond = col("prev.identity").equalTo(col("next.id_i"))
                    .and(col("next.ts_i").gt(col("prev.ts_prev")))
                    .and(col("next.ts_i").leq(col("prev.ts0").plus(windowSecs)));

            Dataset<Row> prevCache = current;
            current = prev.join(next, joinCond, "inner")
                    .groupBy(
                            col("prev.identity").as("identity"),
                            col("prev.ts0").as("ts0")
                    )
                    .agg(min(col("next.ts_i")).alias("ts_prev"))
                    .cache();
            prevCache.unpersist();

            counts[i] = current.count();
            log.info("Funnel {} step {} ({}) → {} identities",
                    funnel.funnelId(), i, steps.get(i).eventName(), counts[i]);
        }
        current.unpersist();

        // Build result rows
        long step0Count = counts[0];
        var results = new ArrayList<FunnelResult>(numSteps);
        for (int i = 0; i < numSteps; i++) {
            double pct = step0Count > 0 ? (double) counts[i] / step0Count * 100.0 : 0.0;
            results.add(new FunnelResult(
                    funnel.funnelId(), funnel.projectId(), runDate,
                    i, steps.get(i).eventName(),
                    counts[i],
                    Math.round(pct * 10_000.0) / 10_000.0
            ));
        }
        return results;
    }

    /**
     * Returns (identity, ts) for events matching the given step's event name and step-level filters.
     * Timestamps are cast to Long (seconds since epoch) for window arithmetic.
     */
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

    /**
     * Applies a list of {@link FunnelFilter} conditions to a DataFrame.
     * Supported operators: {@code =}, {@code IN}, {@code !=}, {@code NOT IN}, {@code CONTAINS}.
     */
    static Dataset<Row> applyFilters(Dataset<Row> df, List<FunnelFilter> filters) {
        for (var filter : filters) {
            var fieldCol = col(filter.field());
            var vals     = filter.value().toArray();
            Column cond  = switch (filter.operator()) {
                case "=", "IN"         -> fieldCol.isin(vals);
                case "!=", "NOT IN"    -> not(fieldCol.isin(vals));
                case "CONTAINS"        -> filter.value().stream()
                        .map(fieldCol::contains)
                        .reduce(Column::or)
                        .orElse(lit(true));
                default                -> lit(true);
            };
            df = df.filter(cond);
        }
        return df;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Column[] buildReadExprs() {
        // Select all known columns + derive user_id from the props JSON blob
        var cols = new ArrayList<Column>();
        for (var c : READ_COLS) cols.add(col(c));
        cols.add(get_json_object(col("props"), "$.user_id").alias("user_id"));
        return cols.toArray(Column[]::new);
    }

    private static Map<String, String> parseArgs(String[] args) {
        var map = new HashMap<String, String>();
        for (int i = 0; i < args.length - 1; i += 2) {
            if (args[i].startsWith("--")) map.put(args[i].substring(2), args[i + 1]);
        }
        return map;
    }

    private static String require(Map<String, String> params, String key) {
        var v = params.get(key);
        if (v == null) throw new IllegalArgumentException("Missing required argument: --" + key);
        return v;
    }
}
