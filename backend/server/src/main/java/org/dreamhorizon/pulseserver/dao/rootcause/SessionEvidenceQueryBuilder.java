package org.dreamhorizon.pulseserver.dao.rootcause;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SessionEvidenceQueryBuilder {

  private static final String DEFAULT_APDEX_THRESHOLD_MS = "1000";
  private static final int DEFAULT_LIMIT = 5;

  private SessionEvidenceQueryBuilder() {}

  /**
   * Build a ClickHouse query to find sessions WORSE THAN the segment itself.
   *
   * Query logic:
   * 1. RCA identifies a segment with specific deltas (e.g., error_rate +28%, poor_interactions +35%)
   * 2. Within that segment, find sessions that EXCEED those deltas
   * 3. Return top sessions by both metrics combined
   *
   * Example:
   * - Segment delta: error_rate_delta = 28%, poor_interaction_delta = 35%
   * - Find sessions where: error_count > (0.28 * avg_interactions) AND poor_count > (0.35 * avg_interactions)
   * - Rank by: error_count DESC, poor_interaction_count DESC
   *
   * @param projectId project scope
   * @param interactionName span.name to filter
   * @param startTime inclusive window start
   * @param endTime exclusive window end
   * @param segmentDimensions dimension filters
   * @param segmentDeltas error_rate and apdex deltas from RCA analysis
   * @param limit max sessions to return
   * @return ClickHouse SQL query string
   */
  public static String buildSessionEvidenceQuery(
      String projectId,
      String interactionName,
      Instant startTime,
      Instant endTime,
      Map<String, String> segmentDimensions,
      Map<String, Double> segmentDeltas,
      Integer limit) {

    String effectiveLimit = limit != null ? limit.toString() : String.valueOf(DEFAULT_LIMIT);

    // Extract deltas (values are percentages, e.g., 28.0 for 28%)
    Double errorRateDelta = segmentDeltas != null ? segmentDeltas.getOrDefault("error_rate", 0.0) : 0.0;
    Double poorInteractionDelta = segmentDeltas != null ? segmentDeltas.getOrDefault("poor_interaction", 0.0) : 0.0;

    // Convert percentages to decimals for comparison
    // e.g., 28% delta -> 0.28
    double errorRateThreshold = errorRateDelta / 100.0;
    double poorInteractionThreshold = poorInteractionDelta / 100.0;

    StringBuilder query = new StringBuilder();

    query.append("SELECT \n")
        .append("  SessionId,\n")
        .append("  countIf(is_error = 'true') as error_count,\n")
        .append("  count() as total_interactions,\n")
        .append("  countIf(apdex_score < 0.5) as poor_interaction_count,\n")
        .append("  avg(toFloat32(apdex_score)) as avg_apdex,\n")
        .append("  (error_count / total_interactions) as error_rate,\n")
        .append("  (poor_interaction_count / total_interactions) as poor_interaction_rate\n")
        .append("FROM (\n")
        .append("  SELECT\n")
        .append("    SessionId,\n")
        .append("    SpanAttributes['pulse.interaction.is_error'] as is_error,\n")
        .append("    toFloat32(SpanAttributes['pulse.interaction.apdex_score']) as apdex_score\n")
        .append("  FROM otel_traces\n")
        .append("  WHERE\n")
        .append("    ProjectId = '")
        .append(escapeStringLiteral(projectId))
        .append("'\n")
        .append("    AND SpanName = '")
        .append(escapeStringLiteral(interactionName))
        .append("'\n")
        .append("    AND Timestamp >= '")
        .append(startTime)
        .append("'\n")
        .append("    AND Timestamp < '")
        .append(endTime)
        .append("'\n")
        .append("    AND SessionId != ''\n");

    appendDimensionFilters(query, segmentDimensions);

    query.append(")\n")
        .append("GROUP BY SessionId\n")
        .append("HAVING\n")
        // Filter: Sessions where error_rate > segment_delta_error_rate
        .append("  error_rate > ").append(errorRateThreshold).append("\n")
        // Filter: Sessions where poor_interaction_rate > segment_delta_poor_interaction_rate
        .append("  AND poor_interaction_rate > ").append(poorInteractionThreshold).append("\n")
        // Sort: By error_count DESC (most errors first)
        .append("ORDER BY\n")
        .append("  error_count DESC,\n")
        // Secondary: By poor_interaction_count DESC (most poor interactions second)
        .append("  poor_interaction_count DESC\n")
        .append("LIMIT ")
        .append(effectiveLimit)
        .append("\n");

    return query.toString();
  }

  /**
   * Backward compatible overload: if deltas not provided, default to > 0.
   */
  public static String buildSessionEvidenceQuery(
      String projectId,
      String interactionName,
      Instant startTime,
      Instant endTime,
      Map<String, String> segmentDimensions,
      Integer limit) {
    return buildSessionEvidenceQuery(
        projectId,
        interactionName,
        startTime,
        endTime,
        segmentDimensions,
        null,  // No deltas provided
        limit);
  }

  /**
   * Build query to count total distinct sessions in segment (for totalSessionsCount).
   */
  public static String buildTotalSessionsCountQuery(
      String projectId,
      String interactionName,
      Instant startTime,
      Instant endTime,
      Map<String, String> segmentDimensions) {

    StringBuilder query = new StringBuilder();

    query.append("SELECT uniqCombined64(nullIf(SessionId, '')) as total_sessions\n")
        .append("FROM otel_traces\n")
        .append("WHERE\n")
        .append("  ProjectId = '")
        .append(escapeStringLiteral(projectId))
        .append("'\n")
        .append("  AND SpanName = '")
        .append(escapeStringLiteral(interactionName))
        .append("'\n")
        .append("  AND Timestamp >= '")
        .append(startTime)
        .append("'\n")
        .append("  AND Timestamp < '")
        .append(endTime)
        .append("'\n")
        .append("  AND SessionId != ''\n");

    appendDimensionFilters(query, segmentDimensions);

    return query.toString();
  }

  /**
   * Determine which metric is the root cause and return appropriate ORDER BY clause.
   * Compares absolute values of deltas to find worst metric.
   */
  public static String determineSortOrder(Map<String, Double> segmentDeltas) {
    if (segmentDeltas == null || segmentDeltas.isEmpty()) {
      return "ORDER BY error_rate DESC, apdex_score ASC";
    }

    Double errorRateDelta = segmentDeltas.getOrDefault("error_rate", 0.0);
    Double apdexDelta = segmentDeltas.getOrDefault("apdex", 0.0);

    double errorRateMagnitude = Math.abs(errorRateDelta);
    double apdexMagnitude = Math.abs(apdexDelta);

    if (errorRateMagnitude >= apdexMagnitude) {
      return "ORDER BY error_rate DESC, apdex_score ASC";
    } else {
      return "ORDER BY apdex_score ASC, error_rate DESC";
    }
  }

  /**
   * Determine primary sort metric based on segment deltas.
   */
  public static String determinePrimarySortMetric(Map<String, Double> segmentDeltas) {
    if (segmentDeltas == null || segmentDeltas.isEmpty()) {
      return "error_rate";
    }

    Double errorRateDelta = segmentDeltas.getOrDefault("error_rate", 0.0);
    Double apdexDelta = segmentDeltas.getOrDefault("apdex", 0.0);

    double errorRateMagnitude = Math.abs(errorRateDelta);
    double apdexMagnitude = Math.abs(apdexDelta);

    if (errorRateMagnitude >= apdexMagnitude) {
      return "error_rate";
    } else {
      return "apdex";
    }
  }

  private static void appendDimensionFilters(
      StringBuilder query, Map<String, String> segmentDimensions) {
    if (segmentDimensions == null || segmentDimensions.isEmpty()) {
      return;
    }

    for (Map.Entry<String, String> entry : segmentDimensions.entrySet()) {
      String dimensionName = entry.getKey();
      String dimensionValue = entry.getValue();

      query.append("    AND ")
          .append(dimensionName)
          .append(" = '")
          .append(escapeStringLiteral(dimensionValue))
          .append("'\n");
    }
  }

  /**
   * Escape single quotes in string literals for ClickHouse SQL.
   */
  private static String escapeStringLiteral(String value) {
    return value.replace("'", "''");
  }
}
