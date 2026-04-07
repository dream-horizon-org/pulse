package org.dreamhorizon.pulseserver.analysis;

import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelType;

/**
 * Derives display status from AUTO|ONCE and latest spark_jobs.status (PENDING, RUNNING, SUCCEEDED,
 * FAILED). Funnel and journey share the same rules.
 */
public final class AnalysisComputedStatusResolver {

  private AnalysisComputedStatusResolver() {
  }

  public static AnalysisComputedStatus compute(FunnelType autoOrOnce, String latestJobStatusOrNull) {
    String status = latestJobStatusOrNull == null ? null : latestJobStatusOrNull.trim().toUpperCase();
    if ("PENDING".equals(status) || "RUNNING".equals(status) || "SUBMITTED".equals(status)) {
      return AnalysisComputedStatus.IN_PROGRESS;
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
