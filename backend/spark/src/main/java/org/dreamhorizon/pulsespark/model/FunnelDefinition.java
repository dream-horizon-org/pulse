package org.dreamhorizon.pulsespark.model;

import java.sql.Timestamp;
import java.util.List;

public record FunnelDefinition(
        long id,
        String projectId,
        List<FunnelStep> steps,
        long windowSeconds,
        String mode,           // UNIQUE_USERS | SESSIONS
        int dateRange,
        List<FunnelFilter> globalFilters,
        String funnelType,     // AUTO | ONCE
        Timestamp startTime,   // null for AUTO
        Timestamp endTime      // null for AUTO
) {}
