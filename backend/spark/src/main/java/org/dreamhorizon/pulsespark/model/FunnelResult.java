package org.dreamhorizon.pulsespark.model;

/**
 * One row written to ClickHouse otel.funnel_results.
 */
public record FunnelResult(
        String funnelId,
        String projectId,
        String runDate,
        int stepIndex,
        String stepName,
        long userCount,
        double conversionPct
) {}
