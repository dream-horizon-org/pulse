package org.dreamhorizon.pulsespark;

import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.types.DataTypes;
import org.dreamhorizon.pulsespark.model.FunnelFilter;
import org.dreamhorizon.pulsespark.model.JourneyDefinition;
import org.dreamhorizon.pulsespark.model.JourneyTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.sql.functions.*;

public class JourneyComputeJob {

    private static final Logger log = LoggerFactory.getLogger(JourneyComputeJob.class);

    public static void runJourneys(SparkSession spark, MysqlRepository mysql, ClickHouseClient ch,
                                    Long referenceId, String s3BucketPrefix, String runTime) throws Exception {
        FunnelComputeJob.configureSparkForParquetMrTimestamps(spark);
        List<JourneyDefinition> journeys = mysql.fetchJourneys(referenceId);
        if (journeys.isEmpty()) {
            log.info("No journeys to process");
            return;
        }
        log.info("Processing {} journey(s), referenceId={}", journeys.size(), referenceId);

        if (referenceId != null) {
            var journey = journeys.get(0);
            if (journey.startTime() == null || journey.endTime() == null) {
                throw new IllegalArgumentException("JOURNEY requires start_time and end_time for journey_id=" + journey.id());
            }
            var startDt = journey.startTime().toLocalDateTime();
            var endDt   = journey.endTime().toLocalDateTime();
            var s3Base  = FunnelComputeJob.buildS3Base(s3BucketPrefix, journey.projectId(), SparkConstants.Tables.S3_OTEL_LOGS);

            log.info("Journey {} window [{} -> {}]", journey.id(), startDt, endDt);
            Dataset<Row> raw = FunnelComputeJob.readS3ByHours(spark, s3Base, startDt, endDt);
            if (raw == null) {
                log.warn("No S3 data for journey {}", journey.id());
                return;
            }
            raw = raw.filter(col("project_id").equalTo(journey.projectId())).cache();
            try {
                long startEpoch = startDt.toEpochSecond(ZoneOffset.UTC);
                long endEpoch   = endDt.toEpochSecond(ZoneOffset.UTC);
                var results = computeJourney(raw, journey, runTime, startEpoch, endEpoch);
                ch.insertJourneyResults(results);
                log.info("Journey {}: wrote {} transition rows", journey.id(), results.size());
            } finally {
                raw.unpersist();
            }
        } else {
            Map<String, List<JourneyDefinition>> byProject = new HashMap<>();
            for (var j : journeys) byProject.computeIfAbsent(j.projectId(), k -> new ArrayList<>()).add(j);

            for (var entry : byProject.entrySet()) {
                try {
                    processProjectBulk(spark, ch, entry.getKey(), entry.getValue(), s3BucketPrefix, runTime);
                } catch (Exception e) {
                    log.error("Project {} journeys failed: {}", entry.getKey(), e.getMessage(), e);
                    throw e;
                }
            }
        }
    }

    private static void processProjectBulk(SparkSession spark, ClickHouseClient ch,
                                            String projectId, List<JourneyDefinition> journeys,
                                            String s3Prefix, String runTime) {
        int maxDays   = journeys.stream().mapToInt(JourneyDefinition::dateRange).max().orElse(7);
        var endDate   = LocalDate.parse(runTime.substring(0, 10));
        var startDate = endDate.minusDays(maxDays - 1L);
        var s3Base    = FunnelComputeJob.buildS3Base(s3Prefix, projectId, SparkConstants.Tables.S3_OTEL_LOGS);

        log.info("Project {} reading S3 [{} -> {}] for {} journey(s)", projectId, startDate, endDate, journeys.size());
        Dataset<Row> raw = FunnelComputeJob.readS3ByDateRange(spark, s3Base, startDate, endDate);
        if (raw == null) {
            log.warn("No S3 data for project {}", projectId);
            return;
        }
        raw = raw.filter(col("project_id").equalTo(projectId)).cache();
        try {
            for (var journey : journeys) {
                try {
                    var results = computeJourney(raw, journey, runTime, null, null);
                    ch.insertJourneyResults(results);
                    log.info("Journey {}: wrote {} transition rows", journey.id(), results.size());
                } catch (Exception e) {
                    log.error("Journey {} failed: {}", journey.id(), e.getMessage(), e);
                    throw e;
                }
            }
        } finally {
            raw.unpersist();
        }
    }

