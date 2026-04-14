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
        Timestamp endTime
) {
  public boolean isUnordered() {
    return "UNORDERED".equalsIgnoreCase(stepOrderType);
  }
}
