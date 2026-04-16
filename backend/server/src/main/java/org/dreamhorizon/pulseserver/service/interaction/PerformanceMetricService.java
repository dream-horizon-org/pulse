package org.dreamhorizon.pulseserver.service.interaction;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionBreakdownRes;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionHealthRes;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionMetricsRes;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsReq;
import org.dreamhorizon.pulseserver.resources.performance.models.interaction.InteractionSessionsRes;

public interface PerformanceMetricService {
  Single<PerformanceMetricDistributionRes> getMetricDistribution(QueryRequest request);

  Single<InteractionHealthRes> getInteractionHealth(InteractionHealthReq req);

  Single<InteractionMetricsRes> getInteractionMetrics(InteractionMetricsReq req);

  Single<InteractionBreakdownRes> getInteractionBreakdown(InteractionBreakdownReq req);

  Single<InteractionSessionsRes> getInteractionSessions(InteractionSessionsReq req);
}
