package org.dreamhorizon.pulseserver.service.insight;

import org.dreamhorizon.pulseserver.dao.insightjob.models.InsightJob;

public record InsightJobDispatch(
    InsightJob job,
    boolean shouldEnqueueWorker,
    String requestBody,
    boolean regenerate) {}
