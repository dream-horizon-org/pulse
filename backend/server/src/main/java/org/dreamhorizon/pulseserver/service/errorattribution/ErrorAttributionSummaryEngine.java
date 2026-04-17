package org.dreamhorizon.pulseserver.service.errorattribution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionResult.RiskRatioRow;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;

/**
 * Builds {@link ErrorAttributionResult} from the Track B summary aggregate row shape (universe U).
 * Used for unit tests and offline validation. Production drill paths use
 * {@link ErrorAttributionService#getErrorAttributionWithOptionalDrillDown} (no summary query).
 */
@RequiredArgsConstructor
public final class ErrorAttributionSummaryEngine {

  private final RootCauseConfig rootCauseConfig;

  /**
   * @param rows first row used when non-empty; empty list yields zeroed counts and four risk rows
   */
  public ErrorAttributionResult buildFromAggregateRows(List<Map<String, Object>> rows) {
    Map<String, Object> raw =
        rows == null || rows.isEmpty() ? Map.of() : lowerKeyMap(rows.get(0));
    long nU = NumberCoercionUtils.toLong(raw.get("n_u"));
    long nPoorU = NumberCoercionUtils.toLong(raw.get("n_poor_u"));

    List<RiskRatioRow> riskRows = new ArrayList<>();
    riskRows.add(
        ErrorAttributionRiskMath.buildRiskRow(
            "crash",
            NumberCoercionUtils.toLong(raw.get("n_treated_crash")),
            NumberCoercionUtils.toLong(raw.get("n_control_crash")),
            NumberCoercionUtils.toLong(raw.get("n_treated_low_crash")),
            NumberCoercionUtils.toLong(raw.get("n_control_low_crash"))));
    riskRows.add(
        ErrorAttributionRiskMath.buildRiskRow(
            "anr",
            NumberCoercionUtils.toLong(raw.get("n_treated_anr")),
            NumberCoercionUtils.toLong(raw.get("n_control_anr")),
            NumberCoercionUtils.toLong(raw.get("n_treated_low_anr")),
            NumberCoercionUtils.toLong(raw.get("n_control_low_anr"))));
    riskRows.add(
        ErrorAttributionRiskMath.buildRiskRow(
            "non_fatal",
            NumberCoercionUtils.toLong(raw.get("n_treated_nf")),
            NumberCoercionUtils.toLong(raw.get("n_control_nf")),
            NumberCoercionUtils.toLong(raw.get("n_treated_low_nf")),
            NumberCoercionUtils.toLong(raw.get("n_control_low_nf"))));
    riskRows.add(
        ErrorAttributionRiskMath.buildRiskRow(
            "api",
            NumberCoercionUtils.toLong(raw.get("n_treated_api")),
            NumberCoercionUtils.toLong(raw.get("n_control_api")),
            NumberCoercionUtils.toLong(raw.get("n_treated_low_api")),
            NumberCoercionUtils.toLong(raw.get("n_control_low_api"))));

    int minPoor = rootCauseConfig.getMinPoorSessionsForErrorAttribution();
    boolean insufficient = nPoorU < minPoor;
    List<String> jointWinners = computeJointWinners(riskRows, nPoorU, minPoor);

    return ErrorAttributionResult.builder()
        .trackBInsufficientData(insufficient)
        .minPoorSessionsForErrorAttribution(minPoor)
        .nPoorInU(nPoorU)
        .nU(nU)
        .riskRatios(riskRows)
        .jointWinners(jointWinners)
        .analysisPhase("1")
        .track("B")
        .diagnosticSpecVersion(ErrorAttributionService.SPEC_VERSION)
        .disclaimer(ErrorAttributionService.DISCLAIMER)
        .cachedAt(null)
        .build();
  }

  private static List<String> computeJointWinners(
      List<RiskRatioRow> riskRows, long nPoorInU, int minPoorSessions) {
    if (nPoorInU < minPoorSessions) {
      return null;
    }
    double maxKey = Double.NEGATIVE_INFINITY;
    double[] keys = new double[riskRows.size()];
    for (int i = 0; i < riskRows.size(); i++) {
      double k = ErrorAttributionRiskMath.winnerComparableKey(riskRows.get(i));
      keys[i] = k;
      if (!Double.isNaN(k) && k > maxKey) {
        maxKey = k;
      }
    }
    if (maxKey == Double.NEGATIVE_INFINITY) {
      return null;
    }
    List<String> winners = new ArrayList<>();
    for (int i = 0; i < riskRows.size(); i++) {
      if (!Double.isNaN(keys[i]) && Double.compare(keys[i], maxKey) == 0) {
        winners.add(riskRows.get(i).getSignal());
      }
    }
    return winners.isEmpty() ? null : winners;
  }

  private static Map<String, Object> lowerKeyMap(Map<String, Object> row) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : row.entrySet()) {
      m.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue());
    }
    return m;
  }
}
