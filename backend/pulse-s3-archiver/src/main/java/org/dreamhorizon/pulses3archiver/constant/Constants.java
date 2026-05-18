package org.dreamhorizon.pulses3archiver.constant;

public final class Constants {

  public static final String SHARED_DATA_KEY = "sharedData";
  public static final String TOPIC_TRACES = "pulse.traces";
  public static final String TOPIC_LOGS = "pulse.logs";
  public static final String TOPIC_METRICS = "pulse.metrics";

  public static final String TABLE_TRACES = "otel_traces";
  public static final String TABLE_LOGS = "otel_logs";
  public static final String TABLE_METRICS_SUM = "otel_metrics_sum";
  public static final String TABLE_METRICS_HISTOGRAM = "otel_metrics_histogram";
  public static final String TABLE_METRICS_EXP_HISTOGRAM = "otel_metrics_exp_histogram";
  public static final String TABLE_METRICS_SUMMARY = "otel_metrics_summary";

  public static final String CONSUMER_GROUP_ID = "pulse-s3-archiver";

  private Constants() {
    throw new UnsupportedOperationException("Utility class");
  }
}
