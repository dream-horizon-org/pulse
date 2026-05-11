package org.dreamhorizon.pulsespark.model;

public record FunnelResult(
        long funnelId,
        String projectId,
        String runTime,
        int stepIndex,
        String stepName,
        long userCount,
        double conversionPct,
        Long medianStepSeconds,
        Long orderCount,
        Double revenue,
        Double avgOrderValue,
        Double lostRevenue
) {}
