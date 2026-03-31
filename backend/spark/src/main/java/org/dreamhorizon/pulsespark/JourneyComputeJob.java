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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.apache.spark.sql.functions.*;

public class JourneyComputeJob {

    private static final Logger log = LoggerFactory.getLogger(JourneyComputeJob.class);

    // -------------------------------------------------------------------------
    // Public entry point (called from SparkJobRunner)
    // -------------------------------------------------------------------------

    public static void runJourneys(SparkSession spark, MysqlRepository mysql, ClickHouseClient ch,
                                    Long referenceId, String s3BucketPrefix, String runTime) throws Exception {
        List<JourneyDefinition> journeys = mysql.fetchJourneys(referenceId);
        if (journeys.isEmpty()) {
            log.info("No journeys found — nothing to do.");
            return;
        }
        log.info("Processing {} journey(s), referenceId={}, run_time={}", journeys.size(), referenceId, runTime);

        if (referenceId != null) {
            var journey = journeys.get(0);
            var s3Base  = "s3a://" + s3BucketPrefix + journey.projectId() + "/vector-logs/";
            Dataset<Row> raw;
            String actualRunTime;

            if (journey.startTime() != null && journey.endTime() != null) {
                var startDt = journey.startTime().toLocalDateTime();
                var endDt   = journey.endTime().toLocalDateTime();
                actualRunTime = runTime;
                raw = FunnelComputeJob.readS3ByHours(spark, s3Base, startDt, endDt);
            } else {
                actualRunTime = runTime;
                var endDate   = LocalDate.parse(runTime.substring(0, 10));
                var startDate = endDate.minusDays(journey.dateRange() - 1L);
                raw = FunnelComputeJob.readS3ByDateRange(spark, s3Base, startDate, endDate);
            }

            if (raw == null) {
                log.warn("No data found on S3 for journey {}", journey.id());
                return;
            }
            raw = raw.filter(col("project_id").equalTo(journey.projectId())).cache();
            try {
                var results = computeJourney(raw, journey, actualRunTime);
                ch.deleteJourneyResults(journey.id(), actualRunTime);
                ch.insertJourneyResults(results);
                log.info("Wrote {} transition rows for journey {}", results.size(), journey.id());
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
                    log.error("Project {} failed: {}", entry.getKey(), e.getMessage(), e);
                    throw e;
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Bulk (AUTO) project processing
    // -------------------------------------------------------------------------

    private static void processProjectBulk(SparkSession spark, ClickHouseClient ch,
                                            String projectId, List<JourneyDefinition> journeys,
                                            String s3Prefix, String runTime) {
        int maxDays   = journeys.stream().mapToInt(JourneyDefinition::dateRange).max().orElse(7);
        var endDate   = LocalDate.parse(runTime.substring(0, 10));
        var startDate = endDate.minusDays(maxDays - 1L);

        var s3Base = "s3a://" + s3Prefix + projectId + "/vector-logs/";
        log.info("Reading S3 {} [{} → {}] for {} journey(s)", s3Base, startDate, endDate, journeys.size());

        Dataset<Row> raw = FunnelComputeJob.readS3ByDateRange(spark, s3Base, startDate, endDate);
        if (raw == null) {
            log.warn("No S3 data for project {}", projectId);
            return;
        }
        raw = raw.filter(col("project_id").equalTo(projectId)).cache();

        try {
            for (var journey : journeys) {
                try {
                    var results = computeJourney(raw, journey, runTime);
                    ch.deleteJourneyResults(journey.id(), runTime);
                    ch.insertJourneyResults(results);
                    log.info("Wrote {} transition rows for journey {}", results.size(), journey.id());
                } catch (Exception e) {
                    log.error("Journey {} failed: {}", journey.id(), e.getMessage(), e);
                    throw e;
                }
            }
        } finally {
            raw.unpersist();
        }
    }

    // -------------------------------------------------------------------------
    // Journey computation — anchor-based path graph
    // -------------------------------------------------------------------------

    private static List<JourneyTransition> computeJourney(Dataset<Row> raw,
                                                            JourneyDefinition journey,
                                                            String runTime) {
        var identityCol  = "UNIQUE_USERS".equals(journey.mode()) ? "user_id" : "session_id";
        boolean isStart  = "START".equals(journey.direction());
        int depth        = journey.depth();
        String anchor    = journey.anchorEvent();

        var endDate   = LocalDate.parse(runTime.substring(0, 10));
        var startDate = endDate.minusDays(journey.dateRange() - 1L);
        Dataset<Row> df = raw.filter(
                col("timestamp").cast(DataTypes.DateType).geq(lit(startDate.toString()))
                        .and(col("timestamp").cast(DataTypes.DateType).leq(lit(endDate.toString())))
        );
        df = FunnelComputeJob.applyFilters(df, journey.globalFilters());

        // Base event dataset: (identity, ts, event_name)
        Dataset<Row> events = df
                .select(
                        col(identityCol).alias("identity"),
                        unix_timestamp(col("timestamp")).alias("ts"),
                        col("event_name")
                )
                .filter(col("identity").isNotNull().and(col("identity").notEqual("")));

        // Find anchor timestamp per identity (first for START, last for END)
        Dataset<Row> anchorEvts = events.filter(col("event_name").equalTo(anchor));
        Dataset<Row> anchorTs;
        if (isStart) {
            anchorTs = anchorEvts
                    .groupBy("identity")
                    .agg(min("ts").alias("ts_anchor"));
        } else {
            anchorTs = anchorEvts
                    .groupBy("identity")
                    .agg(max("ts").alias("ts_anchor"));
        }

        // Distinct identities that hit the anchor
        long anchorCount = anchorTs.count();
        if (anchorCount == 0) {
            log.info("Journey {}: anchor '{}' not found in data", journey.id(), anchor);
            return List.of();
        }

        // Get surrounding events joined with anchor timestamp
        Dataset<Row> joined = events
                .join(anchorTs, "identity")
                .filter(isStart
                        ? col("ts").gt(col("ts_anchor"))
                        : col("ts").lt(col("ts_anchor")));

        // Assign pos: for START: 1,2,3... (asc); for END: -1,-2,-3... (desc)
        // curr.pos + 1 = next.pos works for both:
        //   START: 0+1=1, 1+1=2 ... (anchor at 0, surrounding at 1,2,3)
        //   END:   -2+1=-1, -1+1=0 (anchor at 0, surrounding at -1,-2,-3)
        var posWindow = isStart
                ? Window.partitionBy("identity").orderBy(col("ts").asc())
                : Window.partitionBy("identity").orderBy(col("ts").desc());

        Dataset<Row> positioned;
        if (isStart) {
            positioned = joined
                    .withColumn("pos", row_number().over(posWindow))  // 1,2,3...
                    .filter(col("pos").leq(depth));
        } else {
            positioned = joined
                    .withColumn("pos", row_number().over(posWindow).multiply(-1))  // -1,-2,-3...
                    .filter(col("pos").geq(-depth));
        }

        // Add anchor events at pos=0 for all identities that hit the anchor
        Dataset<Row> anchorRows = anchorTs
                .select(
                        col("identity"),
                        col("ts_anchor").alias("ts"),
                        lit(anchor).alias("event_name"),
                        lit(0).alias("pos")
                );

        Dataset<Row> allPositioned = positioned
                .select("identity", "ts", "event_name", "pos")
                .union(anchorRows)
                .cache();

        // ENTRY edge: ENTRY(-1,"") → anchor(0, anchorEvent), count = distinct identities at anchor
        var results = new ArrayList<JourneyTransition>();
        results.add(new JourneyTransition(
                journey.id(), journey.projectId(), runTime, journey.direction(),
                -1, "",
                0, anchor,
                anchorCount
        ));

        // Transition edges: self-join on curr.pos + 1 = next.pos
        Dataset<Row> curr = allPositioned.alias("curr");
        Dataset<Row> nxt  = allPositioned.alias("nxt");

        Column joinCond = col("curr.identity").equalTo(col("nxt.identity"))
                .and(col("curr.pos").plus(1).equalTo(col("nxt.pos")));

        List<Row> transAgg = curr.join(nxt, joinCond, "inner")
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

        log.info("Journey {}: {} transition(s) (direction={})", journey.id(), transAgg.size(), journey.direction());
        return results;
    }
}