    private static List<JourneyTransition> computeJourney(Dataset<Row> raw, JourneyDefinition journey,
                                                            String runTime,
                                                            Long startEpochSeconds, Long endEpochSeconds) {
        var identityCol = SparkConstants.Modes.UNIQUE_USERS.equals(journey.mode())
            ? SparkConstants.Columns.USER_ID : SparkConstants.Columns.SESSION_ID;
        boolean isStart = SparkConstants.Modes.START.equals(journey.direction());
        int depth       = journey.depth();
        String anchor   = journey.anchorEvent();

        Dataset<Row> df;
        if (startEpochSeconds != null && endEpochSeconds != null) {
            df = raw.filter(
                    unix_timestamp(col("timestamp")).geq(lit(startEpochSeconds))
                            .and(unix_timestamp(col("timestamp")).leq(lit(endEpochSeconds)))
            );
        } else {
            var endDate   = LocalDate.parse(runTime.substring(0, 10));
            var startDate = endDate.minusDays(journey.dateRange() - 1L);
            df = raw.filter(
                    col("timestamp").cast(DataTypes.DateType).geq(lit(startDate.toString()))
                            .and(col("timestamp").cast(DataTypes.DateType).leq(lit(endDate.toString())))
            );
        }
        df = FunnelComputeJob.applyFilters(df, journey.globalFilters());

        Dataset<Row> events = df
                .select(
                        col(identityCol).alias("identity"),
                        unix_timestamp(col(SparkConstants.Columns.TIMESTAMP)).alias("ts"),
                        col(SparkConstants.Columns.EVENT_NAME)
                )
                .filter(col("identity").isNotNull().and(col("identity").notEqual("")));

        Dataset<Row> anchorTs = events.filter(col(SparkConstants.Columns.EVENT_NAME).equalTo(anchor))
                .groupBy("identity")
                .agg(isStart ? min("ts").alias("ts_anchor") : max("ts").alias("ts_anchor"));

        long anchorCount = anchorTs.count();
        if (anchorCount == 0) {
            log.info("Journey {}: anchor '{}' not found in data", journey.id(), anchor);
            return List.of();
        }

        Dataset<Row> joined = events
                .join(anchorTs, "identity")
                .filter(isStart ? col("ts").gt(col("ts_anchor")) : col("ts").lt(col("ts_anchor")));

        var posWindow = isStart
                ? Window.partitionBy("identity").orderBy(col("ts").asc())
                : Window.partitionBy("identity").orderBy(col("ts").desc());

        Dataset<Row> positioned = isStart
                ? joined.withColumn("pos", row_number().over(posWindow)).filter(col("pos").leq(depth))
                : joined.withColumn("pos", row_number().over(posWindow).multiply(-1)).filter(col("pos").geq(-depth));

        Dataset<Row> anchorRows = anchorTs.select(
                col("identity"),
                col("ts_anchor").alias("ts"),
                lit(anchor).alias("event_name"),
                lit(0).alias("pos")
        );

        Dataset<Row> allPositioned = positioned
                .select("identity", "ts", "event_name", "pos")
                .union(anchorRows)
                .cache();

        var results = new ArrayList<JourneyTransition>();
        results.add(new JourneyTransition(
                journey.id(), journey.projectId(), runTime, journey.direction(),
                -1, "", 0, anchor, anchorCount
        ));

        List<Row> transAgg = allPositioned.alias("curr")
                .join(allPositioned.alias("nxt"),
                        col("curr.identity").equalTo(col("nxt.identity"))
                                .and(col("curr.pos").plus(1).equalTo(col("nxt.pos"))),
                        "inner")
                .groupBy(
                        col("curr.pos").alias("pos_from"),
                        col("curr.event_name").alias("event_from"),
                        col("nxt.pos").alias("pos_to"),
                        col("nxt.event_name").alias("event_to")
                )
                .agg(countDistinct("curr.identity").alias("user_count"))
                .collectAsList();

        allPositioned.unpersist();

        for (Row row : transAgg) {
            results.add(new JourneyTransition(
                    journey.id(), journey.projectId(), runTime, journey.direction(),
                    row.getInt(0), row.getString(1),
                    row.getInt(2), row.getString(3),
                    row.getLong(4)
            ));
        }

        log.info("Journey {}: {} transitions (direction={})", journey.id(), transAgg.size(), journey.direction());
        return results;
    }
}
