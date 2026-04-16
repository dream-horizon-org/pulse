package org.dreamhorizon.pulseserver.service.errorattribution;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ErrorAttributionService {

  public static final String SPEC_VERSION = "1";
  public static final String DISCLAIMER =
      "This diagnostic summarizes observational associations only; it does not establish causation.";

  private final RootCauseConfig rootCauseConfig;
  private final ErrorAttributionDrillDownService errorAttributionDrillDownService;

  /**
   * Runs parallel drill ClickHouse queries for each requested signal, merges into
   * {@link ErrorAttributionWithDrillDown#relatedAttributions()} (no summary query). {@code drillSignals}
   * must be non-empty.
   */
  public Single<ErrorAttributionWithDrillDown> getErrorAttributionWithOptionalDrillDown(
      String projectId,
      String interactionName,
      Instant startInclusive,
      Instant endExclusive,
      List<ErrorAttributionDrillDownSignal> drillSignals) {
    List<ErrorAttributionDrillDownSignal> requested =
        drillSignals == null || drillSignals.isEmpty()
            ? List.of()
            : new ArrayList<>(new LinkedHashSet<>(drillSignals));
    if (requested.isEmpty()) {
      return Single.error(
          new IllegalArgumentException("drillSignals must not be empty"));
    }
    List<Single<ErrorAttributionDrillDownResult>> drillSingles = new ArrayList<>();
    for (ErrorAttributionDrillDownSignal s : requested) {
      drillSingles.add(
          errorAttributionDrillDownService.getDrillDown(
              projectId, interactionName, startInclusive, endExclusive, s));
    }
    return Single.zip(
        drillSingles,
        results -> {
          Map<String, ErrorAttributionDrillDownResult> bySignal = new LinkedHashMap<>();
          for (int i = 0; i < requested.size(); i++) {
            bySignal.put(requested.get(i).name(), (ErrorAttributionDrillDownResult) results[i]);
          }
          List<ErrorAttributionRelatedAttributionRow> related =
              ErrorAttributionRelatedAttributions.buildMerged(bySignal, rootCauseConfig);
          return new ErrorAttributionWithDrillDown(
              related, rootCauseConfig.getMinRiskRatioForIssueAttribution());
        });
  }
}
