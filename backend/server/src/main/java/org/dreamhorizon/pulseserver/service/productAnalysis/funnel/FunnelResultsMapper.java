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
   * conversion % (from first step), step-to-step drop-off %, and (when revenue is configured)
   * per-step Revenue / AOV / LostRevenue plus top-level totals.
   *
   * @param currency optional currency code to thread onto the response (display only)
   */
  public static FunnelResultsResponse fromRows(List<FunnelResultRow> rows, String currency) {
    if (rows == null || rows.isEmpty()) {
      return FunnelResultsResponse.builder()
        .steps(List.of())
        .totalEnteredUsers(0L)
        .overallConversionRate(0.0)
        .currency(currency)
        .build();
    }

    List<FunnelStepMeasureResult> steps = new ArrayList<>(rows.size());
    Long prevCount = null;
    long firstCount = 0L;
    boolean first = true;
    Double totalRevenue = null;
    Long totalOrderCount = null;
    Double overallAov = null;

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
          .medianStepSeconds(r.getMedianStepSeconds())
          .orderCount(r.getOrderCount())
          .revenue(round4(r.getRevenue()))
          .avgOrderValue(round4(r.getAvgOrderValue()))
          .lostRevenue(round4(r.getLostRevenue()))
          .build());
      prevCount = count;

      // Revenue/AOV/orderCount are constant across pre-purchase steps in ordered funnels and
      // constant across all steps in unordered funnels — capture whichever non-null value we see
      // first as the response-level totals.
      if (totalRevenue == null && r.getRevenue() != null) {
        totalRevenue = round4(r.getRevenue());
      }
      if (totalOrderCount == null && r.getOrderCount() != null) {
        totalOrderCount = r.getOrderCount();
      }
      if (overallAov == null && r.getAvgOrderValue() != null) {
        overallAov = round4(r.getAvgOrderValue());
      }
    }

    double overall =
      steps.isEmpty() ? 0.0 : steps.get(steps.size() - 1).getConversionRate();

    return FunnelResultsResponse.builder()
      .steps(steps)
      .totalEnteredUsers(firstCount)
      .overallConversionRate(overall)
      .totalRevenue(totalRevenue)
      .totalOrderCount(totalOrderCount)
      .overallAvgOrderValue(overallAov)
      .currency(currency)
      .lastRunAt(rows.get(0).getRunTime())
      .build();
  }

  /** Back-compat overload — kept for callers that don't have a funnel definition handy. */
  public static FunnelResultsResponse fromRows(List<FunnelResultRow> rows) {
    return fromRows(rows, null);
  }

  private static Double round4(Double v) {
    if (v == null) {
      return null;
    }
    return Math.round(v * 10_000.0) / 10_000.0;
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
