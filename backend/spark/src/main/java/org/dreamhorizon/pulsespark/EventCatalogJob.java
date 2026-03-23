package org.dreamhorizon.pulsespark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;

import org.apache.spark.sql.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.sql.functions.*;

/**
 * Pulse Event Catalog — Spark 3.5 / Java 17
 *
 * <p>Scans S3 Parquet custom event data and writes two ClickHouse
 * {@code AggregatingMergeTree} tables that power the funnel-builder UI:
 * <ul>
 *   <li>{@code otel.event_catalog}       — distinct events per project with counts</li>
 *   <li>{@code otel.event_filter_values} — distinct dimension values per (project, event)</li>
 * </ul>
 *
 * <p>Both tables use {@code SimpleAggregateFunction(sum/min/max)} so that each daily
 * Spark run can safely append; ClickHouse accumulates across runs during background
 * merges. Read with {@code FINAL} or the {@code *Merge} aggregate functions.
 *
 * <p>spark-submit example:
 * <pre>
 *   spark-submit \
 *     --class org.dreamhorizon.pulsespark.EventCatalogJob \
 *     pulse-spark-jobs-1.0-SNAPSHOT.jar \
 *     --project_ids fancode,proj-abc \
 *     --lookback_days 30 \
 *     --mysql_host localhost --mysql_port 3307 \
 *     --mysql_db pulse --mysql_user pulse --mysql_password secret \
 *     --clickhouse_host localhost --clickhouse_port 8123 \
 *     --s3_bucket_prefix pulse-otel-
 * </pre>
 */
public class EventCatalogJob {

    private static final Logger log = LoggerFactory.getLogger(EventCatalogJob.class);

    /**
     * Predefined filter dimensions — must match Parquet column names from Vector.
     * These are the values exposed in the UI filter-value dropdowns.
     */
    static final String[] FILTER_DIMENSIONS = {
            "os_name", "os_version", "app_build_name", "device_manufacturer",
            "device_model_identifier", "network_carrier_icc", "screen_name", "service_name"
    };

    private static final int DEFAULT_CHUNK_SIZE = 5_000;

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        var params      = parseArgs(args);
        var runDate     = params.getOrDefault("run_date", LocalDate.now().toString());
        int lookback    = Integer.parseInt(params.getOrDefault("lookback_days", "30"));
        int chunkSize   = Integer.parseInt(params.getOrDefault("insert_chunk_size",
                                                               String.valueOf(DEFAULT_CHUNK_SIZE)));

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

        List<String> projectIds = params.containsKey("project_ids")
                ? Arrays.asList(params.get("project_ids").split(","))
                : mysql.fetchProjectIds();

        if (projectIds.isEmpty()) {
            log.info("No projects found — nothing to do.");
            return;
        }
        log.info("Processing {} project(s), lookback={} days, run_date={}", projectIds.size(), lookback, runDate);

        var spark = SparkSession.builder()
                .appName("pulse-event-catalog-" + runDate)
                .config("spark.sql.parquet.int96RebaseModeInRead", "CORRECTED")
                .config("spark.sql.session.timeZone", "UTC")
                .getOrCreate();
        spark.sparkContext().setLogLevel("WARN");

        boolean anyFailed = false;
        for (var projectId : projectIds) {
            try {
                processProject(spark, ch, projectId.strip(), s3Prefix, runDate, lookback, chunkSize);
            } catch (Exception e) {
                log.error("Project {} failed: {}", projectId, e.getMessage(), e);
                anyFailed = true;
            }
        }

