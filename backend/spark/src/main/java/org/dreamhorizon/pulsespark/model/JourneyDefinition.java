package org.dreamhorizon.pulsespark.model;

import java.sql.Timestamp;
import java.util.List;

public record JourneyDefinition(
        long id,
        String projectId,
        String anchorEvent,
        String direction,
        int depth,
        String mode,
        int dateRange,
        List<FunnelFilter> globalFilters,
        String journeyType,
        Timestamp startTime,
        Timestamp endTime
) {}
