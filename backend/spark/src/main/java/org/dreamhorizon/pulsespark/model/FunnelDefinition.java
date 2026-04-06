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
        Timestamp startTime,
        Timestamp endTime
) {}
