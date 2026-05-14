package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.dreamhorizon.pulseserver.service.rootcause.RootCauseMetricsRegistry.ERROR_RATE;
import static org.dreamhorizon.pulseserver.service.rootcause.RootCauseMetricsRegistry.POOR_USER_PCT;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SegmentSignalGateTest {

  private static final double THRESHOLD = 15.0;

  private static RootCauseSegment segment(String label, Map<String, Double> deltas) {
    return RootCauseSegment.builder().label(label).deltas(deltas).build();
  }

  private static Map<String, Double> deltas(Double err, Double poor) {
    Map<String, Double> m = new LinkedHashMap<>();
    if (err != null) {
      m.put(ERROR_RATE, err);
    }
    if (poor != null) {
      m.put(POOR_USER_PCT, poor);
    }
    return m;
  }

  @Nested
  class ComputeSignal {

    @Test
    void shouldSumAbsolutesOfBothDeltas() {
      assertThat(SegmentSignalGate.computeSignal(deltas(10.0, 5.0))).isEqualTo(15.0);
    }

    @Test
    void shouldTreatNegativeDeltasAsAbsolute() {
      assertThat(SegmentSignalGate.computeSignal(deltas(-12.0, -8.0))).isEqualTo(20.0);
    }

    @Test
    void shouldTreatMixedSignsAsAbsolute() {
      assertThat(SegmentSignalGate.computeSignal(deltas(-20.0, 5.0))).isEqualTo(25.0);
    }

    @Test
    void shouldTreatMissingErrorDeltaAsZero() {
      assertThat(SegmentSignalGate.computeSignal(deltas(null, 18.0))).isEqualTo(18.0);
    }

    @Test
    void shouldTreatMissingPoorDeltaAsZero() {
      assertThat(SegmentSignalGate.computeSignal(deltas(22.0, null))).isEqualTo(22.0);
    }

    @Test
    void shouldReturnZeroWhenBothDeltasMissing() {
      assertThat(SegmentSignalGate.computeSignal(deltas(null, null))).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroForNullDeltasMap() {
      assertThat(SegmentSignalGate.computeSignal((Map<String, Double>) null)).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroForNullSegment() {
      assertThat(SegmentSignalGate.computeSignal((RootCauseSegment) null)).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroForSegmentWithNullDeltas() {
      RootCauseSegment s = RootCauseSegment.builder().label("x").build();
      assertThat(SegmentSignalGate.computeSignal(s)).isEqualTo(0.0);
    }

    @Test
    void shouldIgnoreUnrelatedMetricDeltas() {
      Map<String, Double> d = new HashMap<>();
      d.put(ERROR_RATE, 10.0);
      d.put(POOR_USER_PCT, 4.0);
      d.put("apdex", 99.0);
      d.put("duration_p95", -50.0);
      assertThat(SegmentSignalGate.computeSignal(d)).isEqualTo(14.0);
    }
  }

  @Nested
  class IsEligible {

    @Test
    void shouldDropAtBoundaryBelowThreshold() {
      RootCauseSegment s = segment("below", deltas(10.0, 4.9));
      assertThat(SegmentSignalGate.isEligible(s, THRESHOLD)).isFalse();
    }

    @Test
    void shouldKeepExactlyAtThreshold() {
      RootCauseSegment s = segment("equal", deltas(10.0, 5.0));
      assertThat(SegmentSignalGate.isEligible(s, THRESHOLD)).isTrue();
    }

    @Test
    void shouldKeepWhenOneMetricNullAndOtherStrong() {
      RootCauseSegment s = segment("one-side", deltas(null, 20.0));
      assertThat(SegmentSignalGate.isEligible(s, THRESHOLD)).isTrue();
    }

    @Test
    void shouldDropWhenBothNull() {
      RootCauseSegment s = segment("none", deltas(null, null));
      assertThat(SegmentSignalGate.isEligible(s, THRESHOLD)).isFalse();
    }

    @Test
    void shouldKeepWhenOneZeroAndOtherAtThreshold() {
      RootCauseSegment s = segment("zero-and-strong", deltas(0.0, 15.0));
      assertThat(SegmentSignalGate.isEligible(s, THRESHOLD)).isTrue();
    }

    @Test
    void shouldDropWhenOneZeroAndOtherBelowThreshold() {
      RootCauseSegment s = segment("zero-and-weak", deltas(0.0, 14.9));
      assertThat(SegmentSignalGate.isEligible(s, THRESHOLD)).isFalse();
    }

    @Test
    void shouldKeepWhenStrongNegativeRegression() {
      RootCauseSegment s = segment("regress", deltas(-30.0, 0.0));
      assertThat(SegmentSignalGate.isEligible(s, THRESHOLD)).isTrue();
    }
  }

  @Nested
  class Filter {

    @Test
    void shouldReturnEmptyForNullInput() {
      assertThat(SegmentSignalGate.filter(null, THRESHOLD)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyInput() {
      assertThat(SegmentSignalGate.filter(Collections.emptyList(), THRESHOLD)).isEmpty();
    }

    @Test
    void shouldDropOnlyWeakSegmentsAndPreserveOrder() {
      RootCauseSegment a = segment("strong-1", deltas(20.0, 0.0));
      RootCauseSegment weak = segment("weak", deltas(5.0, 5.0));
      RootCauseSegment b = segment("strong-2", deltas(null, 16.0));
      RootCauseSegment boundaryDrop = segment("boundary-drop", deltas(7.0, 7.9));

      List<RootCauseSegment> kept =
          SegmentSignalGate.filter(Arrays.asList(a, weak, b, boundaryDrop), THRESHOLD);

      assertThat(kept).containsExactly(a, b);
    }

    @Test
    void shouldReturnEmptyWhenAllDrop() {
      RootCauseSegment a = segment("a", deltas(1.0, 2.0));
      RootCauseSegment b = segment("b", deltas(null, null));
      assertThat(SegmentSignalGate.filter(Arrays.asList(a, b), THRESHOLD)).isEmpty();
    }
  }

  /**
   * Screen RCA passes a custom driver-metric key ({@code bad_frustration}); the helper must apply
   * the same null-as-zero / abs / threshold rules to that key set. Asserts no leakage from the
   * default (error_rate, poor_user_pct) pair.
   */
  @Nested
  class CustomMetricKeys {

    private static final String BAD_FRUSTRATION = "bad_frustration";

    private static Map<String, Double> screenDeltas(Double bad) {
      Map<String, Double> m = new LinkedHashMap<>();
      if (bad != null) {
        m.put(BAD_FRUSTRATION, bad);
      }
      return m;
    }

    @Test
    void shouldComputeSignalOverProvidedKeyOnly() {
      assertThat(SegmentSignalGate.computeSignal(screenDeltas(20.0), BAD_FRUSTRATION))
          .isEqualTo(20.0);
    }

    @Test
    void shouldTakeAbsoluteOnNegativeCustomDelta() {
      assertThat(SegmentSignalGate.computeSignal(screenDeltas(-30.0), BAD_FRUSTRATION))
          .isEqualTo(30.0);
    }

    @Test
    void shouldTreatMissingCustomDeltaAsZero() {
      assertThat(SegmentSignalGate.computeSignal(screenDeltas(null), BAD_FRUSTRATION))
          .isEqualTo(0.0);
    }

    @Test
    void shouldIgnoreInteractionDeltasWhenScreenKeysProvided() {
      Map<String, Double> mixed = new HashMap<>();
      mixed.put(ERROR_RATE, 100.0);
      mixed.put(POOR_USER_PCT, 100.0);
      mixed.put(BAD_FRUSTRATION, 5.0);
      assertThat(SegmentSignalGate.computeSignal(mixed, BAD_FRUSTRATION)).isEqualTo(5.0);
    }

    @Test
    void shouldDropScreenSegmentBelowThreshold() {
      RootCauseSegment s = segment("weak-screen", screenDeltas(14.9));
      assertThat(SegmentSignalGate.isEligible(s, THRESHOLD, BAD_FRUSTRATION)).isFalse();
    }

    @Test
    void shouldKeepScreenSegmentAtThreshold() {
      RootCauseSegment s = segment("boundary-screen", screenDeltas(15.0));
      assertThat(SegmentSignalGate.isEligible(s, THRESHOLD, BAD_FRUSTRATION)).isTrue();
    }

    @Test
    void shouldFilterScreenSegmentsPreservingOrder() {
      RootCauseSegment strong = segment("strong-screen", screenDeltas(25.0));
      RootCauseSegment weak = segment("weak-screen", screenDeltas(5.0));
      RootCauseSegment regress = segment("regress-screen", screenDeltas(-18.0));
      List<RootCauseSegment> kept =
          SegmentSignalGate.filter(
              Arrays.asList(strong, weak, regress), THRESHOLD, BAD_FRUSTRATION);
      assertThat(kept).containsExactly(strong, regress);
    }

    @Test
    void shouldReturnZeroWhenMetricKeysEmpty() {
      assertThat(SegmentSignalGate.computeSignal(screenDeltas(99.0), new String[0]))
          .isEqualTo(0.0);
    }
  }

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
}
