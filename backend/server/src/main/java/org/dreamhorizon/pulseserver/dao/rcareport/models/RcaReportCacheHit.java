package org.dreamhorizon.pulseserver.dao.rcareport.models;

import java.time.Instant;

/**
 * Cached RCA report row from MySQL (body JSON + server cache timestamp).
 */
public record RcaReportCacheHit(String reportBody, Instant cachedAt) {}
