package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;

import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionResult.RiskRatioRow;
import org.junit.jupiter.api.Test;

class ErrorAttributionRiskMathTest {

  @Test
  void finiteRiskRatioMatchesP1OverP2() {
    RiskRatioRow row = ErrorAttributionRiskMath.buildRiskRow("crash", 100L, 900L, 10L, 45L);
    assertThat(row.getP1()).isEqualTo(0.1);
    assertThat(row.getP2()).isEqualTo(0.05);
    assertThat(row.getRrUndefined()).isFalse();
    assertThat(row.getRr()).isEqualTo(2.0);
  }

  @Test
  void emptyTreatedArm() {
    RiskRatioRow row = ErrorAttributionRiskMath.buildRiskRow("anr", 0L, 100L, 0L, 5L);
    assertThat(row.getRrUndefined()).isTrue();
    assertThat(row.getRrUndefinedReason()).isEqualTo(ErrorAttributionResult.RR_EMPTY_TREATED_ARM);
  }

  @Test
  void infiniteRrWhenControlPoorRateZero() {
    RiskRatioRow row = ErrorAttributionRiskMath.buildRiskRow("nf", 50L, 100L, 5L, 0L);
    assertThat(row.getRrUndefined()).isTrue();
    assertThat(row.getRrUndefinedReason()).isEqualTo(ErrorAttributionResult.RR_INFINITE_RR);
  }

  @Test
  void winnerComparableKeyOverloadMatchesRiskRatioRow() {
    RiskRatioRow row = ErrorAttributionRiskMath.buildRiskRow("crash", 100L, 900L, 10L, 45L);
    assertThat(
            ErrorAttributionRiskMath.winnerComparableKey(
                row.getRrUndefined(), row.getRrUndefinedReason(), row.getRr()))
        .isEqualTo(ErrorAttributionRiskMath.winnerComparableKey(row));
  }

  @Test
  void passesRelatedThreshold_emptyAlwaysFalseEvenWhenFloorDisabled() {
    assertThat(
            ErrorAttributionRiskMath.passesRelatedThreshold(
                true, ErrorAttributionResult.RR_EMPTY_TREATED_ARM, null, 0.5))
        .isFalse();
    assertThat(
            ErrorAttributionRiskMath.passesRelatedThreshold(
                true, ErrorAttributionResult.RR_ZERO_POOR, null, 0.5))
        .isFalse();
  }

  @Test
  void passesRelatedThreshold_infiniteAlwaysTrue() {
    assertThat(
            ErrorAttributionRiskMath.passesRelatedThreshold(
                true, ErrorAttributionResult.RR_INFINITE_RR, null, 2.0))
        .isTrue();
  }

  @Test
  void passesRelatedThreshold_floorDisabledAcceptsFinite() {
    assertThat(
            ErrorAttributionRiskMath.passesRelatedThreshold(false, null, 1.1, 1.0))
        .isTrue();
  }

  @Test
  void passesRelatedThreshold_floorOnRequiresMinRr() {
    assertThat(ErrorAttributionRiskMath.passesRelatedThreshold(false, null, 1.5, 2.0)).isFalse();
    assertThat(ErrorAttributionRiskMath.passesRelatedThreshold(false, null, 2.0, 2.0)).isTrue();
  }

  @Test
  void passesTreatedPrevalence_floorDisabledReturnsTrueWhenNonPositivePhi() {
    assertThat(ErrorAttributionRiskMath.passesTreatedPrevalenceInUniverse(5L, 99_995L, 0.0d))
        .isTrue();
    assertThat(ErrorAttributionRiskMath.passesTreatedPrevalenceInUniverse(5L, 99_995L, -0.1d))
        .isTrue();
  }

  @Test
  void passesTreatedPrevalence_phiZeroPointZeroFiveRequiresCeilSessions() {
    double phi = RootCauseConfig.DEFAULT_MIN_TREATED_PREVALENCE_FRACTION_IN_U;
    assertThat(phi).isEqualTo(5.0e-4);
    assertThat(ErrorAttributionRiskMath.passesTreatedPrevalenceInUniverse(5L, 99_995L, phi))
        .isFalse();
    assertThat(ErrorAttributionRiskMath.passesTreatedPrevalenceInUniverse(50L, 99_950L, phi))
        .isTrue();
    assertThat(ErrorAttributionRiskMath.passesTreatedPrevalenceInUniverse(1L, 10L, phi))
        .isTrue();
  }

  @Test
  void compareWinnerKeysDescending_nanLast() {
    double finite = 2.0;
    double nan = Double.NaN;
    assertThat(ErrorAttributionRiskMath.compareWinnerKeysDescending(finite, nan)).isNegative();
    assertThat(ErrorAttributionRiskMath.compareWinnerKeysDescending(nan, finite)).isPositive();
  }
}
