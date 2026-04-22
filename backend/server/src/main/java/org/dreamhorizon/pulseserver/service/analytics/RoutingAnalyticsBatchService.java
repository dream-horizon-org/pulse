package org.dreamhorizon.pulseserver.service.analytics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Single;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.AnalyticsEngineConfig;

/**
 * Routing implementation of {@link AnalyticsBatchService} that delegates to either the Spark or
 * ClickHouse implementation based on {@link AnalyticsEngineConfig#isClickHouseEngine()}.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RoutingAnalyticsBatchService implements AnalyticsBatchService {

  private final AnalyticsEngineConfig config;
  private final AnalyticsBatchServiceImpl sparkImpl;
  private final ClickHouseBatchServiceImpl clickHouseImpl;

  private AnalyticsBatchService active() {
    return config.isClickHouseEngine() ? clickHouseImpl : sparkImpl;
  }

  @Override
  public Single<Boolean> triggerFunnelsBatch() {
    return active().triggerFunnelsBatch();
  }

  @Override
  public Single<Boolean> triggerJourneysBatch() {
    return active().triggerJourneysBatch();
  }

  @Override
  public Single<Boolean> triggerEventsBatch() {
    return active().triggerEventsBatch();
  }

  @Override
  public Single<Boolean> triggerFunnelOnSaveJob(Long funnelId) {
    return active().triggerFunnelOnSaveJob(funnelId);
  }

  @Override
  public Single<Boolean> triggerJourneyOnSaveJob(Long journeyId) {
    return active().triggerJourneyOnSaveJob(journeyId);
  }
}
