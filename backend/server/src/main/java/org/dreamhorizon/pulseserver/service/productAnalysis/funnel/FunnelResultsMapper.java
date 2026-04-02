package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models.FunnelResultRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelResultsResponse;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelStepMeasureResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps ClickHouse {@code funnel_results} rows to the funnel visualization API shape.
 */
public final class FunnelResultsMapper {

  private FunnelResultsMapper() {
  }

  /**
   * Builds the same shape as the UI expects from POST {@code /v1/funnel/analyze}: step counts,
   * conversion % (from first step), and step-to-step drop-off %.
   */
  public static FunnelResultsResponse fromRows(List<FunnelResultRow> rows) {
    if (rows == null || rows.isEmpty()) {
      return FunnelResultsResponse.builder()
        .steps(List.of())
        .totalEnteredUsers(0L)
        .overallConversionRate(0.0)
        .build();
    }

    List<FunnelStepMeasureResult> steps = new ArrayList<>(rows.size());
    Long prevCount = null;
    long firstCount = 0L;
    boolean first = true;

    for (FunnelResultRow r : rows) {
      long count = r.getUserCount() == null ? 0L : r.getUserCount();
      if (first) {
        firstCount = count;
        first = false;
      }
      double conversion = round1(safeConversionPct(r.getConversionPct(), count, firstCount));
      double dropoff = 0.0;
      if (prevCount != null && prevCount > 0) {
        dropoff = round1((prevCount - count) / (double) prevCount * 100.0);
      }

      steps.add(
        FunnelStepMeasureResult.builder()
          .stepName(r.getStepName() == null ? "" : r.getStepName())
          .count(count)
          .conversionRate(conversion)
          .dropoffRate(dropoff)
          .build());
      prevCount = count;
    }

    double overall =
      steps.isEmpty() ? 0.0 : steps.get(steps.size() - 1).getConversionRate();

    return FunnelResultsResponse.builder()
      .steps(steps)
      .totalEnteredUsers(firstCount)
      .overallConversionRate(overall)
      .build();
  }

  private static double safeConversionPct(Double pct, long count, long firstCount) {
    if (pct != null) {
      return pct;
    }
    if (firstCount <= 0) {
      return 0.0;
    }
    return count * 100.0 / (double) firstCount;
  }

  private static double round1(double v) {
    return Math.round(v * 10.0) / 10.0;
  }
}
