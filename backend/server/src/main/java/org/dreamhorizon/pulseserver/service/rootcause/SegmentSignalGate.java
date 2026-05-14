package org.dreamhorizon.pulseserver.service.rootcause;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;

/**
 * Combined signal eligibility for {@link RootCauseSegment} entries.
 *
 * <p><b>Interaction RCA</b> (via {@link #filterInteractionSegmentsRatesAboveBaseline}): keeps segments
 * where raw {@code error_rate + poor_user_pct} from the segment slice strictly exceeds the same sum on
 * the interaction baseline row (both from ClickHouse aggregates).
 *
 * <p><b>Screen RCA:</b> after merge, segments must have raw {@code bad_frustration} strictly greater
 * than baseline ({@link #filterSegmentsRawMetricAboveBaseline} with
 * {@link org.dreamhorizon.pulseserver.service.rootcause.ScreenRcaQueryBuilder#BAD_FRUSTRATION}).
 *
 * <p><b>Threshold / delta mode (utilities only):</b> {@code S = sum of |Δm|} retained for callers
 * and tests that explicitly pass metric keys plus a numeric threshold ({@link #filter}). Not used
 * by interaction or screen RCA segment persistence.
 */
@UtilityClass
public class SegmentSignalGate {

  /** Driver metrics for interaction RCA, the canonical PRD pair. */
  public static final String[] DEFAULT_METRIC_KEYS = {
      RootCauseMetricsRegistry.ERROR_RATE,
      RootCauseMetricsRegistry.POOR_USER_PCT
  };

  public static double computeSignal(RootCauseSegment segment) {
    return computeSignal(segment, DEFAULT_METRIC_KEYS);
  }

  public static double computeSignal(RootCauseSegment segment, String... metricKeys) {
    if (segment == null) {
      return 0.0;
    }
    return computeSignal(segment.getDeltas(), metricKeys);
  }

  public static double computeSignal(Map<String, Double> deltas) {
    return computeSignal(deltas, DEFAULT_METRIC_KEYS);
  }

  public static double computeSignal(Map<String, Double> deltas, String... metricKeys) {
    if (deltas == null || metricKeys == null || metricKeys.length == 0) {
      return 0.0;
    }
    double sum = 0.0;
    for (String key : metricKeys) {
      sum += absOrZero(deltas.get(key));
    }
    return sum;
  }

  public static boolean isEligible(RootCauseSegment segment, double threshold) {
    return isEligible(segment, threshold, DEFAULT_METRIC_KEYS);
  }

  public static boolean isEligible(
      RootCauseSegment segment, double threshold, String... metricKeys) {
    return computeSignal(segment, metricKeys) >= threshold;
  }

  /**
   * Returns segments whose combined signal {@code S >= threshold}, preserving relative order.
   * Returns an empty list when the input is null.
   */
  public static List<RootCauseSegment> filter(List<RootCauseSegment> segments, double threshold) {
    return filter(segments, threshold, DEFAULT_METRIC_KEYS);
  }

  public static List<RootCauseSegment> filter(
      List<RootCauseSegment> segments, double threshold, String... metricKeys) {
    if (segments == null || segments.isEmpty()) {
      return Collections.emptyList();
    }
    List<RootCauseSegment> kept = new ArrayList<>(segments.size());
    for (RootCauseSegment s : segments) {
      if (isEligible(s, threshold, metricKeys)) {
        kept.add(s);
      }
    }
    return kept;
  }

  private static double absOrZero(Double d) {
    return d == null ? 0.0 : Math.abs(d);
  }

  /**
   * Raw {@code error_rate + poor_user_pct} from an RCA metrics row (baseline or segment {@code metrics} map).
   * Missing keys coerce like ClickHouse NULL-as-number → 0.
   */
  public static double sumErrorRatePlusPoorUserPct(Map<String, Object> metrics) {
    if (metrics == null) {
      return 0d;
    }
    return NumberCoercionUtils.toDouble(metrics.get(RootCauseMetricsRegistry.ERROR_RATE))
        + NumberCoercionUtils.toDouble(metrics.get(RootCauseMetricsRegistry.POOR_USER_PCT));
  }

  /** Raw scalar from an RCA metrics map; missing/null → 0. */
  public static double rawMetricValue(Map<String, Object> metrics, String metricKey) {
    return NumberCoercionUtils.toDouble(metrics == null ? null : metrics.get(metricKey));
  }

  /**
   * True when segment {@code metricKey} strictly exceeds baseline (count or rate slice vs cohort).
   */
  public static boolean isEligibleRawMetricAboveBaseline(
      RootCauseSegment segment, Map<String, Object> baselineMetrics, String metricKey) {
    if (segment == null || baselineMetrics == null || metricKey == null || metricKey.isEmpty()) {
      return false;
    }
    return rawMetricValue(segment.getMetrics(), metricKey)
        > rawMetricValue(baselineMetrics, metricKey);
  }

  /**
   * Filter segments whose {@code metricKey} raw value exceeds baseline; preserves order. When {@code
   * baselineMetrics} is null, returns a copy of the input list (no-op).
   */
  public static List<RootCauseSegment> filterSegmentsRawMetricAboveBaseline(
      List<RootCauseSegment> segments, Map<String, Object> baselineMetrics, String metricKey) {
    if (segments == null || segments.isEmpty()) {
      return Collections.emptyList();
    }
    if (baselineMetrics == null) {
      return new ArrayList<>(segments);
    }
    List<RootCauseSegment> kept = new ArrayList<>(segments.size());
    for (RootCauseSegment s : segments) {
      if (isEligibleRawMetricAboveBaseline(s, baselineMetrics, metricKey)) {
        kept.add(s);
      }
    }
    return kept;
  }

  /**
   * Interaction RCA segment gate: segment slice is worse than baseline on the summed poor/error rates.
   */
  public static boolean isEligibleInteractionRatesAboveBaseline(
      RootCauseSegment segment, Map<String, Object> baselineMetrics) {
    if (segment == null || baselineMetrics == null) {
      return false;
    }
    return sumErrorRatePlusPoorUserPct(segment.getMetrics())
        > sumErrorRatePlusPoorUserPct(baselineMetrics);
  }

  /**
   * Filters interaction RCA segments whose {@code error_rate + poor_user_pct} sum is strictly greater
   * than the baseline sum; preserves input order.
   */
  public static List<RootCauseSegment> filterInteractionSegmentsRatesAboveBaseline(
      List<RootCauseSegment> segments, Map<String, Object> baselineMetrics) {
    if (segments == null || segments.isEmpty()) {
      return Collections.emptyList();
    }
    if (baselineMetrics == null) {
      return new ArrayList<>(segments);
    }
    List<RootCauseSegment> kept = new ArrayList<>(segments.size());
    for (RootCauseSegment s : segments) {
      if (isEligibleInteractionRatesAboveBaseline(s, baselineMetrics)) {
        kept.add(s);
      }
    }
    return kept;
  }
}
