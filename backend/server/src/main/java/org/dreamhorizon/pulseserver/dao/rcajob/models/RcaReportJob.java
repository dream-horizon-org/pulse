package org.dreamhorizon.pulseserver.dao.rcajob.models;

import java.time.Instant;
import java.time.LocalDate;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaJobStatus;

/** Row from {@code pulse_db.rca_report_jobs}. */
public record RcaReportJob(
    String jobId,
    String projectId,
    String interactionName,
    LocalDate date,
    RcaJobStatus status,
    String errorMessage,
    Instant createdAt,
    Instant startedAt,
    Instant completedAt,
    String createdBy,
    String workerInstanceId,
    int version) {}
