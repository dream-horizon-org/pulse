package org.dreamhorizon.pulsespark;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single entry point for all Pulse Spark jobs.
 * <p>
 * Args:
 * --job_type        FUNNELS_DAILY | JOURNEYS_DAILY | EVENTS_INCREMENTAL | FUNNEL | JOURNEY
 * --reference_id    Long (required for FUNNEL / JOURNEY)
 * --run_time        optional UTC literal {@code yyyy-MM-dd HH:mm:ss.SSS}; when set, used for all ClickHouse inserts
 * --otel_logs_bucket  OTEL logs bucket for FUNNEL / FUNNELS_DAILY (default {@link SparkConstants.OtelLogsS3#DEFAULT_BUCKET})
 * --spark_job_id    Long (MySQL analytics_jobs.id row to update; legacy flag name)
 * --secrets_name    optional AWS Secrets Manager name
 * --aws_region      default ap-south-1
 * --s3_bucket_prefix  pooled OTEL-log bucket name ({@link org.dreamhorizon.pulsespark.SparkConstants.OtelLogsS3#DEFAULT_BUCKET})
 *                       layout {@code s3://&lt;bucket&gt;/&lt;projectId&gt;/otel_logs/}
 * --clickhouse_host   hostname or {@code https://host[:port][/path]} (HTTP(S) interface; port optional, default 443 for https)
 * --clickhouse_port   used only when host is not a full URL (default 8123)
 * --mysql_host        hostname or {@code https://host[:port]} for TLS on 443 (tunnel); port arg ignored in URL form
 * --mysql_port        default 3306 when host is not a full URL
 */
public class SparkJobRunner {

  private static final Logger log = LoggerFactory.getLogger(SparkJobRunner.class);
  private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)(ms|s|m|h)$");

  public static void main(String[] args) throws Exception {
    var params = parseArgs(args);
    AwsSecretsHelper.mergeInto(params);

    var jobType = require(params, "job_type");
    String runTime = params.containsKey("run_time")
        ? params.get("run_time")
        : LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    var vectorS3Prefix = params.getOrDefault("s3_bucket_prefix", "pulse-otel-");
    var otelLogsBucket = params.getOrDefault("otel_logs_bucket", SparkConstants.OtelLogsS3.DEFAULT_BUCKET);
    Long analyticsJobId = params.containsKey("spark_job_id") ? Long.parseLong(params.get("spark_job_id")) : null;
    Long referenceId = params.containsKey("reference_id") ? Long.parseLong(params.get("reference_id")) : null;
    validateJobArguments(jobType, referenceId);
    log.info("Spark job starting. job_type={}, reference_id={}, analytics_job_id={}, vector_s3_prefix={}, otel_logs_bucket={}, run_time={}",
        jobType, referenceId, analyticsJobId, vectorS3Prefix, otelLogsBucket, runTime);

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
    ch.ping();
    log.info("ClickHouse connectivity check passed");

    if (analyticsJobId != null) {
      mysql.updateAnalyticsJobRunning(analyticsJobId);
    }

    var spark = SparkSession.builder()
        .appName("pulse-spark-%s-%s".formatted(jobType.toLowerCase(), runTime.substring(0, 10)))
        .config("spark.sql.parquet.int96RebaseModeInRead", "CORRECTED")
        .config("spark.sql.session.timeZone", "UTC")
        .getOrCreate();
    normalizeS3TimeoutConfigs(spark);
    spark.sparkContext().setLogLevel("WARN");
    try {
      var ctx = (org.apache.logging.log4j.core.LoggerContext)
          org.apache.logging.log4j.LogManager.getContext(false);
      var cfg = ctx.getConfiguration();
      var lc = new org.apache.logging.log4j.core.config.LoggerConfig(
          "org.dreamhorizon.pulsespark",
          org.apache.logging.log4j.Level.INFO, true);
      cfg.addLogger("org.dreamhorizon.pulsespark", lc);
      ctx.updateLoggers();
    } catch (Exception | NoClassDefFoundError ignored) {
      // log4j2 not available (e.g. local Spark 4.x) — fall back silently
    }

    boolean failed = false;
    String errorMessage = null;
    try {
      try {
        log.info("Dispatching job_type={} (attempt=1)", jobType);
        dispatch(jobType, spark, mysql, ch, referenceId, vectorS3Prefix, otelLogsBucket, runTime);
      } catch (Exception firstEx) {
        log.warn("Job {} failed on first attempt: {} — retrying once", jobType, firstEx.getMessage(), firstEx);
        log.info("Dispatching job_type={} (attempt=2)", jobType);
        dispatch(jobType, spark, mysql, ch, referenceId, vectorS3Prefix, otelLogsBucket, runTime);
      }
      log.info("Job {} completed successfully", jobType);

      if (analyticsJobId != null) {
        mysql.updateAnalyticsJobSucceeded(analyticsJobId);
      }
    } catch (Exception e) {
      failed = true;
      errorMessage = e.getMessage();
      log.error("Job {} failed after retry: {}", jobType, errorMessage, e);
      if (analyticsJobId != null) {
        try {
          mysql.updateAnalyticsJobFailed(analyticsJobId, errorMessage);
        } catch (Exception ex) {
          log.error("Failed to update analytics_jobs status: {}", ex.getMessage());
        }
      }
    } finally {
      spark.stop();
    }

    if (failed) {
      System.exit(1);
    }
  }

  private static void dispatch(String jobType, SparkSession spark, MysqlRepository mysql,
                               ClickHouseClient ch, Long referenceId,
                               String vectorS3Prefix, String otelLogsBucket, String runTime) throws Exception {
    switch (jobType) {
      case "FUNNEL" -> FunnelComputeJob.runFunnels(spark, mysql, ch, referenceId, otelLogsBucket, runTime);
      case "JOURNEY" -> JourneyComputeJob.runJourneys(spark, mysql, ch, referenceId, vectorS3Prefix, runTime);
      case "FUNNELS_DAILY" -> FunnelComputeJob.runFunnels(spark, mysql, ch, null, otelLogsBucket, runTime);
      case "JOURNEYS_DAILY" -> JourneyComputeJob.runJourneys(spark, mysql, ch, null, vectorS3Prefix, runTime);
      case "EVENTS_INCREMENTAL" -> EventCatalogJob.runCatalog(spark, mysql, ch, vectorS3Prefix, runTime);
      default -> throw new IllegalArgumentException("Unknown job_type: " + jobType);
    }
  }

  private static Map<String, String> parseArgs(String[] args) {
    var map = new HashMap<String, String>();
    for (int i = 0; i < args.length - 1; i += 2) {
      if (args[i].startsWith("--")) {
        map.put(args[i].substring(2), args[i + 1]);
      }
    }
    return map;
  }

  private static String require(Map<String, String> params, String key) {
    var v = params.get(key);
    if (v == null) {
      throw new IllegalArgumentException("Missing required argument: --" + key);
    }
    return v;
  }

  private static void validateJobArguments(String jobType, Long referenceId) {
    if ("FUNNEL".equals(jobType) && referenceId == null) {
      throw new IllegalArgumentException("Missing required argument: --reference_id for job_type=FUNNEL");
    }
    if ("FUNNELS_DAILY".equals(jobType) && referenceId != null) {
      throw new IllegalArgumentException("Do not provide --reference_id for job_type=FUNNELS_DAILY");
    }
    if ("JOURNEY".equals(jobType) && referenceId == null) {
      throw new IllegalArgumentException("Missing required argument: --reference_id for job_type=JOURNEY");
    }
    if ("JOURNEYS_DAILY".equals(jobType) && referenceId != null) {
      throw new IllegalArgumentException("Do not provide --reference_id for job_type=JOURNEYS_DAILY");
    }
  }

  private static void normalizeS3TimeoutConfigs(SparkSession spark) {
    var hadoopConf = spark.sparkContext().hadoopConfiguration();
    String[] keys = {
        "fs.s3a.connection.establish.timeout",
        "fs.s3a.connection.timeout",
        "fs.s3a.connection.request.timeout",
        "fs.s3a.connection.acquisition.timeout",
        "fs.s3a.socket.timeout",
        "fs.s3a.attempts.maximum"
    };

    for (String key : keys) {
      var current = hadoopConf.get(key);
      if (current == null || current.isBlank()) {
        continue;
      }
      var normalized = normalizeDurationLikeValue(current);
      if (!current.equals(normalized)) {
        hadoopConf.set(key, normalized);
        log.warn("Normalized Hadoop config {} from '{}' to '{}'", key, current, normalized);
      }
    }
  }

  private static String normalizeDurationLikeValue(String value) {
    var v = value.trim().toLowerCase();
    var matcher = DURATION_PATTERN.matcher(v);
    if (!matcher.matches()) {
      return value;
    }
    long amount = Long.parseLong(matcher.group(1));
    String unit = matcher.group(2);
    long millis;
    switch (unit) {
      case "ms":
        millis = amount;
        break;
      case "s":
        millis = amount * 1000L;
        break;
      case "m":
        millis = amount * 60_000L;
        break;
      case "h":
        millis = amount * 3_600_000L;
        break;
      default:
        return value;
    }
    return String.valueOf(millis);
  }
}
