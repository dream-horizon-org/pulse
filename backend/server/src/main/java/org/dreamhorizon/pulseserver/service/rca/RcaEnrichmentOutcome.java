package org.dreamhorizon.pulseserver.service.rca;

import java.time.Instant;
import java.time.LocalDate;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;

/**
 * Enriched request body for the AI RCA endpoint plus context for post-AI heatmap merge and cache
 * write.
 */
public record RcaEnrichmentOutcome(
    String body,
    RootCauseResult rootCause,
    LocalDate anchorDate,
    Instant windowEndExclusive,
    boolean enrichmentOk) {}
