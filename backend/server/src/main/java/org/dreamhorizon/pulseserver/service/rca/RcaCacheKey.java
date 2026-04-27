package org.dreamhorizon.pulseserver.service.rca;

import java.time.LocalDate;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;

/**
 * Cache key + original POST body for async RCA job creation.
 * Supports multiple RCA types: INTERACTION, SESSION, SCREEN, etc.
 */
public record RcaCacheKey(
    String projectId,
    RcaType type,
    String entityKey,
    LocalDate date,
    boolean regenerate,
    String requestBody) {}
