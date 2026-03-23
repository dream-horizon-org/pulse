package org.dreamhorizon.pulsespark.model;

import java.util.List;

/**
 * Mirrors the MySQL `funnel` table row, with JSON columns already deserialized.
 */
public record FunnelDefinition(
        String funnelId,
        String projectId,
        List<FunnelStep> steps,
        long windowSeconds,
        String mode,          // UNIQUE_USERS | SESSIONS
        int dateRangeDays,
        List<FunnelFilter> globalFilters
) {}
