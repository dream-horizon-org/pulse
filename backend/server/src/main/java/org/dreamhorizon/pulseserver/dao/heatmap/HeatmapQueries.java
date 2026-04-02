package org.dreamhorizon.pulseserver.dao.heatmap;

public final class HeatmapQueries {

  private HeatmapQueries() {}

  public static final String HEATMAP_AGGREGATE = """
      SELECT
          XBin AS xBin,
          YBin AS yBin,
          sum(WeightNormal) AS weightNormal,
          sum(WeightRage) AS weightRage,
          sum(WeightDead) AS weightDead
      FROM otel.interaction_heatmaps_daily
      WHERE %s
      GROUP BY XBin, YBin
      """;

  /**
   * Average Apdex per interaction on a screen, from interaction spans. {@code WHERE %s} is built in
   * {@link org.dreamhorizon.pulseserver.service.heatmap.HeatmapServiceImpl}.
   */
  public static final String INTERACTIONS_APDEX_BY_SCREEN = """
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
}
