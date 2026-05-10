package org.dreamhorizon.pulseserver.dao.webvitals;

import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;

/**
 * Web vitals queries use {@link ClickhouseConstants#CH_RESOURCE_IS_PULSE_WEB_PREDICATE} so Pulse Web
 * sessions match SDK identity ({@code telemetry.sdk.name}, {@code rum.sdk.name}, {@code platform}),
 * not the CH {@code Platform} column (materialized from {@code os.name} / browser UA).
 */
public final class WebVitalsQueries {

  private WebVitalsQueries() {}

  public static final String GET_WEB_VITALS_SUMMARY =
      """
        SELECT LogAttributes['web_vital.name'] AS vital_name,
          quantile(0.75)(toFloat64(LogAttributes['web_vital.value'])) AS p75,
          countIf(LogAttributes['web_vital.rating'] = 'good') AS good_count,
          countIf(LogAttributes['web_vital.rating'] = 'needs-improvement') AS needs_improvement_count,
          countIf(LogAttributes['web_vital.rating'] = 'poor') AS poor_count,
          count() AS total_count
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN parseDateTime64BestEffort('${start_time}') AND parseDateTime64BestEffort('${end_time}')
          AND PulseType = 'web_vital'
          AND """
          + ClickhouseConstants.CH_RESOURCE_IS_PULSE_WEB_PREDICATE
          + """
          AND LogAttributes['web_vital.name'] != ''
        GROUP BY vital_name LIMIT 10
        """;

  public static final String GET_WEB_VITALS_SUMMARY_FOR_SCREEN =
      """
        SELECT LogAttributes['web_vital.name'] AS vital_name,
          quantile(0.75)(toFloat64(LogAttributes['web_vital.value'])) AS p75,
          countIf(LogAttributes['web_vital.rating'] = 'good') AS good_count,
          countIf(LogAttributes['web_vital.rating'] = 'needs-improvement') AS needs_improvement_count,
          countIf(LogAttributes['web_vital.rating'] = 'poor') AS poor_count,
          count() AS total_count
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN parseDateTime64BestEffort('${start_time}') AND parseDateTime64BestEffort('${end_time}')
          AND PulseType = 'web_vital'
          AND """
          + ClickhouseConstants.CH_RESOURCE_IS_PULSE_WEB_PREDICATE
          + """
          AND LogAttributes['web_vital.name'] != ''
          AND ScreenName = '${screen_name}'
        GROUP BY vital_name LIMIT 10
        """;

  public static final String GET_WEB_VITALS_TREND =
      """
        SELECT toStartOfInterval(Timestamp, INTERVAL ${bucket_minutes} MINUTE) AS bucket,
          quantile(0.75)(toFloat64(LogAttributes['web_vital.value'])) AS p75
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN parseDateTime64BestEffort('${start_time}') AND parseDateTime64BestEffort('${end_time}')
          AND PulseType = 'web_vital'
          AND """
          + ClickhouseConstants.CH_RESOURCE_IS_PULSE_WEB_PREDICATE
          + """
          AND LogAttributes['web_vital.name'] = '${vital_name}'
        GROUP BY bucket ORDER BY bucket ASC LIMIT 100
        """;

  public static final String GET_WEB_VITALS_TREND_FOR_SCREEN =
      """
        SELECT toStartOfInterval(Timestamp, INTERVAL ${bucket_minutes} MINUTE) AS bucket,
          quantile(0.75)(toFloat64(LogAttributes['web_vital.value'])) AS p75
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN parseDateTime64BestEffort('${start_time}') AND parseDateTime64BestEffort('${end_time}')
          AND PulseType = 'web_vital'
          AND """
          + ClickhouseConstants.CH_RESOURCE_IS_PULSE_WEB_PREDICATE
          + """
          AND LogAttributes['web_vital.name'] = '${vital_name}'
          AND ScreenName = '${screen_name}'
        GROUP BY bucket ORDER BY bucket ASC LIMIT 100
        """;

  public static final String GET_WEB_VITALS_BY_SCREEN =
      """
        SELECT ScreenName AS screen_name,
          quantile(0.75)(toFloat64(LogAttributes['web_vital.value'])) AS p75,
          count() AS total_count,
          countIf(LogAttributes['web_vital.rating'] = 'good') * 100.0 / count() AS good_pct
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN parseDateTime64BestEffort('${start_time}') AND parseDateTime64BestEffort('${end_time}')
          AND PulseType = 'web_vital'
          AND """
          + ClickhouseConstants.CH_RESOURCE_IS_PULSE_WEB_PREDICATE
          + """
          AND LogAttributes['web_vital.name'] = '${vital_name}'
          AND ScreenName != ''
        GROUP BY screen_name ORDER BY total_count DESC LIMIT 20
        """;
}
