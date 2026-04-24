package org.dreamhorizon.pulseserver.service.rca;

import org.dreamhorizon.pulseserver.dao.rcajob.models.RcaReportJob;

/**
 * Result of {@link RcaReportJobService#createOrGetJob}: existing in-flight job vs newly inserted job
 * that should be enqueued to the worker. Request body and regenerate flag are not stored in MySQL;
 * they are carried only for the creator path that runs {@code enqueueProcess}.
 */
public record RcaJobDispatch(
    RcaReportJob job,
    boolean shouldEnqueueWorker,
    String requestBody,
    boolean forceRootCauseRefresh) {}
