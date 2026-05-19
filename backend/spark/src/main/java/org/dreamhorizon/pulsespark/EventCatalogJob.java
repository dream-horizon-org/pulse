package org.dreamhorizon.pulsespark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.apache.spark.sql.functions.*;

public class EventCatalogJob {

    private static final Logger log = LoggerFactory.getLogger(EventCatalogJob.class);

    private static final String FILTER_KEY_EVENT           = "EVENT";
    private static final String FILTER_KEY_APP_BUILD_NAME  = "APP_BUILD_NAME";
    private static final String FILTER_KEY_OS_VERSION      = "OS_VERSION";
    private static final String FILTER_KEY_OS_NAME         = "OS_NAME";

    private static final int COLD_LOOKBACK_DAYS   = 7;
    private static final int DEFAULT_CHUNK_SIZE   = 5_000;

    public static void runCatalog(SparkSession spark, MysqlRepository mysql, ClickHouseClient ch,
                                   String s3BucketPrefix, String runTime) throws Exception {
        FunnelComputeJob.configureSparkForParquetMrTimestamps(spark);
        List<String> projectIds = mysql.fetchProjectIds();
        if (projectIds.isEmpty()) {
            log.info("No projects to process");
            return;
        }
        Instant runEnd = parseRunTimeUtc(runTime);
        Optional<Timestamp> priorSucceededStart = mysql.getLatestSucceededEventCatalogJobStartedAt();
        log.info("Event catalog for {} project(s), priorSucceededStartedAt={}",
                projectIds.size(), priorSucceededStart.map(Object::toString).orElse("none"));

        for (var projectId : projectIds) {
            try {
                processProject(spark, ch, projectId.strip(), s3BucketPrefix, runEnd, priorSucceededStart);
            } catch (Exception e) {
                log.error("Project {} catalog failed: {}", projectId, e.getMessage(), e);
                throw e;
            }
        }
    }

    private static void processProject(SparkSession spark, ClickHouseClient ch,
                                       String projectId, String s3Prefix, Instant runEnd,
                                       Optional<Timestamp> priorSucceededStart) throws Exception {
        LocalDate endDate = runEnd.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate startDate;
        long windowStartEpoch;
        long windowEndEpoch = runEnd.getEpochSecond();

        if (priorSucceededStart.isEmpty()) {
            startDate = endDate.minusDays(COLD_LOOKBACK_DAYS - 1L);
            windowStartEpoch = startDate.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
            log.info("Project {} cold start: S3 [{} -> {}]", projectId, startDate, endDate);
        } else {
            var wm = priorSucceededStart.get();
            startDate = wm.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            windowStartEpoch = wm.getTime() / 1000L;
            log.info("Project {} incremental from {}: S3 [{} -> {}]", projectId, wm, startDate, endDate);
        }

        var s3Base = FunnelComputeJob.buildS3Base(s3Prefix, projectId, "otel_logs");
        Dataset<Row> raw = FunnelComputeJob.readS3ByDateRange(spark, s3Base, startDate, endDate);
        if (raw == null) {
            log.warn("No S3 data for project {} — skipping", projectId);
            return;
        }

        var tsSec = unix_timestamp(col("timestamp"));
        var inTimeWindow = priorSucceededStart.isEmpty()
                ? tsSec.geq(lit(windowStartEpoch)).and(tsSec.leq(lit(windowEndEpoch)))
                : tsSec.gt(lit(windowStartEpoch)).and(tsSec.leq(lit(windowEndEpoch)));

        Dataset<Row> df = raw
                .select(
                        col("project_id"),
                        col("event_name"),
                        col("timestamp"),
                        col("app_build_name"),
                        col("os_version"),
                        col("os_name")
                )
                .filter(
                        col("project_id").equalTo(projectId)
                                .and(col("event_name").isNotNull())
                                .and(col("event_name").notEqual(""))
                                .and(inTimeWindow)
                )
                .cache();

        try {
            List<String> valueRows = buildCatalogInsertRows(df);
            if (!valueRows.isEmpty()) {
                ch.bulkInsert("event_catalog_entries", "ProjectId,FilterKey,FilterValue",
                        valueRows, DEFAULT_CHUNK_SIZE);
                log.info("Project {}: wrote {} event_catalog_entries rows (events + dimensions)", projectId, valueRows.size());
            } else {
                log.info("Project {}: no new event catalog entries", projectId);
            }
        } finally {
            df.unpersist();
        }
    }

    /** Distinct (ProjectId, FilterKey, FilterValue) for EVENT and dimension filters from parquet columns. */
    private static List<String> buildCatalogInsertRows(Dataset<Row> df) {
        Column nonBlankFv = col("fv").isNotNull().and(length(trim(col("fv"))).gt(0));

        Dataset<Row> events = df.select(
                col("project_id").alias("pid"),
                lit(FILTER_KEY_EVENT).alias("fk"),
                col("event_name").cast("string").alias("fv")
        ).filter(nonBlankFv);

        Dataset<Row> unioned = events
                .unionByName(dimensionSlice(df, FILTER_KEY_APP_BUILD_NAME, "app_build_name"))
                .unionByName(dimensionSlice(df, FILTER_KEY_OS_VERSION, "os_version"))
                .unionByName(dimensionSlice(df, FILTER_KEY_OS_NAME, "os_name"))
                .distinct();

        List<Row> rows = unioned.collectAsList();
        var out = new ArrayList<String>(rows.size());
        for (Row row : rows) {
            out.add("('%s','%s','%s')".formatted(
                    esc(row.getString(0)),
                    esc(row.getString(1)),
                    esc(row.getString(2))
            ));
        }
        return out;
    }

    private static Dataset<Row> dimensionSlice(Dataset<Row> df, String filterKey, String valueCol) {
        Column nonBlankFv = col("fv").isNotNull().and(length(trim(col("fv"))).gt(0));
        return df.select(
                col("project_id").alias("pid"),
                lit(filterKey).alias("fk"),
                col(valueCol).cast("string").alias("fv")
        ).filter(nonBlankFv);
    }

    private static Instant parseRunTimeUtc(String runTime) {
        return LocalDateTime.parse(runTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"))
                .toInstant(ZoneOffset.UTC);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("'", "\\'");
    }
}
