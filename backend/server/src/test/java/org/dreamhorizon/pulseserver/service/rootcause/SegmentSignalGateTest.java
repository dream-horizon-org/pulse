package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.dreamhorizon.pulseserver.service.rootcause.RootCauseMetricsRegistry.ERROR_RATE;
import static org.dreamhorizon.pulseserver.service.rootcause.RootCauseMetricsRegistry.POOR_USER_PCT;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SegmentSignalGateTest {

  /**
   * Interaction RCA: gate on raw {@code error_rate + poor_user_pct} vs baseline (not delta sum).
   */
  @Nested
  class InteractionRatesAboveBaseline {

    private static Map<String, Object> metricsRow(Double err, Double poor) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put(ERROR_RATE, err);
      m.put(POOR_USER_PCT, poor);
      return m;
    }

    private static RootCauseSegment segmentWithRates(String label, Double err, Double poor) {
      return RootCauseSegment.builder().label(label).metrics(metricsRow(err, poor)).build();
    }

    @Test
    void shouldSumRawRatesFromMetricsRow() {
      assertThat(SegmentSignalGate.sumErrorRatePlusPoorUserPct(metricsRow(3.0, 7.0))).isEqualTo(10.0);
    }

    @Test
    void shouldTreatNullMetricsAsZeroSum() {
      assertThat(SegmentSignalGate.sumErrorRatePlusPoorUserPct(null)).isEqualTo(0.0);
    }

    @Test
    void shouldKeepWhenSegmentSumStrictlyAboveBaseline() {
      Map<String, Object> baseline = metricsRow(5.0, 5.0);
      RootCauseSegment s = segmentWithRates("hot", 6.0, 5.0);
      assertThat(SegmentSignalGate.isEligibleInteractionRatesAboveBaseline(s, baseline)).isTrue();
    }

    @Test
    void shouldDropWhenSegmentSumEqualsBaseline() {
      Map<String, Object> baseline = metricsRow(5.0, 5.0);
      RootCauseSegment s = segmentWithRates("same", 3.0, 7.0);
      assertThat(SegmentSignalGate.isEligibleInteractionRatesAboveBaseline(s, baseline)).isFalse();
    }

    @Test
    void shouldDropWhenBelowBaseline() {
      Map<String, Object> baseline = metricsRow(10.0, 10.0);
      RootCauseSegment s = segmentWithRates("cold", 5.0, 5.0);
      assertThat(SegmentSignalGate.isEligibleInteractionRatesAboveBaseline(s, baseline)).isFalse();
    }

    @Test
    void shouldRejectWhenBaselineNull() {
      assertThat(
              SegmentSignalGate.isEligibleInteractionRatesAboveBaseline(
                  segmentWithRates("x", 1.0, 1.0), null))
          .isFalse();
    }

    @Test
    void shouldPassThroughAllWhenBaselineMapNullInFilter() {
      RootCauseSegment a = segmentWithRates("a", 1.0, 1.0);
      assertThat(SegmentSignalGate.filterInteractionSegmentsRatesAboveBaseline(List.of(a), null))
          .containsExactly(a);
    }

    @Test
    void shouldFilterPreservingOrder() {
      Map<String, Object> baseline = metricsRow(2.0, 2.0);
      RootCauseSegment strong = segmentWithRates("strong", 5.0, 5.0);
      RootCauseSegment weak = segmentWithRates("weak", 1.0, 2.0);
      List<RootCauseSegment> kept =
          SegmentSignalGate.filterInteractionSegmentsRatesAboveBaseline(
              Arrays.asList(strong, weak), baseline);
      assertThat(kept).containsExactly(strong);
    }
  }

  @Nested
  class ScreenBadFrustrationAboveBaseline {

    private static Map<String, Object> metricsWithBad(long bad) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put(ScreenRcaQueryBuilder.BAD_FRUSTRATION, (double) bad);
      return m;
    }

    private static RootCauseSegment screenSegment(String label, long badCount) {
      return RootCauseSegment.builder().label(label).metrics(metricsWithBad(badCount)).build();
    }

    @Test
    void shouldCompareRawBadFrustrationAgainstBaseline() {
      Map<String, Object> baseline = metricsWithBad(100L);
      RootCauseSegment s = screenSegment("hot", 101L);
      assertThat(SegmentSignalGate.isEligibleRawMetricAboveBaseline(s, baseline,
              ScreenRcaQueryBuilder.BAD_FRUSTRATION))
          .isTrue();
    }

    @Test
    void shouldRejectWhenBadFrustrationDoesNotStrictlyBeatBaseline() {
      Map<String, Object> baseline = metricsWithBad(99L);
      RootCauseSegment s = screenSegment("equal", 99L);
      assertThat(SegmentSignalGate.isEligibleRawMetricAboveBaseline(s, baseline,
              ScreenRcaQueryBuilder.BAD_FRUSTRATION))
          .isFalse();
    }

    @Test
    void shouldFilterByBadFrustrationPreservingOrder() {
      Map<String, Object> baseline = metricsWithBad(50L);
      RootCauseSegment a = screenSegment("a", 60L);
      RootCauseSegment b = screenSegment("b", 50L);
      List<RootCauseSegment> kept = SegmentSignalGate.filterSegmentsRawMetricAboveBaseline(
          Arrays.asList(a, b), baseline, ScreenRcaQueryBuilder.BAD_FRUSTRATION);
      assertThat(kept).containsExactly(a);
    }
  }

  @Nested
  class ScreenBadFrustrationRateAboveBaseline {

    private static Map<String, Object> screenMetrics(long vol, long bad) {
      Map<String, Object> m = new LinkedHashMap<>();
      m.put(ScreenRcaQueryBuilder.CLICK_VOLUME, vol);
      m.put(ScreenRcaQueryBuilder.BAD_FRUSTRATION, bad);
      return m;
    }

    private static RootCauseSegment screenSegment(String label, long vol, long bad) {
      return RootCauseSegment.builder().label(label).metrics(screenMetrics(vol, bad)).build();
    }

    @Test
    void shouldCompareRatesNotRawCounts_smallCohortHighRatePasses() {
      Map<String, Object> baseline = screenMetrics(500L, 100L);
      RootCauseSegment s = screenSegment("hot", 10L, 8L);
      assertThat(
              SegmentSignalGate.isEligibleRateAboveBaseline(
                  s,
                  baseline,
                  ScreenRcaQueryBuilder.BAD_FRUSTRATION,
                  ScreenRcaQueryBuilder.CLICK_VOLUME))
          .isTrue();
    }

    @Test
    void shouldRejectWhenRateDoesNotStrictlyBeatBaseline() {
      Map<String, Object> baseline = screenMetrics(500L, 100L);
      RootCauseSegment s = screenSegment("eq", 50L, 10L);
      assertThat(
              SegmentSignalGate.isEligibleRateAboveBaseline(
                  s,
                  baseline,
                  ScreenRcaQueryBuilder.BAD_FRUSTRATION,
                  ScreenRcaQueryBuilder.CLICK_VOLUME))
          .isFalse();
    }

    @Test
    void shouldRejectWhenHigherRawBadButLowerRate() {
      Map<String, Object> baseline = screenMetrics(500L, 100L);
      RootCauseSegment s = screenSegment("misleading", 1000L, 150L);
      assertThat(
              SegmentSignalGate.isEligibleRateAboveBaseline(
                  s,
                  baseline,
                  ScreenRcaQueryBuilder.BAD_FRUSTRATION,
                  ScreenRcaQueryBuilder.CLICK_VOLUME))
          .isFalse();
    }

    @Test
    void shouldFilterByRatePreservingOrder() {
      Map<String, Object> baseline = screenMetrics(100L, 20L);
      RootCauseSegment strong = screenSegment("strong", 10L, 5L);
      RootCauseSegment weak = screenSegment("weak", 10L, 2L);
      List<RootCauseSegment> kept =
          SegmentSignalGate.filterSegmentsRateAboveBaseline(
              Arrays.asList(strong, weak),
              baseline,
              ScreenRcaQueryBuilder.BAD_FRUSTRATION,
              ScreenRcaQueryBuilder.CLICK_VOLUME);
      assertThat(kept).containsExactly(strong);
    }

    @Test
    void shouldPassThroughAllWhenBaselineMapNullInFilter() {
      RootCauseSegment s = screenSegment("a", 10L, 5L);
      assertThat(
              SegmentSignalGate.filterSegmentsRateAboveBaseline(
                  List.of(s),
                  null,
                  ScreenRcaQueryBuilder.BAD_FRUSTRATION,
                  ScreenRcaQueryBuilder.CLICK_VOLUME))
          .containsExactly(s);
    }
  }
}
