package org.dreamhorizon.pulseserver.service.rca;

import java.time.LocalDate;

/** Cache key + original POST body for async RCA job creation. */
public record RcaCacheKey(
    String projectId,
    String interactionName,
    LocalDate date,
    boolean regenerate,
    String requestBody) {}
