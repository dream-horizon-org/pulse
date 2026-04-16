package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;

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
}
