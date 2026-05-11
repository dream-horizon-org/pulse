package org.dreamhorizon.pulseserver.service.sessionrca;

import lombok.experimental.UtilityClass;

/**
 * Metric names and ClickHouse expressions for Session RCA over {@code otel.session_summary}.
 * Phase 1: quality_score (APDEX avg) and volume only.
 */
@UtilityClass
public class SessionRcaMetricsRegistry {

  public static final String VOLUME = "volume";
  public static final String VOLUME_WITH_APDEX = "volume_with_apdex";
  public static final String QUALITY_SCORE = "quality_score";
  public static final String QUALITY_SCORE_MEAN = "quality_score_mean";
  public static final String QUALITY_SCORE_STD = "quality_score_std";
  public static final String LOW_QUALITY_COUNT = "low_quality_count";
  public static final String Z_SCORE = "z_score";
  public static final String IMPACT = "impact";

  public static final String IMPACT_CRITICAL = "critical";
  public static final String IMPACT_NORMAL = "normal";

  static final String QUALITY_SCORE_EXPR =
      "if(sum(apdexCount) = 0, NULL, sum(apdexSum) / sum(apdexCount))";

  static final String QUALITY_SCORE_MEAN_EXPR =
      "avg(if(apdexCount = 0, NULL, apdexSum / apdexCount))";

  static final String QUALITY_SCORE_STD_EXPR =
      "stddevPop(if(apdexCount = 0, NULL, apdexSum / apdexCount))";

  static String lowQualityCountExpr(String thresholdParam) {
    return "countIf(notEmpty(toString(apdexCount)) AND apdexCount > 0"
        + " AND (apdexSum / apdexCount) < :" + thresholdParam + ")";
  }
}
