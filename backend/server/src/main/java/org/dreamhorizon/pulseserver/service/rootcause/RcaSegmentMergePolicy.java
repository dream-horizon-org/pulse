package org.dreamhorizon.pulseserver.service.rootcause;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;

/**
 * Pure merge/sort/cap logic for RCA hybrid segment pipeline. No ClickHouse, no I/O — only ordering
 * math on already-materialized {@link RootCauseSegment} lists.
 *
 * <p>Tier rules (per PRD):
 * <ul>
 *   <li><b>Hierarchical tier:</b> segments whose {@code dimensions} map has ≥ 2 entries.
 *       Sorted by lift (segment problematic rate − baseline problematic rate) DESC; tie-break by
 *       dimension count DESC (more specific intersection wins).
 *   <li><b>Flat tier:</b> all remaining candidates (1D). Sorted by {@code countKey} DESC;
 *       tie-break by dimension order index ASC (earlier configured dimension wins).
 *   <li><b>Merge:</b> hierarchical tier first, then flat tier, truncated to {@code maxSegments}.
 * </ul>
 */
@UtilityClass
public class RcaSegmentMergePolicy {

  static final String DEFAULT_VOLUME_KEY = RootCauseMetricsRegistry.VOLUME;
  static final String DEFAULT_COUNT_KEY = "problematic_count";

  /**
   * Merges hierarchical and flat segment candidates using interaction RCA metric keys
   * ({@code volume} / {@code problematic_count}).
   *
   * @see #mergeAndCap(Map, List, List, List, int, String, String)
   */
  public static List<RootCauseSegment> mergeAndCap(
      Map<String, Object> baseline,
      List<RootCauseSegment> hierarchicalCandidates,
      List<RootCauseSegment> flatCandidates,
      List<String> dimensionOrder,
      int maxSegments) {
    return mergeAndCap(baseline, hierarchicalCandidates, flatCandidates,
        dimensionOrder, maxSegments, DEFAULT_VOLUME_KEY, DEFAULT_COUNT_KEY);
  }

  /**
   * Merges hierarchical and flat segment candidates into a single ordered list capped at
   * {@code maxSegments}. Hierarchical candidates with fewer than 2 dimensions are silently dropped
   * from the hierarchical tier (they do not fall through to the flat tier; pass them as
   * {@code flatCandidates} if 1D representation is desired).
   *
   * @param baseline               baseline metrics row (needs {@code volumeKey} and
   *                               {@code countKey} for lift computation)
   * @param hierarchicalCandidates segments from hierarchical analysis; only 2D+ are kept
   * @param flatCandidates         segments from the flat 1D pass
   * @param dimensionOrder         configured dimension order for flat tier tie-breaks
   * @param maxSegments            upper bound on the returned list size
   * @param volumeKey              metrics map key for the volume field (e.g. "volume" or "click_volume")
   * @param countKey               metrics map key for the problematic count (e.g. "problematic_count"
   *                               or "bad_frustration")
   * @return final ordered list, length ≤ maxSegments
   */
  public static List<RootCauseSegment> mergeAndCap(
      Map<String, Object> baseline,
      List<RootCauseSegment> hierarchicalCandidates,
      List<RootCauseSegment> flatCandidates,
      List<String> dimensionOrder,
      int maxSegments,
      String volumeKey,
      String countKey) {

    double baselineRate = computeRateFromMap(baseline, volumeKey, countKey);

    List<RootCauseSegment> hierarchicalTier = hierarchicalCandidates.stream()
        .filter(s -> s.getDimensions() != null && s.getDimensions().size() >= 2)
        .sorted((a, b) -> {
          int liftCmp = Double.compare(
              computeLift(b, baselineRate, volumeKey, countKey),
              computeLift(a, baselineRate, volumeKey, countKey));
          if (liftCmp != 0) {
            return liftCmp;
          }
          return Integer.compare(b.getDimensions().size(), a.getDimensions().size());
        })
        .toList();

    List<RootCauseSegment> flatTier = flatCandidates.stream()
        .sorted((a, b) -> {
          int countCmp = Long.compare(
              getCount(b, countKey), getCount(a, countKey));
          if (countCmp != 0) {
            return countCmp;
          }
          return Integer.compare(
              getDimensionOrderIndex(a, dimensionOrder),
              getDimensionOrderIndex(b, dimensionOrder));
        })
        .toList();

    return Stream.concat(hierarchicalTier.stream(), flatTier.stream())
        .limit(Math.max(0, maxSegments))
        .toList();
  }

  /**
   * Lift = segment problematic rate − baseline problematic rate using default interaction keys.
   * Zero-volume-safe (rate is 0.0 when volume is 0).
   *
   * <p>Package-private for unit tests.
   */
  static double computeLift(RootCauseSegment segment, double baselineRate) {
    return computeLift(segment, baselineRate, DEFAULT_VOLUME_KEY, DEFAULT_COUNT_KEY);
  }

  /**
   * Lift = segment problematic rate − baseline problematic rate using the given metric keys.
   *
   * <p>Package-private for unit tests.
   */
  static double computeLift(
      RootCauseSegment segment, double baselineRate, String volumeKey, String countKey) {
    return computeRateFromMap(segment.getMetrics(), volumeKey, countKey) - baselineRate;
  }

  private static double computeRateFromMap(Map<String, Object> map, String volumeKey, String countKey) {
    if (map == null) {
      return 0.0;
    }
    long volume = NumberCoercionUtils.toLong(map.get(volumeKey));
    if (volume == 0) {
      return 0.0;
    }
    long count = NumberCoercionUtils.toLong(map.get(countKey));
    return (double) count / volume;
  }

  private static long getCount(RootCauseSegment segment, String countKey) {
    if (segment.getMetrics() == null) {
      return 0L;
    }
    return NumberCoercionUtils.toLong(segment.getMetrics().get(countKey));
  }

  private static int getDimensionOrderIndex(
      RootCauseSegment segment, List<String> dimensionOrder) {
    if (segment.getDimensions() == null || segment.getDimensions().isEmpty()) {
      return Integer.MAX_VALUE;
    }
    String dim = segment.getDimensions().keySet().iterator().next();
    int idx = dimensionOrder.indexOf(dim);
    return idx >= 0 ? idx : Integer.MAX_VALUE;
  }
}
