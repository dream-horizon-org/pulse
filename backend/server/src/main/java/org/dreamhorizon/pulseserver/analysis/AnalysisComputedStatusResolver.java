package org.dreamhorizon.pulseserver.analysis;

import java.time.Instant;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelType;

/**
 * Derives display status from AUTO|ONCE, the row's {@code expiry}, and latest
 * {@code analytics_jobs.status} (PENDING, RUNNING, SUCCEEDED, FAILED). Funnel and journey
 * share the same rules; the SQL mirror lives in {@code FUNNEL_COMPUTED_STATUS_CASE} /
 * {@code JOURNEY_COMPUTED_STATUS_CASE}.
 *
 * <p>An AUTO row whose {@code expiry} is in the past is treated as stopped and reads as
 * {@code COMPLETED} — matches the semantics of the "Mark as Completed" action which sets
 * {@code expiry = NOW()} without changing {@code funnel_type}.
 */
public final class AnalysisComputedStatusResolver {

  private AnalysisComputedStatusResolver() {
  }

  /**
   * Backwards-compatible overload for callers that don't have access to the row's expiry
   * (legacy code paths). Treats {@code expiry} as null — i.e. never stopped via expiry.
   */
  public static AnalysisComputedStatus compute(FunnelType autoOrOnce, String latestJobStatusOrNull) {
    return compute(autoOrOnce, latestJobStatusOrNull, null);
  }

  public static AnalysisComputedStatus compute(
      FunnelType autoOrOnce, String latestJobStatusOrNull, Instant expiry) {
    String status = latestJobStatusOrNull == null ? null : latestJobStatusOrNull.trim().toUpperCase();
    if ("PENDING".equals(status) || "RUNNING".equals(status) || "SUBMITTED".equals(status)) {
      return AnalysisComputedStatus.IN_PROGRESS;
    }
    // AUTO + expiry passed → stopped → COMPLETED. Mirrors the SQL CASE branch.
    if (autoOrOnce == FunnelType.AUTO
        && expiry != null
        && !expiry.isAfter(Instant.now())) {
      return AnalysisComputedStatus.COMPLETED;
    }
    boolean noJob = status == null || status.isEmpty();
    if (noJob) {
      return autoOrOnce == FunnelType.ONCE
        ? AnalysisComputedStatus.PENDING
        : AnalysisComputedStatus.ACTIVE;
    }
    if (autoOrOnce == FunnelType.AUTO) {
      if ("FAILED".equals(status)) {
        return AnalysisComputedStatus.WARN;
      }
      if ("SUCCEEDED".equals(status)) {
        return AnalysisComputedStatus.ACTIVE;
      }
    } else {
      if ("FAILED".equals(status)) {
        return AnalysisComputedStatus.FAILED;
      }
      if ("SUCCEEDED".equals(status)) {
        return AnalysisComputedStatus.COMPLETED;
      }
    }
    return AnalysisComputedStatus.ACTIVE;
  }
}
