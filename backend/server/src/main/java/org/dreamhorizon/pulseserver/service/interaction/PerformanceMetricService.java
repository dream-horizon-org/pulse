package org.dreamhorizon.pulseserver.service.interaction;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;

public interface PerformanceMetricService {
  Single<PerformanceMetricDistributionRes> getMetricDistribution(QueryRequest request);
}
