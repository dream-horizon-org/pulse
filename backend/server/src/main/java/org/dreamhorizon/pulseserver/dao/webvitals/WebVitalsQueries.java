package org.dreamhorizon.pulseserver.dao.webvitals;

import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;

/**
 * Web vitals queries filter {@code Platform = 'web'} (materialized from {@code ResourceAttributes['os.name']};
 * Pulse Web SDK sets {@code os.name} to {@code web} for parity with coarse mobile {@code Platform}).
 *
 * <p>Vital name, value, and rating use materialized columns {@code WebVitalName}, {@code WebVitalValue},
 * {@code WebVitalRating} on {@code otel.otel_logs} (see DDL) instead of repeated {@code LogAttributes[...]}
 * access.
 */
public final class WebVitalsQueries {

  private WebVitalsQueries() {}

  public static final String GET_WEB_VITALS_SUMMARY =
      """
        SELECT WebVitalName AS vital_name,
          quantile(0.75)(WebVitalValue) AS p75,
          countIf(WebVitalRating = 'good') AS good_count,
          countIf(WebVitalRating = 'needs-improvement') AS needs_improvement_count,
          countIf(WebVitalRating = 'poor') AS poor_count,
          count() AS total_count
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN parseDateTime64BestEffort('${start_time}') AND parseDateTime64BestEffort('${end_time}')
          AND PulseType = 'web_vital'
          """
          + ClickhouseConstants.CH_PLATFORM_IS_WEB
          + """
          AND WebVitalName != ''
        GROUP BY vital_name LIMIT 10
        """;

  public static final String GET_WEB_VITALS_SUMMARY_FOR_SCREEN =
      """
        SELECT WebVitalName AS vital_name,
          quantile(0.75)(WebVitalValue) AS p75,
          countIf(WebVitalRating = 'good') AS good_count,
          countIf(WebVitalRating = 'needs-improvement') AS needs_improvement_count,
          countIf(WebVitalRating = 'poor') AS poor_count,
          count() AS total_count
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN parseDateTime64BestEffort('${start_time}') AND parseDateTime64BestEffort('${end_time}')
          AND PulseType = 'web_vital'
          """
          + ClickhouseConstants.CH_PLATFORM_IS_WEB
          + """
          AND WebVitalName != ''
          AND ScreenName = '${screen_name}'
        GROUP BY vital_name LIMIT 10
        """;

  public static final String GET_WEB_VITALS_TREND =
      """
        SELECT toStartOfInterval(Timestamp, INTERVAL ${bucket_minutes} MINUTE) AS bucket,
          quantile(0.75)(WebVitalValue) AS p75
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN parseDateTime64BestEffort('${start_time}') AND parseDateTime64BestEffort('${end_time}')
          AND PulseType = 'web_vital'
          """
          + ClickhouseConstants.CH_PLATFORM_IS_WEB
          + """
          AND WebVitalName = '${vital_name}'
        GROUP BY bucket ORDER BY bucket ASC LIMIT 100
        """;

  public static final String GET_WEB_VITALS_TREND_FOR_SCREEN =
      """
        SELECT toStartOfInterval(Timestamp, INTERVAL ${bucket_minutes} MINUTE) AS bucket,
          quantile(0.75)(WebVitalValue) AS p75
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN parseDateTime64BestEffort('${start_time}') AND parseDateTime64BestEffort('${end_time}')
          AND PulseType = 'web_vital'
          """
          + ClickhouseConstants.CH_PLATFORM_IS_WEB
          + """
          AND WebVitalName = '${vital_name}'
          AND ScreenName = '${screen_name}'
        GROUP BY bucket ORDER BY bucket ASC LIMIT 100
        """;

  public static final String GET_WEB_VITALS_BY_SCREEN =
      """
        SELECT ScreenName AS screen_name,
          quantile(0.75)(WebVitalValue) AS p75,
          count() AS total_count,
          countIf(WebVitalRating = 'good') * 100.0 / count() AS good_pct
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN parseDateTime64BestEffort('${start_time}') AND parseDateTime64BestEffort('${end_time}')
          AND PulseType = 'web_vital'
          """
          + ClickhouseConstants.CH_PLATFORM_IS_WEB
          + """
          AND WebVitalName = '${vital_name}'
          AND ScreenName != ''
        GROUP BY screen_name ORDER BY total_count DESC LIMIT 20
        """;
}
