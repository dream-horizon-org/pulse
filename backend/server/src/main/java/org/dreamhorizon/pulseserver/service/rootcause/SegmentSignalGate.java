package org.dreamhorizon.pulseserver.service.rootcause;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
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
 * <p><b>Screen RCA:</b> after merge, segments must have {@code bad_frustration / click_volume}
 * strictly greater than the same ratio on the baseline row ({@link
 * #filterSegmentsRateAboveBaseline} with {@link
 * org.dreamhorizon.pulseserver.service.rootcause.ScreenRcaQueryBuilder#BAD_FRUSTRATION} and {@link
 * org.dreamhorizon.pulseserver.service.rootcause.ScreenRcaQueryBuilder#CLICK_VOLUME}), aligned with
 * interaction RCA’s rate-style gate.
 */
@UtilityClass
public class SegmentSignalGate {

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
   * {@code numeratorKey / denominatorKey} for a metrics row. When {@code denominatorKey} is
   * non-positive and numerator is positive, returns positive infinity (non-zero bad signal with no
   * volume). Returns empty when the ratio is undefined (e.g. both zero).
   */
  public static OptionalDouble metricRate(
      Map<String, Object> metrics, String numeratorKey, String denominatorKey) {
    if (metrics == null
        || numeratorKey == null
        || numeratorKey.isEmpty()
        || denominatorKey == null
        || denominatorKey.isEmpty()) {
      return OptionalDouble.empty();
    }
    double den = rawMetricValue(metrics, denominatorKey);
    double num = rawMetricValue(metrics, numeratorKey);
    if (den <= 0.0d) {
      if (num > 0.0d) {
        return OptionalDouble.of(Double.POSITIVE_INFINITY);
      }
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(num / den);
  }

  /**
   * Screen RCA gate: segment bad-frustration rate strictly exceeds baseline rate on the same keys.
   */
  public static boolean isEligibleRateAboveBaseline(
      RootCauseSegment segment,
      Map<String, Object> baselineMetrics,
      String numeratorKey,
      String denominatorKey) {
    if (segment == null || baselineMetrics == null) {
      return false;
    }
    OptionalDouble seg = metricRate(segment.getMetrics(), numeratorKey, denominatorKey);
    OptionalDouble base = metricRate(baselineMetrics, numeratorKey, denominatorKey);
    if (seg.isEmpty() || base.isEmpty()) {
      return false;
    }
    return seg.getAsDouble() > base.getAsDouble();
  }

  /**
   * Filters segments whose {@code numeratorKey / denominatorKey} is strictly greater than baseline;
   * preserves order. When {@code baselineMetrics} is null, returns a copy of the input (no-op).
   */
  public static List<RootCauseSegment> filterSegmentsRateAboveBaseline(
      List<RootCauseSegment> segments,
      Map<String, Object> baselineMetrics,
      String numeratorKey,
      String denominatorKey) {
    if (segments == null || segments.isEmpty()) {
      return Collections.emptyList();
    }
    if (baselineMetrics == null) {
      return new ArrayList<>(segments);
    }
    List<RootCauseSegment> kept = new ArrayList<>(segments.size());
    for (RootCauseSegment s : segments) {
      if (isEligibleRateAboveBaseline(s, baselineMetrics, numeratorKey, denominatorKey)) {
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
