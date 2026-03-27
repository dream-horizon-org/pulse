package org.dreamhorizon.pulseserver.analysis;

import org.dreamhorizon.pulseserver.resources.funnel.models.FunnelType;

/**
 * Derives display status from AUTO|ONCE and latest spark_jobs.status (PENDING, RUNNING, SUCCEEDED,
 * FAILED). Funnel and journey share the same rules.
 */
public final class AnalysisComputedStatusResolver {

  private AnalysisComputedStatusResolver() {}

  public static AnalysisComputedStatus compute(FunnelType autoOrOnce, String latestJobStatusOrNull) {
    String j = latestJobStatusOrNull == null ? null : latestJobStatusOrNull.trim().toUpperCase();
    if ("PENDING".equals(j) || "RUNNING".equals(j)) {
      return AnalysisComputedStatus.IN_PROGRESS;
    }
    boolean noJob = j == null || j.isEmpty();
    if (noJob) {
      return autoOrOnce == FunnelType.ONCE
          ? AnalysisComputedStatus.PENDING
          : AnalysisComputedStatus.ACTIVE;
    }
    if (autoOrOnce == FunnelType.AUTO) {
      if ("FAILED".equals(j)) {
        return AnalysisComputedStatus.WARN;
      }
      if ("SUCCEEDED".equals(j)) {
        return AnalysisComputedStatus.ACTIVE;
      }
    } else {
      if ("FAILED".equals(j)) {
        return AnalysisComputedStatus.FAILED;
      }
      if ("SUCCEEDED".equals(j)) {
        return AnalysisComputedStatus.COMPLETED;
      }
    }
    return AnalysisComputedStatus.ACTIVE;
  }
}
