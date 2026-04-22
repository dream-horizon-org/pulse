package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Single;
import org.dreamhorizon.pulseserver.config.AnalyticsEngineConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutingAnalyticsBatchServiceTest {

  @Mock
  AnalyticsEngineConfig config;

  @Mock
  AnalyticsBatchServiceImpl sparkImpl;

  @Mock
  ClickHouseBatchServiceImpl clickHouseImpl;

  RoutingAnalyticsBatchService service;

  @BeforeEach
  void setUp() {
    service = new RoutingAnalyticsBatchService(config, sparkImpl, clickHouseImpl);
  }

  @Test
  void triggerFunnelsBatch_routesToClickHouseWhenConfigured() {
    when(config.isClickHouseEngine()).thenReturn(true);
    when(clickHouseImpl.triggerFunnelsBatch()).thenReturn(Single.just(true));

    assertThat(service.triggerFunnelsBatch().blockingGet()).isTrue();
    verify(clickHouseImpl).triggerFunnelsBatch();
  }

  @Test
  void triggerFunnelsBatch_routesToSparkWhenNotClickHouse() {
    when(config.isClickHouseEngine()).thenReturn(false);
    when(sparkImpl.triggerFunnelsBatch()).thenReturn(Single.just(false));

    assertThat(service.triggerFunnelsBatch().blockingGet()).isFalse();
    verify(sparkImpl).triggerFunnelsBatch();
  }

  @Test
  void triggerJourneyOnSaveJob_delegatesToActiveEngine() {
    when(config.isClickHouseEngine()).thenReturn(true);
    when(clickHouseImpl.triggerJourneyOnSaveJob(9L)).thenReturn(Single.just(true));

    assertThat(service.triggerJourneyOnSaveJob(9L).blockingGet()).isTrue();
    verify(clickHouseImpl).triggerJourneyOnSaveJob(9L);
  }
}
