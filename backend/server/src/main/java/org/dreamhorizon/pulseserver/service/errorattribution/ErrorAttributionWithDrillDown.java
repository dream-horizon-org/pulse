package org.dreamhorizon.pulseserver.service.errorattribution;

import java.util.List;

/**
 * In-memory bundle after drill-only aggregation: merged related rows plus resolved RR floor from
 * config (used when mapping to RCA / REST payloads).
 */
public record ErrorAttributionWithDrillDown(
    List<ErrorAttributionRelatedAttributionRow> relatedAttributions,
    /** Resolved minimum RR config; {@code null} if not applicable. */
    Double minRiskRatioForIssueAttribution) {}
