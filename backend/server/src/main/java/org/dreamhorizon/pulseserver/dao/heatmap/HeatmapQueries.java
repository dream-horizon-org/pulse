package org.dreamhorizon.pulseserver.dao.heatmap;

public final class HeatmapQueries {

  private HeatmapQueries() {}

  public static final String HEATMAP_AGGREGATE = """
      SELECT
          XBin AS xBin,
          YBin AS yBin,
          OutOfFold AS outOfFold,
          sum(WeightNormal) AS weightNormal,
          sum(WeightRage) AS weightRage,
          sum(WeightDead) AS weightDead
      FROM otel.interaction_heatmaps_daily
      WHERE %s
      GROUP BY XBin, YBin, OutOfFold
      """;

  /**
   * Distinct {@code AppVersion} values in the heatmap slice (same {@code WHERE} as aggregates, with
   * no {@code AppVersion} predicate). Used to pick the latest version string in Java (segment
   * comparison after normalizing {@code _} to {@code .}).
   */
  public static final String DISTINCT_APP_VERSIONS_IN_SLICE = """
      SELECT DISTINCT AppVersion AS app_version
      FROM otel.interaction_heatmaps_daily
      WHERE %s
      """;

  /**
   * Distinct span event names observed on a screen via {@code Events.Attributes['screen.name']}
   * (not span-level {@code SpanAttributes['screen.name']}).
   */
  public static final String DISTINCT_EVENT_NAMES_ON_SCREEN = """
      SELECT DISTINCT event_name AS event_name
      FROM otel.otel_traces
      ARRAY JOIN
          `Events.Name` AS event_name,
          `Events.Attributes` AS event_attrs
      WHERE ProjectId = '%s'
        AND event_attrs['screen.name'] = '%s'
        AND length(`Events.Name`) > 0
        AND Timestamp >= parseDateTime64BestEffort('%s', 9, 'UTC')
        AND Timestamp <= parseDateTime64BestEffort('%s', 9, 'UTC')
      """;

  /**
   * Average Apdex per interaction from interaction spans whose {@code pulse.interaction.name} is in
   * the eligible set (screen association comes from t0 ∈ distinct event names, not span screen).
   * {@code WHERE %s} is built in {@link org.dreamhorizon.pulseserver.service.heatmap.HeatmapServiceImpl}.
   */
  public static final String INTERACTIONS_APDEX_FOR_INTERACTION_NAMES = """
      SELECT
          SpanAttributes['pulse.interaction.name'] AS interaction_name,
          round(avgIf(
              toFloat64OrNull(SpanAttributes['pulse.interaction.apdex_score']),
              SpanAttributes['pulse.interaction.apdex_score'] != ''
          ), 2) AS avg_score
      FROM otel.otel_traces
      WHERE %s
      GROUP BY interaction_name
      HAVING interaction_name != ''
      ORDER BY interaction_name
      """;

  /**
   * Distinct {@code AppVersion} values for a project (any screen/date/breakpoint). Used for
   * screenshot URL resolution when the request does not specify {@code app_version}.
   */
  public static final String DISTINCT_APP_VERSIONS_FOR_PROJECT = """
      SELECT DISTINCT AppVersion AS app_version
      FROM otel.interaction_heatmaps_daily
      WHERE ProjectId = '%s'
        AND AppVersion != ''
      """;
}
