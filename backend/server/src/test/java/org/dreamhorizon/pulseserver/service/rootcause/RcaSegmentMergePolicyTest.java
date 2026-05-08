package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RcaSegmentMergePolicyTest {

  private static final List<String> DIM_ORDER =
      List.of("Platform", "AppVersion", "GeoState", "DeviceModel", "NetworkProvider");

  // ── helpers ──────────────────────────────────────────────────────────────

  private static RootCauseSegment seg(
      String label, Map<String, String> dims, long volume, long problematic) {
    return RootCauseSegment.builder()
        .label(label)
        .dimensions(dims)
        .metrics(Map.of("volume", volume, "problematic_count", problematic))
        .build();
  }

  private static Map<String, Object> baseline(long volume, long problematic) {
    return Map.of("volume", volume, "problematic_count", problematic);
  }

  // ── empty-tier cases ─────────────────────────────────────────────────────

  @Nested
  class EmptyTiers {

    @Test
    void shouldReturnEmptyWhenBothTiersEmpty() {
      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(), List.of(), DIM_ORDER, 5);

      assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnFlatOnlyWhenHierarchicalCandidatesEmpty() {
      RootCauseSegment f1 = seg("Platform: Android", Map.of("Platform", "Android"), 500, 80);
      RootCauseSegment f2 = seg("AppVersion: 1.0", Map.of("AppVersion", "1.0"), 300, 50);

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(), List.of(f1, f2), DIM_ORDER, 5);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getLabel()).isEqualTo("Platform: Android");
    }

    @Test
    void shouldReturnHierarchicalOnlyWhenFlatCandidatesEmpty() {
      RootCauseSegment h = seg("Android + 1.0",
          Map.of("Platform", "Android", "AppVersion", "1.0"), 400, 100);

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(h), List.of(), DIM_ORDER, 5);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getLabel()).isEqualTo("Android + 1.0");
    }
  }

  // ── tier ordering ─────────────────────────────────────────────────────────

  @Nested
  class TierOrdering {

    @Test
    void shouldPlaceAllHierarchicalBeforeFlat() {
      RootCauseSegment h1 = seg("A+B", Map.of("Platform", "A", "AppVersion", "B"), 300, 60);
      RootCauseSegment h2 = seg("C+D", Map.of("GeoState", "C", "DeviceModel", "D"), 200, 40);
      // flat has huge problematic_count — but must still appear after hierarchical
      RootCauseSegment f1 = seg("Platform: Android", Map.of("Platform", "Android"), 800, 500);

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(h1, h2), List.of(f1), DIM_ORDER, 5);

      assertThat(result).hasSize(3);
      assertThat(result.subList(0, 2))
          .extracting(RootCauseSegment::getLabel)
          .containsExactlyInAnyOrder("A+B", "C+D");
      assertThat(result.get(2).getLabel()).isEqualTo("Platform: Android");
    }

    @Test
    void shouldExclude1DHierarchicalCandidates() {
      // 1D segments passed as hierarchical candidates must be dropped, not promoted to flat
      RootCauseSegment oneDim = seg("Platform: Android", Map.of("Platform", "Android"), 600, 200);
      RootCauseSegment twoDim = seg("Android+1.0",
          Map.of("Platform", "Android", "AppVersion", "1.0"), 300, 100);

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(oneDim, twoDim), List.of(), DIM_ORDER, 5);

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getLabel()).isEqualTo("Android+1.0");
    }
  }

  // ── hierarchical tier sorting ─────────────────────────────────────────────

  @Nested
  class HierarchicalTierSorting {

    @Test
    void shouldSortByLiftDescending() {
      // baseline rate = 100/1000 = 0.1
      // seg1: 60/400 = 0.15, lift = 0.05
      // seg2: 90/300 = 0.30, lift = 0.20  ← higher lift
      RootCauseSegment seg1 = seg("A+B", Map.of("Platform", "A", "AppVersion", "B"), 400, 60);
      RootCauseSegment seg2 = seg("C+D", Map.of("GeoState", "C", "DeviceModel", "D"), 300, 90);

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(seg1, seg2), List.of(), DIM_ORDER, 5);

      assertThat(result.get(0).getLabel()).isEqualTo("C+D");
      assertThat(result.get(1).getLabel()).isEqualTo("A+B");
    }

    @Test
    void shouldBreakLiftTieByMoreDimensionsFirst() {
      // Same metrics → same lift; 3D segment wins over 2D
      Map<String, Object> metrics = Map.of("volume", 400L, "problematic_count", 80L);
      RootCauseSegment twoDim = RootCauseSegment.builder()
          .label("A+B")
          .dimensions(Map.of("Platform", "A", "AppVersion", "B"))
          .metrics(metrics)
          .build();
      RootCauseSegment threeDim = RootCauseSegment.builder()
          .label("A+B+C")
          .dimensions(Map.of("Platform", "A", "AppVersion", "B", "GeoState", "C"))
          .metrics(metrics)
          .build();

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(twoDim, threeDim), List.of(), DIM_ORDER, 5);

      assertThat(result.get(0).getLabel()).isEqualTo("A+B+C");
      assertThat(result.get(1).getLabel()).isEqualTo("A+B");
    }
  }

  // ── flat tier sorting ─────────────────────────────────────────────────────

  @Nested
  class FlatTierSorting {

    @Test
    void shouldSortByProblematicCountDescending() {
      RootCauseSegment small = seg("AppVersion: 1.0", Map.of("AppVersion", "1.0"), 500, 30);
      RootCauseSegment large = seg("Platform: Android", Map.of("Platform", "Android"), 500, 100);

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(), List.of(small, large), DIM_ORDER, 5);

      assertThat(result.get(0).getLabel()).isEqualTo("Platform: Android");
      assertThat(result.get(1).getLabel()).isEqualTo("AppVersion: 1.0");
    }

    @Test
    void shouldBreakFlatTieByDimensionOrderIndex() {
      // Same problematic_count; Platform is index 0 in DIM_ORDER, AppVersion is index 1
      RootCauseSegment appVersion =
          seg("AppVersion: 1.0", Map.of("AppVersion", "1.0"), 500, 50);
      RootCauseSegment platform =
          seg("Platform: Android", Map.of("Platform", "Android"), 500, 50);

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(), List.of(appVersion, platform), DIM_ORDER, 5);

      assertThat(result.get(0).getLabel()).isEqualTo("Platform: Android");
      assertThat(result.get(1).getLabel()).isEqualTo("AppVersion: 1.0");
    }
  }

  // ── cap behaviour ─────────────────────────────────────────────────────────

  @Nested
  class CapBehaviour {

    @Test
    void shouldCapToMaxSegments() {
      List<RootCauseSegment> flat = List.of(
          seg("Platform: Android", Map.of("Platform", "Android"), 500, 100),
          seg("AppVersion: 1.0", Map.of("AppVersion", "1.0"), 400, 80),
          seg("GeoState: CA", Map.of("GeoState", "CA"), 300, 60));

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(), flat, DIM_ORDER, 2);

      assertThat(result).hasSize(2);
    }

    @Test
    void shouldCutOnlyFlatTailWhenHierarchicalFillsMostSlots() {
      // hier1 lift = 100/200 − 0.1 = 0.4  (higher)
      // hier2 lift = 80/400 − 0.1 = 0.1
      RootCauseSegment hier1 =
          seg("A+B", Map.of("Platform", "A", "AppVersion", "B"), 200, 100);
      RootCauseSegment hier2 =
          seg("C+D", Map.of("GeoState", "C", "DeviceModel", "D"), 400, 80);
      RootCauseSegment flat1 =
          seg("Platform: Android", Map.of("Platform", "Android"), 600, 200);
      RootCauseSegment flat2 =
          seg("AppVersion: 1.0", Map.of("AppVersion", "1.0"), 400, 150);

      // cap=3: hier1, hier2, flat1 (flat2 dropped)
      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(hier1, hier2), List.of(flat1, flat2), DIM_ORDER, 3);

      assertThat(result).hasSize(3);
      assertThat(result.get(0).getLabel()).isEqualTo("A+B");
      assertThat(result.get(1).getLabel()).isEqualTo("C+D");
      assertThat(result.get(2).getLabel()).isEqualTo("Platform: Android");
    }

    @Test
    void shouldCapHierarchyItselfWhenHierarchyExceedsMaxSegments() {
      // baseline rate = 100/1000 = 0.1
      // seg1 lift = 90/300 − 0.1 = 0.20  (highest)
      // seg2 lift = 80/400 − 0.1 = 0.10
      // seg3 lift = 60/500 − 0.1 = 0.02  (lowest)
      RootCauseSegment seg1 =
          seg("A+B", Map.of("Platform", "A", "AppVersion", "B"), 300, 90);
      RootCauseSegment seg2 =
          seg("C+D", Map.of("GeoState", "C", "DeviceModel", "D"), 400, 80);
      RootCauseSegment seg3 =
          seg("E+F", Map.of("NetworkProvider", "E", "DeviceModel", "F"), 500, 60);
      RootCauseSegment flat1 =
          seg("Platform: X", Map.of("Platform", "X"), 900, 150);

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(seg1, seg2, seg3), List.of(flat1), DIM_ORDER, 2);

      assertThat(result).hasSize(2);
      assertThat(result.get(0).getLabel()).isEqualTo("A+B");
      assertThat(result.get(1).getLabel()).isEqualTo("C+D");
    }
  }

  // ── zero-volume safety ────────────────────────────────────────────────────

  @Nested
  class ZeroVolumeSafety {

    @Test
    void shouldHandleZeroBaselineVolumeWithoutException() {
      RootCauseSegment h = seg("A+B", Map.of("Platform", "A", "AppVersion", "B"), 400, 80);

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(0, 0), List.of(h), List.of(), DIM_ORDER, 5);

      assertThat(result).hasSize(1);
    }

    @Test
    void shouldHandleZeroSegmentVolumeWithoutException() {
      RootCauseSegment h = seg("A+B", Map.of("Platform", "A", "AppVersion", "B"), 0, 0);

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(h), List.of(), DIM_ORDER, 5);

      assertThat(result).hasSize(1);
    }

    @Test
    void shouldHandleNullSegmentMetricsWithoutException() {
      RootCauseSegment noMetrics = RootCauseSegment.builder()
          .label("A+B")
          .dimensions(Map.of("Platform", "A", "AppVersion", "B"))
          .metrics(null)
          .build();

      List<RootCauseSegment> result = RcaSegmentMergePolicy.mergeAndCap(
          baseline(1000, 100), List.of(noMetrics), List.of(), DIM_ORDER, 5);

      assertThat(result).hasSize(1);
    }
  }

  // ── computeLift ───────────────────────────────────────────────────────────

  @Nested
  class ComputeLift {

    @Test
    void shouldReturnPositiveLiftWhenSegmentRateExceedsBaseline() {
      // segment rate = 200/500 = 0.4; baseline rate = 0.1
      RootCauseSegment s =
          seg("A+B", Map.of("Platform", "A", "AppVersion", "B"), 500, 200);

      assertThat(RcaSegmentMergePolicy.computeLift(s, 0.1)).isCloseTo(0.3, offset(0.001));
    }

    @Test
    void shouldReturnNegativeLiftWhenSegmentRateBelowBaseline() {
      // segment rate = 10/500 = 0.02; baseline rate = 0.1
      RootCauseSegment s =
          seg("A+B", Map.of("Platform", "A", "AppVersion", "B"), 500, 10);

      assertThat(RcaSegmentMergePolicy.computeLift(s, 0.1)).isCloseTo(-0.08, offset(0.001));
    }

    @Test
    void shouldReturnZeroMinusBaselineRateWhenSegmentVolumeIsZero() {
      RootCauseSegment s =
          seg("A+B", Map.of("Platform", "A", "AppVersion", "B"), 0, 0);

      assertThat(RcaSegmentMergePolicy.computeLift(s, 0.1)).isCloseTo(-0.1, offset(0.001));
    }

    @Test
    void shouldReturnZeroWhenNullMetrics() {
      RootCauseSegment s = RootCauseSegment.builder()
          .label("A+B")
          .dimensions(Map.of("Platform", "A", "AppVersion", "B"))
          .metrics(null)
          .build();

      assertThat(RcaSegmentMergePolicy.computeLift(s, 0.0)).isEqualTo(0.0);
    }
  }
}
