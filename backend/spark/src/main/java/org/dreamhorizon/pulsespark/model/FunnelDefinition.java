package org.dreamhorizon.pulsespark.model;

import java.sql.Timestamp;
import java.util.List;

public record FunnelDefinition(
        long id,
        String projectId,
        List<FunnelStep> steps,
        long windowSeconds,
        String mode,
        int dateRange,
        List<FunnelFilter> globalFilters,
        String funnelType,
        String stepOrderType,
        Timestamp startTime,
        Timestamp endTime,
        String revenueAttribute,
        Integer revenueStepIndex,
        String currency
) {
  public boolean isUnordered() {
    return "UNORDERED".equalsIgnoreCase(stepOrderType);
  }

  public boolean hasRevenueConfig() {
    return revenueAttribute != null && !revenueAttribute.isBlank();
  }

  public int effectiveRevenueStepIndex() {
    if (revenueStepIndex != null && revenueStepIndex >= 0 && revenueStepIndex < steps.size()) {
      return revenueStepIndex;
    }
    return steps.size() - 1;
  }
}
