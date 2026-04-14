package org.dreamhorizon.pulseserver.service.rca;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;

/** Validated RCA POST payload for enrichment (matches {@code RcaReportProxyHandler} parsing). */
public record RcaParsedReportBody(
    String rawBody,
    ObjectNode bodyRoot,
    String projectId,
    String interactionName,
    LocalDate date,
    boolean regenerate) {}
