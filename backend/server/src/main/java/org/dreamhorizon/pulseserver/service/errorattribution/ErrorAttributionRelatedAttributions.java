package org.dreamhorizon.pulseserver.service.errorattribution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownResult.IssueRow;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionDrillDownResult.NetworkEndpointRow;

/** Builds the merged, RR-threshold-filtered related list from per-signal drill-down results. */
public final class ErrorAttributionRelatedAttributions {

  public static final String ROW_KIND_ISSUE = "issue";
  public static final String ROW_KIND_API = "api";

  private ErrorAttributionRelatedAttributions() {}

  public static List<ErrorAttributionRelatedAttributionRow> buildMerged(
      Map<String, ErrorAttributionDrillDownResult> bySignal, RootCauseConfig rootCauseConfig) {
    double minRr = rootCauseConfig.getMinRiskRatioForIssueAttribution();
    int globalLimit = rootCauseConfig.getIssueDrillDownLimit();
    List<ErrorAttributionRelatedAttributionRow> rows = new ArrayList<>();
    if (bySignal == null || bySignal.isEmpty()) {
      return rows;
    }
    for (Map.Entry<String, ErrorAttributionDrillDownResult> e : bySignal.entrySet()) {
      String signal = e.getKey();
      ErrorAttributionDrillDownResult r = e.getValue();
      if (r == null) {
        continue;
      }
      if (r.getIssues() != null) {
        for (IssueRow i : r.getIssues()) {
          if (ErrorAttributionRiskMath.passesRelatedThreshold(
              i.getRrUndefined(), i.getRrUndefinedReason(), i.getRr(), minRr)) {
            rows.add(fromIssue(signal, i));
          }
        }
      }
      if (r.getNetworkEndpoints() != null) {
        for (NetworkEndpointRow n : r.getNetworkEndpoints()) {
          if (ErrorAttributionRiskMath.passesRelatedThreshold(
              n.getRrUndefined(), n.getRrUndefinedReason(), n.getRr(), minRr)) {
            rows.add(fromEndpoint(signal, n));
          }
        }
      }
    }
    rows.sort(relatedRowComparator());
    if (rows.size() > globalLimit) {
      return List.copyOf(rows.subList(0, globalLimit));
    }
    return rows;
  }

  private static Comparator<ErrorAttributionRelatedAttributionRow> relatedRowComparator() {
    return (a, b) -> {
      double ka =
          ErrorAttributionRiskMath.winnerComparableKey(
              a.rrUndefined(), a.rrUndefinedReason(), a.rr());
      double kb =
          ErrorAttributionRiskMath.winnerComparableKey(
              b.rrUndefined(), b.rrUndefinedReason(), b.rr());
      int c = ErrorAttributionRiskMath.compareWinnerKeysDescending(ka, kb);
      if (c != 0) {
        return c;
      }
      return Integer.compare(signalRank(a.sourceSignal()), signalRank(b.sourceSignal()));
    };
  }

  private static int signalRank(String signal) {
    if (signal == null) {
      return 99;
    }
    return switch (signal) {
      case "crash" -> 0;
      case "anr" -> 1;
      case "non_fatal" -> 2;
      case "api" -> 3;
      default -> 4;
    };
  }

  private static ErrorAttributionRelatedAttributionRow fromIssue(String signal, IssueRow i) {
    return new ErrorAttributionRelatedAttributionRow(
        signal,
        ROW_KIND_ISSUE,
        i.getGroupId(),
        i.getTitle(),
        i.getExceptionType(),
        null,
        null,
        null,
        null,
        null,
        i.getOccurrences(),
        i.getNTreated(),
        i.getNControl(),
        i.getNTreatedLow(),
        i.getNControlLow(),
        i.getP1(),
        i.getP2(),
        i.getRr(),
        i.getRrUndefined(),
        i.getRrUndefinedReason());
  }

  private static ErrorAttributionRelatedAttributionRow fromEndpoint(String signal, NetworkEndpointRow n) {
    return new ErrorAttributionRelatedAttributionRow(
        signal,
        ROW_KIND_API,
        null,
        null,
        null,
        n.getUrl(),
        n.getGraphqlOperationName(),
        n.getGraphqlOperationType(),
        n.getHttpMethod(),
        n.getHttpStatusCode(),
        n.getOccurrences(),
        n.getNTreated(),
        n.getNControl(),
        n.getNTreatedLow(),
        n.getNControlLow(),
        n.getP1(),
        n.getP2(),
        n.getRr(),
        n.getRrUndefined(),
        n.getRrUndefinedReason());
  }
}
