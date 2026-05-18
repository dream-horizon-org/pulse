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
import java.util.Set;

import static org.apache.spark.sql.functions.*;

public class JourneyComputeJob {

    private static final Logger log = LoggerFactory.getLogger(JourneyComputeJob.class);

    public static void runJourneys(SparkSession spark, MysqlRepository mysql, ClickHouseClient ch,
                                    Long referenceId, String s3BucketPrefix, String runTime) throws Exception {
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
            var s3Base  = "s3a://" + s3BucketPrefix + journey.projectId() + "/vector-logs/";

            log.info("Journey {} window [{} -> {}]", journey.id(), startDt, endDt);
            Set<String> propKeys = FunnelFilterPropKeys.collectFromJourney(journey);
            Dataset<Row> raw = FunnelComputeJob.readS3ByHours(spark, s3Base, startDt, endDt, propKeys);
            if (raw == null) {
                log.warn("No S3 data for journey {}", journey.id());
                return;
            }
            raw = raw.filter(col(SparkConstants.VectorLog.PROJECT_ID).equalTo(journey.projectId())).cache();
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
        var s3Base    = "s3a://" + s3Prefix + projectId + "/vector-logs/";

        log.info("Project {} reading S3 [{} -> {}] for {} journey(s)", projectId, startDate, endDate, journeys.size());
        Set<String> propKeys = FunnelFilterPropKeys.collectFromJourneys(journeys);
        Dataset<Row> raw = FunnelComputeJob.readS3ByDateRange(spark, s3Base, startDate, endDate, propKeys);
        if (raw == null) {
            log.warn("No S3 data for project {}", projectId);
            return;
        }
        raw = raw.filter(col(SparkConstants.VectorLog.PROJECT_ID).equalTo(projectId)).cache();
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
        var identityCol = SparkConstants.DefinitionModes.JOURNEY_UNIQUE_USERS.equals(journey.mode())
                ? SparkConstants.Derived.INSTALLATION_ID
                : SparkConstants.VectorLog.SESSION_ID;
        boolean isStart = SparkConstants.DefinitionModes.JOURNEY_DIRECTION_START.equals(journey.direction());
        int depth       = journey.depth();
        String anchor   = journey.anchorEvent();

        Dataset<Row> df;
        if (startEpochSeconds != null && endEpochSeconds != null) {
            df = raw.filter(
                    unix_timestamp(col(SparkConstants.VectorLog.TIMESTAMP)).geq(lit(startEpochSeconds))
                            .and(unix_timestamp(col(SparkConstants.VectorLog.TIMESTAMP)).leq(lit(endEpochSeconds)))
            );
        } else {
            var endDate   = LocalDate.parse(runTime.substring(0, 10));
            var startDate = endDate.minusDays(journey.dateRange() - 1L);
            df = raw.filter(
                    col(SparkConstants.VectorLog.TIMESTAMP).cast(DataTypes.DateType).geq(lit(startDate.toString()))
                            .and(col(SparkConstants.VectorLog.TIMESTAMP).cast(DataTypes.DateType).leq(lit(endDate.toString())))
            );
        }
        df = FunnelComputeJob.applyGlobalFilters(df, journey.globalFilters());

        Dataset<Row> events = df
                .select(
                        col(identityCol).alias(SparkConstants.AnalyticsDataset.IDENTITY),
                        unix_timestamp(col(SparkConstants.VectorLog.TIMESTAMP)).alias(SparkConstants.AnalyticsDataset.TS),
                        col(SparkConstants.VectorLog.EVENT_NAME)
                )
                .filter(col(SparkConstants.AnalyticsDataset.IDENTITY).isNotNull()
                        .and(col(SparkConstants.AnalyticsDataset.IDENTITY).notEqual("")));

        Dataset<Row> anchorTs = events.filter(col(SparkConstants.VectorLog.EVENT_NAME).equalTo(anchor))
                .groupBy(SparkConstants.AnalyticsDataset.IDENTITY)
                .agg(isStart ? min(SparkConstants.AnalyticsDataset.TS).alias(SparkConstants.AnalyticsDataset.TS_ANCHOR)
                        : max(SparkConstants.AnalyticsDataset.TS).alias(SparkConstants.AnalyticsDataset.TS_ANCHOR));

        long anchorCount = anchorTs.count();
        if (anchorCount == 0) {
            log.info("Journey {}: anchor '{}' not found in data", journey.id(), anchor);
            return List.of();
        }

        Dataset<Row> joined = events
                .join(anchorTs, SparkConstants.AnalyticsDataset.IDENTITY)
                .filter(isStart
                        ? col(SparkConstants.AnalyticsDataset.TS).gt(col(SparkConstants.AnalyticsDataset.TS_ANCHOR))
                        : col(SparkConstants.AnalyticsDataset.TS).lt(col(SparkConstants.AnalyticsDataset.TS_ANCHOR)));

        var posWindow = isStart
                ? Window.partitionBy(SparkConstants.AnalyticsDataset.IDENTITY).orderBy(col(SparkConstants.AnalyticsDataset.TS).asc())
                : Window.partitionBy(SparkConstants.AnalyticsDataset.IDENTITY).orderBy(col(SparkConstants.AnalyticsDataset.TS).desc());

        Dataset<Row> positioned = isStart
                ? joined.withColumn(SparkConstants.AnalyticsDataset.POS, row_number().over(posWindow))
                        .filter(col(SparkConstants.AnalyticsDataset.POS).leq(depth))
                : joined.withColumn(SparkConstants.AnalyticsDataset.POS, row_number().over(posWindow).multiply(-1))
                        .filter(col(SparkConstants.AnalyticsDataset.POS).geq(-depth));

        Dataset<Row> anchorRows = anchorTs.select(
                col(SparkConstants.AnalyticsDataset.IDENTITY),
                col(SparkConstants.AnalyticsDataset.TS_ANCHOR).alias(SparkConstants.AnalyticsDataset.TS),
                lit(anchor).alias(SparkConstants.VectorLog.EVENT_NAME),
                lit(0).alias(SparkConstants.AnalyticsDataset.POS)
        );

        Dataset<Row> allPositioned = positioned
                .select(
                        SparkConstants.AnalyticsDataset.IDENTITY,
                        SparkConstants.AnalyticsDataset.TS,
                        SparkConstants.VectorLog.EVENT_NAME,
                        SparkConstants.AnalyticsDataset.POS)
                .union(anchorRows)
                .cache();

        var results = new ArrayList<JourneyTransition>();
        results.add(new JourneyTransition(
                journey.id(), journey.projectId(), runTime, journey.direction(),
                -1, "", 0, anchor, anchorCount
        ));

        List<Row> transAgg = allPositioned.alias(SparkConstants.AnalyticsDataset.JOIN_CURR)
                .join(allPositioned.alias(SparkConstants.AnalyticsDataset.JOIN_NXT),
                        col(SparkConstants.AnalyticsDataset.JOIN_CURR + "." + SparkConstants.AnalyticsDataset.IDENTITY)
                                .equalTo(col(SparkConstants.AnalyticsDataset.JOIN_NXT + "."
                                        + SparkConstants.AnalyticsDataset.IDENTITY))
                                .and(col(SparkConstants.AnalyticsDataset.JOIN_CURR + "." + SparkConstants.AnalyticsDataset.POS)
                                        .plus(1)
                                        .equalTo(col(SparkConstants.AnalyticsDataset.JOIN_NXT + "."
                                                + SparkConstants.AnalyticsDataset.POS))),
                        "inner")
                .groupBy(
                        col(SparkConstants.AnalyticsDataset.JOIN_CURR + "." + SparkConstants.AnalyticsDataset.POS)
                                .alias(SparkConstants.AnalyticsDataset.POS_FROM),
                        col(SparkConstants.AnalyticsDataset.JOIN_CURR + "." + SparkConstants.VectorLog.EVENT_NAME)
                                .alias(SparkConstants.AnalyticsDataset.EVENT_FROM),
                        col(SparkConstants.AnalyticsDataset.JOIN_NXT + "." + SparkConstants.AnalyticsDataset.POS)
                                .alias(SparkConstants.AnalyticsDataset.POS_TO),
                        col(SparkConstants.AnalyticsDataset.JOIN_NXT + "." + SparkConstants.VectorLog.EVENT_NAME)
                                .alias(SparkConstants.AnalyticsDataset.EVENT_TO)
                )
                .agg(countDistinct(col(SparkConstants.AnalyticsDataset.JOIN_CURR + "."
                        + SparkConstants.AnalyticsDataset.IDENTITY))
                        .alias(SparkConstants.AnalyticsDataset.USER_COUNT))
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
