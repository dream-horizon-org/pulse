package org.dreamhorizon.pulseserver.dao.webvitals;

public final class WebVitalsQueries {

  private WebVitalsQueries() {}

  public static final String GET_WEB_VITALS_SUMMARY =
      """
        SELECT Attributes['web_vital.name'] AS vital_name,
          quantile(0.75)(toFloat64(Attributes['web_vital.value'])) AS p75,
          countIf(Attributes['web_vital.rating'] = 'good') AS good_count,
          countIf(Attributes['web_vital.rating'] = 'needs-improvement') AS needs_improvement_count,
          countIf(Attributes['web_vital.rating'] = 'poor') AS poor_count,
          count() AS total_count
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN '${start_time}' AND '${end_time}'
          AND PulseType = 'web_vital' AND Platform = 'web'
          AND Attributes['web_vital.name'] != ''
        GROUP BY vital_name LIMIT 10
        """;

  public static final String GET_WEB_VITALS_SUMMARY_FOR_SCREEN =
      """
        SELECT Attributes['web_vital.name'] AS vital_name,
          quantile(0.75)(toFloat64(Attributes['web_vital.value'])) AS p75,
          countIf(Attributes['web_vital.rating'] = 'good') AS good_count,
          countIf(Attributes['web_vital.rating'] = 'needs-improvement') AS needs_improvement_count,
          countIf(Attributes['web_vital.rating'] = 'poor') AS poor_count,
          count() AS total_count
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN '${start_time}' AND '${end_time}'
          AND PulseType = 'web_vital' AND Platform = 'web'
          AND Attributes['web_vital.name'] != ''
          AND ScreenName = '${screen_name}'
        GROUP BY vital_name LIMIT 10
        """;

  public static final String GET_WEB_VITALS_TREND =
      """
        SELECT toStartOfInterval(Timestamp, INTERVAL ${bucket_minutes} MINUTE) AS bucket,
          quantile(0.75)(toFloat64(Attributes['web_vital.value'])) AS p75
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN '${start_time}' AND '${end_time}'
          AND PulseType = 'web_vital' AND Platform = 'web'
          AND Attributes['web_vital.name'] = '${vital_name}'
        GROUP BY bucket ORDER BY bucket ASC LIMIT 100
        """;

  public static final String GET_WEB_VITALS_TREND_FOR_SCREEN =
      """
        SELECT toStartOfInterval(Timestamp, INTERVAL ${bucket_minutes} MINUTE) AS bucket,
          quantile(0.75)(toFloat64(Attributes['web_vital.value'])) AS p75
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN '${start_time}' AND '${end_time}'
          AND PulseType = 'web_vital' AND Platform = 'web'
          AND Attributes['web_vital.name'] = '${vital_name}'
          AND ScreenName = '${screen_name}'
        GROUP BY bucket ORDER BY bucket ASC LIMIT 100
        """;

  public static final String GET_WEB_VITALS_BY_SCREEN =
      """
        SELECT ScreenName AS screen_name,
          quantile(0.75)(toFloat64(Attributes['web_vital.value'])) AS p75,
          count() AS total_count,
          countIf(Attributes['web_vital.rating'] = 'good') * 100.0 / count() AS good_pct
        FROM otel.otel_logs
        WHERE ProjectId = '${project_id}' AND Timestamp BETWEEN '${start_time}' AND '${end_time}'
          AND PulseType = 'web_vital' AND Platform = 'web'
          AND Attributes['web_vital.name'] = '${vital_name}'
          AND ScreenName != ''
        GROUP BY screen_name ORDER BY total_count DESC LIMIT 20
        """;
}
