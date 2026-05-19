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
    return winnerComparableKey(
        row.getRrUndefined(), row.getRrUndefinedReason(), row.getRr());
  }

  /**
   * Same ordering as {@link #winnerComparableKey(RiskRatioRow)} for use on drill-down rows before
   * they are wrapped as {@link RiskRatioRow}.
   */
  public static double winnerComparableKey(
      Boolean rrUndefined, String rrUndefinedReason, Double rr) {
    if (Boolean.FALSE.equals(rrUndefined) && rr != null) {
      return round4(rr);
    }
    if (Boolean.TRUE.equals(rrUndefined)
        && ErrorAttributionResult.RR_INFINITE_RR.equals(rrUndefinedReason)) {
      return Double.POSITIVE_INFINITY;
    }
    return Double.NaN;
  }

  /**
   * Descending RR sort: stronger keys first; {@link Double#NaN} always last (unlike naive {@code
   * reversed()} on {@code comparingDouble}, which can rank NaN first).
   */
  public static int compareWinnerKeysDescending(double k1, double k2) {
    boolean n1 = Double.isNaN(k1);
    boolean n2 = Double.isNaN(k2);
    if (n1 && n2) {
      return 0;
    }
    if (n1) {
      return 1;
    }
    if (n2) {
      return -1;
    }
    return Double.compare(k2, k1);
  }

  /**
   * Whether a drill-down row may appear in the merged “related” list for the given configured
   * minimum RR. {@code minRr <= 1.0} disables the finite RR floor; {@code RR_EMPTY_*} and {@code
   * RR_ZERO_POOR} never pass.
   */
  public static boolean passesRelatedThreshold(
      Boolean rrUndefined, String rrUndefinedReason, Double rr, double minRr) {
    String reason = rrUndefinedReason;
    if (ErrorAttributionResult.RR_EMPTY_TREATED_ARM.equals(reason)
        || ErrorAttributionResult.RR_EMPTY_CONTROL_ARM.equals(reason)
        || ErrorAttributionResult.RR_ZERO_POOR.equals(reason)) {
      return false;
    }
    if (Boolean.TRUE.equals(rrUndefined)
        && ErrorAttributionResult.RR_INFINITE_RR.equals(reason)) {
      return true;
    }
    boolean floorDisabled = minRr <= 1.0d;
    if (floorDisabled) {
      return Boolean.FALSE.equals(rrUndefined) && rr != null;
    }
    return Boolean.FALSE.equals(rrUndefined) && rr != null && rr >= minRr;
  }

  /**
   * Prevalence in {@code U}: {@code n_treated / (n_treated + n_control) >= minPrevalenceFraction}. {@code
   * minPrevalenceFraction <= 0} disables the gate ({@code true}). Uses ceiling on the fractional
   * minimum count so thresholds match “at least φ share”.
   */
  public static boolean passesTreatedPrevalenceInUniverse(
      Long nTreated, Long nControl, double minPrevalenceFraction) {
    if (minPrevalenceFraction <= 0.0d) {
      return true;
    }
    long treated = nTreated == null ? 0L : nTreated;
    long control = nControl == null ? 0L : nControl;
    long nu = treated + control;
    if (nu <= 0) {
      return false;
    }
    double minSessions = minPrevalenceFraction * (double) nu;
    long minRequired = (long) Math.ceil(minSessions - 1e-12);
    if (minRequired < 1) {
      minRequired = 1;
    }
    return treated >= minRequired;
  }

  public static double round4(double v) {
    return BigDecimal.valueOf(v).setScale(RR_SCALE, RoundingMode.HALF_UP).doubleValue();
  }
}
