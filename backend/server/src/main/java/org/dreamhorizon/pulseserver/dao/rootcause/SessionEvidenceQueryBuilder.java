package org.dreamhorizon.pulseserver.dao.rootcause;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
   * Build a ClickHouse query to find sessions within a segment that are WORSE than the segment itself.
   *
   * Query logic:
   * 1. RCA identifies a segment with specific metrics (e.g., error_rate 5%, apdex 0.035)
   * 2. Within that segment's dimensions, find sessions that are worse than the segment
   * 3. Return top sessions by error rate and apdex (worst first)
   *
   * Note: Uses HAVING clause to filter for sessions worse than segment metrics.
   * This ensures we get the most problematic sessions as evidence.
   *
   * @param projectId project scope
   * @param interactionName span.name to filter
   * @param startTime inclusive window start
   * @param endTime exclusive window end
   * @param segmentDimensions dimension filters
   * @param segmentMetrics segment's own metrics (error_rate, apdex) used as thresholds
   * @param limit max sessions to return
   * @return ClickHouse SQL query string
   */
  //TODO: Have Standardisation in clickhouse query
  public static String buildSessionEvidenceQuery(
      String projectId,
      String interactionName,
      Instant startTime,
      Instant endTime,
      Map<String, String> segmentDimensions,
      Map<String, Double> segmentMetrics,
      Integer limit) {

    String effectiveLimit = limit != null ? limit.toString() : String.valueOf(DEFAULT_LIMIT);

    // Extract segment's metrics - these are thresholds for finding worse sessions
    // error_rate: percentage (e.g., 5 for 5% error rate in the segment)
    // apdex: absolute value (e.g., 0.035 for apdex score of 0.035 in the segment)
    Double errorRateThreshold = segmentMetrics != null ? segmentMetrics.getOrDefault("error_rate", 0.0) : 0.0;
    Double apdexThreshold = segmentMetrics != null ? segmentMetrics.getOrDefault("apdex", 1.0) : 1.0;

    // Convert error_rate percentage to decimal
    // e.g., 5% -> 0.05
    double errorRateThresholdDecimal = errorRateThreshold / 100.0;

    StringBuilder query = new StringBuilder();

    // Format timestamps for ClickHouse: convert from ISO format to "YYYY-MM-DD HH:MM:SS"
    DateTimeFormatter chFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    String formattedStartTime = chFormatter.format(startTime);
    String formattedEndTime = chFormatter.format(endTime);

    query.append("SELECT \n")
        .append("  SessionId,\n")
        .append("  countIf(StatusCode = 'Error') as error_count,\n")
        .append("  count() as total_interactions,\n")
        .append("  avg(nullIf(ApdexScore, 0)) as avg_apdex,\n")
        .append("  (error_count / total_interactions) as error_rate\n")
        .append("FROM otel.otel_traces\n")
        .append("WHERE\n")
        .append("  ProjectId = '")
        .append(escapeStringLiteral(projectId))
        .append("'\n")
        .append("  AND SpanName = '")
        .append(escapeStringLiteral(interactionName))
        .append("'\n")
        .append("  AND Timestamp >= '")
        .append(formattedStartTime)
        .append("'\n")
        .append("  AND Timestamp < '")
        .append(formattedEndTime)
        .append("'\n")
        .append("  AND SessionId != ''\n");

    appendDimensionFilters(query, segmentDimensions);

    query.append("GROUP BY SessionId\n")
        .append("HAVING\n")
        // Filter: Sessions where error_rate >= segment's own error_rate AND avg_apdex <= segment's own apdex
        // Both conditions must be true to select a session (stricter filtering)
        // Handle NULL apdex: if apdex is NULL (all errors), treat as 0.0 for comparison
        .append("  (error_rate >= ").append(errorRateThresholdDecimal).append(")\n")
        .append("  AND (ifNull(avg_apdex, 0.0) <= ").append(apdexThreshold).append(")\n")
        // Sort: By error_count DESC (most errors first), then by avg_apdex ASC (lowest apdex first)
        .append("ORDER BY\n")
        .append("  error_count DESC,\n")
        .append("  avg_apdex ASC\n")
        .append("LIMIT ")
        .append(effectiveLimit)
        .append("\n");

    return query.toString();
  }

  /**
   * Backward compatible overload: if metrics not provided, default to low thresholds.
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
        null,  // No metrics provided - will default to 0% error, 1.0 apdex
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

    // Format timestamps for ClickHouse: convert from ISO format to "YYYY-MM-DD HH:MM:SS"
    DateTimeFormatter chFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    String formattedStartTime = chFormatter.format(startTime);
    String formattedEndTime = chFormatter.format(endTime);

    query.append("SELECT uniqCombined64(nullIf(SessionId, '')) as total_sessions\n")
        .append("FROM otel.otel_traces\n")
        .append("WHERE\n")
        .append("  ProjectId = '")
        .append(escapeStringLiteral(projectId))
        .append("'\n")
        .append("  AND SpanName = '")
        .append(escapeStringLiteral(interactionName))
        .append("'\n")
        .append("  AND Timestamp >= '")
        .append(formattedStartTime)
        .append("'\n")
        .append("  AND Timestamp < '")
        .append(formattedEndTime)
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
