package org.dreamhorizon.pulseserver.dao.insightreport.models;

import java.time.Instant;

public record InsightReportCacheHit(String reportBody, Instant cachedAt) {}
