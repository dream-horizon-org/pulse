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
 *   <li><b>Flat tier:</b> all remaining candidates (1D). Sorted by {@code problematic_count}
 *       DESC; tie-break by dimension order index ASC (earlier configured dimension wins).
 *   <li><b>Merge:</b> hierarchical tier first, then flat tier, truncated to {@code maxSegments}.
 * </ul>
 */
@UtilityClass
public class RcaSegmentMergePolicy {

  private static final String PROBLEMATIC_COUNT_KEY = "problematic_count";
  private static final String VOLUME_KEY = RootCauseMetricsRegistry.VOLUME;

  /**
   * Merges hierarchical and flat segment candidates into a single ordered list capped at
   * {@code maxSegments}. Hierarchical candidates with fewer than 2 dimensions are silently dropped
   * from the hierarchical tier (they do not fall through to the flat tier; pass them as
   * {@code flatCandidates} if 1D representation is desired).
   *
   * @param baseline               baseline metrics row (needs {@code volume} and
   *                               {@code problematic_count} for lift computation)
   * @param hierarchicalCandidates segments from hierarchical analysis; only 2D+ are kept
   * @param flatCandidates         segments from the flat 1D pass
   * @param dimensionOrder         configured dimension order for flat tier tie-breaks
   * @param maxSegments            upper bound on the returned list size
   * @return final ordered list, length ≤ maxSegments
   */
  public static List<RootCauseSegment> mergeAndCap(
      Map<String, Object> baseline,
      List<RootCauseSegment> hierarchicalCandidates,
      List<RootCauseSegment> flatCandidates,
      List<String> dimensionOrder,
      int maxSegments) {

    double baselineRate = computeProblematicRate(baseline);

    List<RootCauseSegment> hierarchicalTier = hierarchicalCandidates.stream()
        .filter(s -> s.getDimensions() != null && s.getDimensions().size() >= 2)
        .sorted((a, b) -> {
          int liftCmp = Double.compare(computeLift(b, baselineRate), computeLift(a, baselineRate));
          if (liftCmp != 0) {
            return liftCmp;
          }
          int dimA = a.getDimensions().size();
          int dimB = b.getDimensions().size();
          return Integer.compare(dimB, dimA);
        })
        .toList();

    List<RootCauseSegment> flatTier = flatCandidates.stream()
        .sorted((a, b) -> {
          int countCmp = Long.compare(getProblematicCount(b), getProblematicCount(a));
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
   * Lift = segment problematic rate − baseline problematic rate. Zero-volume-safe (rate is 0.0
   * when volume is 0).
   *
   * <p>Package-private for unit tests.
   */
  static double computeLift(RootCauseSegment segment, double baselineRate) {
    return computeProblematicRateFromMap(segment.getMetrics()) - baselineRate;
  }

  private static double computeProblematicRate(Map<String, Object> metricsOrBaseline) {
    return computeProblematicRateFromMap(metricsOrBaseline);
  }

  private static double computeProblematicRateFromMap(Map<String, Object> map) {
    if (map == null) {
      return 0.0;
    }
    long volume = NumberCoercionUtils.toLong(map.get(VOLUME_KEY));
    if (volume == 0) {
      return 0.0;
    }
    long problematic = NumberCoercionUtils.toLong(map.get(PROBLEMATIC_COUNT_KEY));
    return (double) problematic / volume;
  }

  private static long getProblematicCount(RootCauseSegment segment) {
    if (segment.getMetrics() == null) {
      return 0L;
    }
    return NumberCoercionUtils.toLong(segment.getMetrics().get(PROBLEMATIC_COUNT_KEY));
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
