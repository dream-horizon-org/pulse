package org.dreamhorizon.pulseserver.dao.insightjob;

import java.time.LocalDate;

public record InsightJobKey(
    String projectId,
    InsightType insightType,
    String entityKey,
    InsightExecutionMode executionMode,
    LocalDate startDate,
    LocalDate endDate) {}
