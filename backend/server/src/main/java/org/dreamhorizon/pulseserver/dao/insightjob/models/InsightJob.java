package org.dreamhorizon.pulseserver.dao.insightjob.models;

import java.time.Instant;
import java.time.LocalDate;
import lombok.Builder;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightExecutionMode;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightJobStatus;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;

@Builder
public record InsightJob(
    String jobId,
    String projectId,
    InsightType insightType,
    String entityKey,
    InsightExecutionMode executionMode,
    LocalDate startDate,
    LocalDate endDate,
    InsightJobStatus status,
    String errorMessage,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    String createdBy) {}
