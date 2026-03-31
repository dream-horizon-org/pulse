package org.dreamhorizon.pulsespark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.spark.sql.functions.*;

public class EventCatalogJob {

    private static final Logger log = LoggerFactory.getLogger(EventCatalogJob.class);

    static final String[] FILTER_DIMENSIONS = {
            "os_name", "os_version", "app_build_name", "device_manufacturer",
            "device_model_identifier", "network_carrier_icc", "screen_name", "service_name"
    };

    private static final int DEFAULT_CHUNK_SIZE = 5_000;
    private static final int CATALOG_LOOKBACK_DAYS = 1;

    // -------------------------------------------------------------------------
    // Public entry point (called from SparkJobRunner)
    // -------------------------------------------------------------------------

    public static void runCatalog(SparkSession spark, MysqlRepository mysql, ClickHouseClient ch,
                                   String s3BucketPrefix, String runTime) throws Exception {
        List<String> projectIds = mysql.fetchProjectIds();
        if (projectIds.isEmpty()) {
            log.info("No projects found — nothing to do.");
            return;
        }
        log.info("Processing event catalog for {} project(s), run_time={}", projectIds.size(), runTime);

        for (var projectId : projectIds) {
            try {
                processProject(spark, ch, projectId.strip(), s3BucketPrefix, runTime);
            } catch (Exception e) {
                log.error("Project {} catalog failed: {}", projectId, e.getMessage(), e);
                throw e;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Project-level processing
    // -------------------------------------------------------------------------

    private static void processProject(SparkSession spark, ClickHouseClient ch,
                                       String projectId, String s3Prefix, String runTime) {
        var endDate   = LocalDate.parse(runTime.substring(0, 10));
        var startDate = endDate.minusDays(CATALOG_LOOKBACK_DAYS - 1L);

        var s3Base = "s3a://" + s3Prefix + projectId + "/vector-logs/";
        log.info("Scanning {} [{} → {}]", s3Base, startDate, endDate);

        Dataset<Row> raw = FunnelComputeJob.readS3ByDateRange(spark, s3Base, startDate, endDate);
        if (raw == null) {
            log.warn("No S3 data for project {}", projectId);
            return;
        }

        var selectCols = new ArrayList<Column>();
        selectCols.add(col("project_id"));
        selectCols.add(col("event_name"));
        selectCols.add(col("timestamp"));
        for (var dim : FILTER_DIMENSIONS) selectCols.add(col(dim).cast(DataTypes.StringType));

        // raw already has project_id column from READ_COLS; filter and select catalog cols
        Dataset<Row> df = raw
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
            writeEventCatalog(ch, df, runTime, DEFAULT_CHUNK_SIZE);
            writeFilterValues(ch, df, runTime, DEFAULT_CHUNK_SIZE);
        } finally {
            df.unpersist();
        }
    }

    // -------------------------------------------------------------------------
    // otel.event_catalog
    // -------------------------------------------------------------------------

    private static void writeEventCatalog(ClickHouseClient ch, Dataset<Row> raw,
                                          String runTime, int chunkSize) {
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
                    runTime
            ));
        }

        if (!rows.isEmpty()) {
            ch.bulkInsert("event_catalog",
                    "project_id,event_name,event_count,first_seen,last_seen,run_time",
                    rows, chunkSize);
            log.info("event_catalog: wrote {} rows for run_time={}", rows.size(), runTime);
        }
    }

    // -------------------------------------------------------------------------
    // otel.event_filter_values
    // -------------------------------------------------------------------------

    private static void writeFilterValues(ClickHouseClient ch, Dataset<Row> raw,
                                          String runTime, int chunkSize) {
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
                    runTime
            ));
        }

        if (!rows.isEmpty()) {
            ch.bulkInsert("event_filter_values",
                    "project_id,event_name,filter_key,filter_value,event_count,run_time",
                    rows, chunkSize);
            log.info("event_filter_values: wrote {} rows for run_time={}", rows.size(), runTime);
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
