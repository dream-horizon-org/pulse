package org.dreamhorizon.pulseserver.service.rca;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;

/**
 * Validated RCA POST payload for enrichment (matches {@code RcaReportProxyHandler} parsing).
 * Supports multiple RCA types: INTERACTION, SESSION, SCREEN, etc.
 */
public record RcaParsedReportBody(
    String rawBody,
    ObjectNode bodyRoot,
    String projectId,
    RcaType entityType,
    String entityKey,
    LocalDate date,
    boolean regenerate) {}
