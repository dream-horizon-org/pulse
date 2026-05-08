package org.dreamhorizon.pulseserver.service.rootcause;

import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;

/**
 * Single place for hybrid RCA **merge + cap + mode** so {@link RootCauseService} and
 * {@link ScreenRcaService} stay aligned (issue: flat hierarchy segment merge PRD).
 *
 * <p><b>Not</b> {@link org.dreamhorizon.pulseserver.config.RootCauseConfig#isHybridDimensionOrderingEnabled()}:
 * that flag only affects dimension column order before segmentation. {@link RootCauseAnalysisMode#HYBRID}
 * means the merged output combined hierarchical 2D+ candidates with the flat 1D pass.
 */
@Slf4j
@UtilityClass
public class RcaHybridMergeOutcome {

  /**
   * Interaction RCA: default metric keys {@link RootCauseMetricsRegistry#VOLUME} /
   * {@code problematic_count}.
   */
  public static Result mergeForInteraction(
      String debugLogPrefix,
      Map<String, Object> baseline,
      List<RootCauseSegment> hierarchicalCandidates,
      List<RootCauseSegment> flatCandidates,
      List<String> dimensionOrder,
      int maxSegments) {
    List<RootCauseSegment> merged =
        RcaSegmentMergePolicy.mergeAndCap(
            baseline, hierarchicalCandidates, flatCandidates, dimensionOrder, maxSegments);
    RootCauseAnalysisMode mode = modeFromHierarchicalTier(hierarchicalCandidates);
    logOutcome(debugLogPrefix, hierarchicalCandidates, flatCandidates, merged, mode);
    return new Result(merged, mode);
  }

  /**
   * Screen RCA: driver metrics {@link ScreenRcaQueryBuilder#CLICK_VOLUME} /
   * {@link ScreenRcaQueryBuilder#BAD_FRUSTRATION}.
   */
  public static Result mergeForScreen(
      String debugLogPrefix,
      Map<String, Object> baseline,
      List<RootCauseSegment> hierarchicalCandidates,
      List<RootCauseSegment> flatCandidates,
      List<String> dimensionOrder,
      int maxSegments) {
    List<RootCauseSegment> merged =
        RcaSegmentMergePolicy.mergeAndCap(
            baseline,
            hierarchicalCandidates,
            flatCandidates,
            dimensionOrder,
            maxSegments,
            ScreenRcaQueryBuilder.CLICK_VOLUME,
            ScreenRcaQueryBuilder.BAD_FRUSTRATION);
    RootCauseAnalysisMode mode = modeFromHierarchicalTier(hierarchicalCandidates);
    logOutcome(debugLogPrefix, hierarchicalCandidates, flatCandidates, merged, mode);
    return new Result(merged, mode);
  }

  /**
   * When the hierarchical drill yielded no 2D+ materialized rows, the effective outcome is flat-only
   * ({@link RootCauseAnalysisMode#FLAT}). Otherwise the pipeline ran hybrid merge
   * ({@link RootCauseAnalysisMode#HYBRID}); signal gate may still drop segments afterward.
   */
  static RootCauseAnalysisMode modeFromHierarchicalTier(List<RootCauseSegment> hierarchicalCandidates) {
    return hierarchicalCandidates.isEmpty()
        ? RootCauseAnalysisMode.FLAT
        : RootCauseAnalysisMode.HYBRID;
  }

  private static void logOutcome(
      String debugLogPrefix,
      List<RootCauseSegment> hierarchicalCandidates,
      List<RootCauseSegment> flatCandidates,
      List<RootCauseSegment> merged,
      RootCauseAnalysisMode mode) {
    log.debug(
        "{} Merge result: hierarchicalCandidates={}, flatCandidates={}, merged={}, mode={}",
        debugLogPrefix,
        hierarchicalCandidates.size(),
        flatCandidates.size(),
        merged.size(),
        mode);
    if (log.isDebugEnabled()) {
      long twoPlus =
          merged.stream()
              .filter(
                  s -> s.getDimensions() != null && s.getDimensions().size() >= 2)
              .count();
      long oneD =
          merged.stream()
              .filter(
                  s -> s.getDimensions() != null && s.getDimensions().size() == 1)
              .count();
      log.debug(
          "{} Merged tier counts (2D+={}, 1D={}, unclassified={})",
          debugLogPrefix,
          twoPlus,
          oneD,
          merged.size() - twoPlus - oneD);
    }
  }

  public record Result(List<RootCauseSegment> segments, RootCauseAnalysisMode mode) {}
}
