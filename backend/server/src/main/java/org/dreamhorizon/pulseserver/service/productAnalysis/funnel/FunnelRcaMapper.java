package org.dreamhorizon.pulseserver.service.productAnalysis.funnel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models.FunnelDropoffCauseRow;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;

/**
 * Maps precomputed {@code funnel_dropoff_attribution} rows into {@link RootCauseResult} for the
 * async RCA pipeline and pulse_ai narrative.
 */
public final class FunnelRcaMapper {

  private static final int MAX_CAUSE_SEGMENTS = 8;

  private FunnelRcaMapper() {}

  public static RootCauseResult toRootCauseResult(
      List<FunnelDropoffCauseRow> causes,
      String funnelMode,
      String focusStepName,
      int focusStepIndex,
      long funnelId) {
    if (causes == null || causes.isEmpty()) {
      return noDataUnavailable(funnelId, focusStepIndex, focusStepName, funnelMode, null);
    }

    List<FunnelDropoffCauseRow> ranked =
        causes.stream()
            .sorted(
                Comparator.comparing(
                        (FunnelDropoffCauseRow r) -> r.getLift() == null ? 0.0 : r.getLift(),
                        Comparator.reverseOrder())
                    .thenComparing(
                        (FunnelDropoffCauseRow r) ->
                            r.getDropoffAffected() == null ? 0L : r.getDropoffAffected(),
                        Comparator.reverseOrder()))
            .limit(MAX_CAUSE_SEGMENTS)
            .toList();

    FunnelDropoffCauseRow first = ranked.get(0);
    long dropoffCohort = nz(first.getDropoffCohort());
    long dropoffAffected = nz(first.getDropoffAffected());
    double dropoffRatePct =
        dropoffCohort == 0 ? 0.0 : Math.round((dropoffAffected * 1000.0 / dropoffCohort)) / 10.0;

    Map<String, Object> baseline = new HashMap<>();
    baseline.put("funnel_id", funnelId);
    baseline.put("focus_step_index", focusStepIndex);
    baseline.put("focus_step_name", focusStepName != null ? focusStepName : "");
    baseline.put("funnel_mode", funnelMode != null ? funnelMode : "");
    baseline.put("dropoff_cohort", dropoffCohort);
    baseline.put("converter_cohort", nz(first.getConverterCohort()));
    baseline.put("dropoff_rate_pct", dropoffRatePct);

    List<RootCauseSegment> segments = new ArrayList<>(ranked.size());
    for (FunnelDropoffCauseRow row : ranked) {
      segments.add(toSegment(row));
    }

    return RootCauseResult.builder()
        .mode(RootCauseAnalysisMode.FLAT)
        .baseline(baseline)
        .segments(segments)
        .everythingGood(false)
        .noDataAvailable(false)
        .build();
  }

  private static RootCauseSegment toSegment(FunnelDropoffCauseRow row) {
    String label =
        row.getCauseLabel() != null && !row.getCauseLabel().isBlank()
            ? row.getCauseLabel()
            : (row.getCauseKind() != null ? row.getCauseKind() : "unknown");

    long dropoffCohort = nz(row.getDropoffCohort());
    long dropoffAffected = nz(row.getDropoffAffected());
    long converterAffected = nz(row.getConverterAffected());
    double lift = row.getLift() == null ? 0.0 : row.getLift();
    double dropoffRatePct =
        dropoffCohort == 0 ? 0.0 : Math.round((dropoffAffected * 1000.0 / dropoffCohort)) / 10.0;
    double converterRatePct =
        nz(row.getConverterCohort()) == 0
            ? 0.0
            : Math.round((converterAffected * 1000.0 / nz(row.getConverterCohort())) * 10.0) / 10.0;

    Map<String, Object> metrics = new HashMap<>();
    metrics.put("lift", lift);
    metrics.put("dropoff_affected", dropoffAffected);
    metrics.put("dropoff_cohort", dropoffCohort);
    metrics.put("dropoff_rate_pct", dropoffRatePct);
    metrics.put("converter_affected", converterAffected);
    metrics.put("converter_rate_pct", converterRatePct);

    Map<String, String> dimensions = new HashMap<>();
    if (row.getCauseKind() != null) {
      dimensions.put("cause_kind", row.getCauseKind());
    }
    if (row.getCauseKey() != null) {
      dimensions.put("cause_key", row.getCauseKey());
    }

    return RootCauseSegment.builder()
        .label(label)
        .dimensions(dimensions)
        .metrics(metrics)
        .deltas(Map.of())
        .exampleSessionIds(FunnelDropoffMapper.splitCsv(row.getExampleSessions()))
        .build();
  }

  /** Payload when attribution is missing or enrichment could not load funnel metadata. */
  public static RootCauseResult noDataUnavailable(
      long funnelId,
      int focusStepIndex,
      String focusStepName,
      String funnelMode,
      String message) {
    return RootCauseResult.builder()
        .mode(RootCauseAnalysisMode.FLAT)
        .baseline(
            Map.of(
                "funnel_id", funnelId,
                "focus_step_index", focusStepIndex,
                "focus_step_name", focusStepName != null ? focusStepName : "",
                "funnel_mode", funnelMode != null ? funnelMode : ""))
        .segments(List.of())
        .noDataAvailable(true)
        .message(
            message != null && !message.isBlank()
                ? message
                : "Funnel drop-off attribution is not available for this step yet.")
        .build();
  }

  private static long nz(Long v) {
    return v == null ? 0L : v;
  }
}
