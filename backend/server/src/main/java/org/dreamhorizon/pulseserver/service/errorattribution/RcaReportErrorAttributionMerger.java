package org.dreamhorizon.pulseserver.service.errorattribution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.interaction.models.ErrorAttributionRestResponse;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;

/**
 * Injects {@code errorAttribution} into {@code report.structured} on successful RCA POST responses,
 * using the same interaction window as {@link RootCauseQueryBuilder.Window} and the same drill
 * the same four drill signals as the former REST drill path (crash, anr, non_fatal, api).
 *
 * <p>Runs independently of whether RCA segments exist (unlike heatmap merge). Failures are logged
 * and skipped so the RCA response still persists.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public final class RcaReportErrorAttributionMerger {

  /** Matches {@code pulse-ui} {@code ErrorAttribution.tsx} {@code ALL_DRILL_SIGNALS} order. */
  private static final List<ErrorAttributionDrillDownSignal> CANONICAL_DRILL_SIGNALS =
      List.of(
          ErrorAttributionDrillDownSignal.crash,
          ErrorAttributionDrillDownSignal.anr,
          ErrorAttributionDrillDownSignal.non_fatal,
          ErrorAttributionDrillDownSignal.api);

  private final ObjectMapper objectMapper;
  private final ErrorAttributionService errorAttributionService;

  /**
   * Sets {@code report.structured.errorAttribution} when {@code report.structured} is an object.
   * No-op if structured is missing, window is invalid, or the service fails.
   */
  public void mergeInto(
      ObjectNode responseRoot,
      String projectId,
      String interactionName,
      LocalDate anchorDate,
      Instant windowEndExclusive,
      int lookbackDays) {
    JsonNode structured = responseRoot.path("report").path("structured");
    if (!structured.isObject()) {
      log.debug("RCA error attribution merge: report.structured missing or not an object");
      return;
    }
    ObjectNode structuredObj = (ObjectNode) structured;
    final RootCauseQueryBuilder.Window window;
    try {
      window = new RootCauseQueryBuilder.Window(anchorDate, lookbackDays, windowEndExclusive);
    } catch (IllegalArgumentException e) {
      log.warn("RCA error attribution merge skipped: {}", e.getMessage());
      return;
    }
    try {
      ErrorAttributionWithDrillDown bundle =
          errorAttributionService
              .getErrorAttributionWithOptionalDrillDown(
                  projectId,
                  interactionName,
                  window.startInclusive,
                  window.endExclusive,
                  CANONICAL_DRILL_SIGNALS)
              .subscribeOn(Schedulers.io())
              .blockingGet();
      ErrorAttributionRestResponse rest = ErrorAttributionRestResponseMapper.fromBundle(bundle);
      structuredObj.set("errorAttribution", objectMapper.valueToTree(rest));
    } catch (RuntimeException e) {
      log.warn("RCA error attribution merge failed: {}", e.getMessage());
    }
  }
}
