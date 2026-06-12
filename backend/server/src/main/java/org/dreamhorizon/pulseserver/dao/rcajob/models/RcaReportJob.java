package org.dreamhorizon.pulseserver.dao.rcajob.models;

import java.time.Instant;
import java.time.LocalDate;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;

/**
 * Row from {@code pulse_db.rca_report_jobs}.
 * Generic async RCA job supporting multiple report types (INTERACTION, SESSION, SCREEN, etc.).
 */
public record RcaReportJob(
    String jobId,
    String projectId,
    RcaType entityType,
    String entityKey,
    LocalDate date,
    RcaJobStatus status,
    String errorMessage,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    String createdBy,
    String workerInstanceId) {}
