package org.dreamhorizon.pulseserver.service.insight;

import java.time.LocalDate;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightExecutionMode;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;

public record InsightCacheKey(
    String projectId,
    InsightType insightType,
    String entityKey,
    InsightExecutionMode executionMode,
    LocalDate startDate,
    LocalDate endDate,
    boolean regenerate,
    String requestBody) {}
