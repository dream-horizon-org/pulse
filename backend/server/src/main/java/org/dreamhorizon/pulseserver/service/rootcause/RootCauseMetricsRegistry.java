package org.dreamhorizon.pulseserver.service.rootcause;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;

/**
 * Registry of metrics used for Root Cause Analysis on interaction traces.
 * All expressions are compatible with otel_traces (PulseType='interaction').
 */
@UtilityClass
public class RootCauseMetricsRegistry {

  public static final String VOLUME = "volume";
  public static final String APDEX = "apdex";
  public static final String ERROR_RATE = "error_rate";
  public static final String POOR_USER_PCT = "poor_user_pct";
  public static final String DURATION_P50 = "duration_p50";
  public static final String DURATION_P95 = "duration_p95";
  public static final String CRASH_RATE = "crash_rate";
  public static final String ANR_RATE = "anr_rate";
  public static final String FROZEN_FRAME_RATE = "frozen_frame_rate";
  public static final String SLOW_FRAME_RATE = "slow_frame_rate";

  /** Metric name -> ClickHouse SELECT expression (without alias). */
  public static Map<String, String> getMetricExpressions() {
    Map<String, String> m = new LinkedHashMap<>();
    m.put(VOLUME, "count()");
    m.put(APDEX, ClickhouseConstants.CH_APDEX_SELECT_CLAUSE);
    m.put(ERROR_RATE, ClickhouseConstants.ERROR_RATE);
    m.put(POOR_USER_PCT, ClickhouseConstants.POOR_USER_RATE);
    m.put(DURATION_P50, ClickhouseConstants.CH_DURATION_P50_SELECT_CLAUSE);
    m.put(DURATION_P95, ClickhouseConstants.CH_DURATION_P95_SELECT_CLAUSE);
    m.put(CRASH_RATE, ClickhouseConstants.CRASH_RATE);
    m.put(ANR_RATE, ClickhouseConstants.ANR_RATE);
    m.put(FROZEN_FRAME_RATE, ClickhouseConstants.FROZEN_FRAME_RATE);
    m.put(SLOW_FRAME_RATE, ClickhouseConstants.SLOW_FRAME_RATE);
    return m;
  }

  /** Expression for problematic count (error OR poor); used for segment selection. */
  public static String getProblematicCountExpression() {
    return ClickhouseConstants.PROBLEMATIC_COUNT;
  }
}