        spark.stop();
        if (anyFailed) System.exit(1);
    }

    // -------------------------------------------------------------------------
    // Project-level processing
    // -------------------------------------------------------------------------

    private static void processProject(SparkSession spark, ClickHouseClient ch,
                                       String projectId, String s3Prefix,
                                       String runDate, int lookbackDays, int chunkSize) {
        var endDate   = LocalDate.parse(runDate);
        var startDate = endDate.minusDays(lookbackDays - 1L);

        var s3Path = "s3://" + s3Prefix + projectId + "/vector-logs";
        log.info("Scanning {} [{} → {}]", s3Path, startDate, endDate);

        // Select only what we need
        var selectCols = new ArrayList<Column>();
        selectCols.add(col("project_id"));
        selectCols.add(col("event_name"));
        selectCols.add(col("timestamp"));
        for (var dim : FILTER_DIMENSIONS) selectCols.add(col(dim).cast(DataTypes.StringType));

        Dataset<Row> raw = spark.read()
                .format("parquet")
                .option("recursiveFileLookup", "true")
                .option("pathGlobFilter", "*.log")
                .option("mergeSchema", "true")
                .load(s3Path)
                .select(selectCols.toArray(Column[]::new))
                .filter(
                        col("project_id").equalTo(projectId)
                                .and(col("event_name").isNotNull())
                                .and(col("event_name").notEqual(""))
                                .and(col("timestamp").cast(DataTypes.DateType).geq(lit(startDate.toString())))
                                .and(col("timestamp").cast(DataTypes.DateType).leq(lit(endDate.toString())))
                )
                .cache();

        try {
            writeEventCatalog(ch, raw, runDate, chunkSize);
            writeFilterValues(ch, raw, runDate, chunkSize);
        } finally {
            raw.unpersist();
        }
    }

    // -------------------------------------------------------------------------
    // otel.event_catalog
    // -------------------------------------------------------------------------

    private static void writeEventCatalog(ClickHouseClient ch, Dataset<Row> raw,
                                          String runDate, int chunkSize) {
        List<Row> agg = raw
                .groupBy("project_id", "event_name")
                .agg(
                        count("*").alias("event_count"),
                        min(col("timestamp").cast(DataTypes.DateType)).alias("first_seen"),
                        max(col("timestamp").cast(DataTypes.DateType)).alias("last_seen")
                )
                .collectAsList();

        var rows = new ArrayList<String>(agg.size());
        for (Row row : agg) {
            rows.add("('%s','%s',%d,'%s','%s','%s')".formatted(
                    esc(row.getString(0)),
                    esc(row.getString(1)),
                    row.getLong(2),
                    row.getDate(3),
                    row.getDate(4),
                    runDate
            ));
        }

        if (!rows.isEmpty()) {
            ch.bulkInsert("event_catalog",
                    "project_id,event_name,event_count,first_seen,last_seen,run_date",
                    rows, chunkSize);
            log.info("event_catalog: wrote {} rows for run_date={}", rows.size(), runDate);
        }
    }

    // -------------------------------------------------------------------------
    // otel.event_filter_values
    // -------------------------------------------------------------------------

    private static void writeFilterValues(ClickHouseClient ch, Dataset<Row> raw,
                                          String runDate, int chunkSize) {
        // Build array of structs: [{filter_key: "os_name", filter_value: <value>}, ...]
        var structExprs = Arrays.stream(FILTER_DIMENSIONS)
                .map(dim -> struct(lit(dim).alias("filter_key"), col(dim).alias("filter_value")))
                .toArray(Column[]::new);

        List<Row> agg = raw
                .select(
                        col("project_id"),
                        col("event_name"),
                        explode(array(structExprs)).alias("kv")
                )
                .select(
                        col("project_id"),
                        col("event_name"),
                        col("kv.filter_key").alias("filter_key"),
                        col("kv.filter_value").alias("filter_value")
                )
                .filter(col("filter_value").isNotNull().and(col("filter_value").notEqual("")))
                .groupBy("project_id", "event_name", "filter_key", "filter_value")
                .agg(count("*").alias("event_count"))
                .collectAsList();

        var rows = new ArrayList<String>(agg.size());
        for (Row row : agg) {
            rows.add("('%s','%s','%s','%s',%d,'%s')".formatted(
                    esc(row.getString(0)),
                    esc(row.getString(1)),
                    esc(row.getString(2)),
                    esc(row.getString(3)),
                    row.getLong(4),
                    runDate
            ));
        }

        if (!rows.isEmpty()) {
            ch.bulkInsert("event_filter_values",
                    "project_id,event_name,filter_key,filter_value,event_count,run_date",
                    rows, chunkSize);
            log.info("event_filter_values: wrote {} rows for run_date={}", rows.size(), runDate);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
