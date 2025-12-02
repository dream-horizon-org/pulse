package in.horizonos.pulseserver.service.interaction;

import in.horizonos.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import in.horizonos.pulseserver.resources.performance.models.QueryRequest;
import io.reactivex.rxjava3.core.Single;

public interface PerformanceMetricService {
  Single<PerformanceMetricDistributionRes> getMetricDistribution(QueryRequest request);
}
