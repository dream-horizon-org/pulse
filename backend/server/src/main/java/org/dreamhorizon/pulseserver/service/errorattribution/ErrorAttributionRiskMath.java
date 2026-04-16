package org.dreamhorizon.pulseserver.service.errorattribution;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionResult.RiskRatioRow;

/**
 * Shared Track B risk-ratio math for signal-level rows and per-issue drill-down rows.
 */
public final class ErrorAttributionRiskMath {

  public static final int RR_SCALE = 4;

  private ErrorAttributionRiskMath() {}

  public static RiskRatioRow buildRiskRow(
      String signal, long nTreated, long nControl, long nTreatedLow, long nControlLow) {
    Double p1 = nTreated == 0 ? null : (double) nTreatedLow / nTreated;
    Double p2 = nControl == 0 ? null : (double) nControlLow / nControl;

    Double rr = null;
    boolean rrUndefined = true;
    String rrReason = null;

    if (nTreated == 0) {
      rrReason = ErrorAttributionResult.RR_EMPTY_TREATED_ARM;
    } else if (nControl == 0) {
      rrReason = ErrorAttributionResult.RR_EMPTY_CONTROL_ARM;
    } else if (p2 != null && p2 > 0) {
      rr = p1 / p2;
      rrUndefined = false;
      rrReason = null;
    } else if (p1 != null && p1 > 0) {
      rrReason = ErrorAttributionResult.RR_INFINITE_RR;
    } else {
      rrReason = ErrorAttributionResult.RR_ZERO_POOR;
    }

    return RiskRatioRow.builder()
        .signal(signal)
        .nTreated(nTreated)
        .nControl(nControl)
        .nTreatedLow(nTreatedLow)
        .nControlLow(nControlLow)
        .p1(p1)
        .p2(p2)
        .rr(rrUndefined ? null : rr)
        .rrUndefined(rrUndefined)
        .rrUndefinedReason(rrReason)
        .build();
  }

  /**
   * Batch-style 4dp RR for joint-winner tie logic; {@code INFINITE_RR} compares as {@link
   * Double#POSITIVE_INFINITY}.
   */
  public static double winnerComparableKey(RiskRatioRow row) {
    if (Boolean.FALSE.equals(row.getRrUndefined()) && row.getRr() != null) {
      return round4(row.getRr());
    }
    if (Boolean.TRUE.equals(row.getRrUndefined())
        && ErrorAttributionResult.RR_INFINITE_RR.equals(row.getRrUndefinedReason())) {
      return Double.POSITIVE_INFINITY;
    }
    return Double.NaN;
  }

  public static double round4(double v) {
    return BigDecimal.valueOf(v).setScale(RR_SCALE, RoundingMode.HALF_UP).doubleValue();
  }
}
