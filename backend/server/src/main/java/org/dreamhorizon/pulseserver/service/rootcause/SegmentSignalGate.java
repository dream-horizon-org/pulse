package org.dreamhorizon.pulseserver.service.rootcause;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;

/**
 * Combined signal eligibility for {@link RootCauseSegment} entries.
 *
 * <p>S = sum of |Δm| over a configured set of driver metrics. Default driver set is
 * {@code (error_rate, poor_user_pct)} for interaction RCA. Screen RCA passes its own
 * driver keys (e.g. {@code bad_frustration}). A missing delta for any metric is
 * treated as 0. A segment is eligible when {@code S >= threshold}.
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
}
