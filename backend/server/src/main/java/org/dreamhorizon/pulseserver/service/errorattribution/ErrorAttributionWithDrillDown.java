package org.dreamhorizon.pulseserver.service.errorattribution;

import java.util.List;

/**
 * In-memory bundle for one HTTP error-attribution response: merged related rows plus resolved RR
 * floor from config.
 */
public record ErrorAttributionWithDrillDown(
    List<ErrorAttributionRelatedAttributionRow> relatedAttributions,
    /** Resolved minimum RR config; {@code null} if not applicable. */
    Double minRiskRatioForIssueAttribution) {}
