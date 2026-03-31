package org.dreamhorizon.pulsespark.model;

import java.sql.Timestamp;
import java.util.List;

public record JourneyDefinition(
        long id,
        String projectId,
        String anchorEvent,
        String direction,      // START | END
        int depth,
        String mode,           // UNIQUE_USERS | SESSIONS
        int dateRange,
        List<FunnelFilter> globalFilters,
        String journeyType,    // AUTO | ONCE
        Timestamp startTime,   // null for AUTO
        Timestamp endTime      // null for AUTO
) {}
