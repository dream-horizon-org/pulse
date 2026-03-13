package org.dreamhorizon.pulseserver.service.rootcause;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;

/**
 * Registry of root-cause metrics for interaction traces.
 * Keys and CH expressions used by the segment selection algorithm and query builder.
 */
public final class RootCauseMetricsRegistry {

  private RootCauseMetricsRegistry() {}

  /** Metric keys in display order (volume first, then quality metrics). */
  public static final List<String> METRIC_KEYS = List.of(
      "volume",
      "apdex",
      "error_rate",
      "poor_user_pct",
      "duration_p50",
      "duration_p95",
      "crash_rate",
      "anr_rate",
      "frozen_frame_rate",
      "slow_frame_rate"
  );

  /** ClickHouse SELECT expression per metric key (for use in SELECT clause). */
  public static final Map<String, String> METRIC_EXPRESSIONS = new LinkedHashMap<>();

  static {
    METRIC_EXPRESSIONS.put("volume", "count()");
    METRIC_EXPRESSIONS.put("apdex", ClickhouseConstants.CH_APDEX_SELECT_CLAUSE);
    METRIC_EXPRESSIONS.put("error_rate", ClickhouseConstants.ERROR_RATE);
    METRIC_EXPRESSIONS.put("poor_user_pct", ClickhouseConstants.POOR_USER_RATE);
    METRIC_EXPRESSIONS.put("duration_p50", ClickhouseConstants.CH_DURATION_P50_SELECT_CLAUSE);
    METRIC_EXPRESSIONS.put("duration_p95", ClickhouseConstants.CH_DURATION_P95_SELECT_CLAUSE);
    METRIC_EXPRESSIONS.put("crash_rate", ClickhouseConstants.CRASH_RATE);
    METRIC_EXPRESSIONS.put("anr_rate", ClickhouseConstants.ANR_RATE);
    METRIC_EXPRESSIONS.put("frozen_frame_rate", ClickhouseConstants.FROZEN_FRAME_RATE);
    METRIC_EXPRESSIONS.put("slow_frame_rate", ClickhouseConstants.SLOW_FRAME_RATE);
  }

  /** Dimensions for segment breakdown (key -> ClickHouse column name). */
  public static final List<String> DIMENSION_KEYS = List.of(
      "Platform",
      "OsVersion",
      "AppVersion",
      "DeviceModel",
      "NetworkProvider",
      "GeoState"
  );
}
