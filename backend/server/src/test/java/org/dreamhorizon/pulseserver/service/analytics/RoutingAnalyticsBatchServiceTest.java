package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.dreamhorizon.pulseserver.config.AnalyticsEngineConfig;
import org.dreamhorizon.pulseserver.config.SparkConfig;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobDao;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobEntity;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobStatus;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyDao;
import org.dreamhorizon.pulseserver.service.spark.SparkJobService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutingAnalyticsBatchServiceTest {

  @Mock AnalyticsEngineConfig config;

  @Mock SparkConfig sparkConfig;

  @Mock SparkJobService sparkJobService;

  @Mock AnalyticsJobDao sparkAnalyticsJobDao;

  @Mock AnalyticsJobDao chAnalyticsJobDao;

  @Mock ClickHouseComputeService computeService;

  @Mock FunnelDefinitionDao funnelDefinitionDao;

  @Mock JourneyDao journeyDao;

  AnalyticsBatchServiceImpl sparkImpl;
  ClickHouseBatchServiceImpl clickHouseImpl;
  RoutingAnalyticsBatchService service;

  @BeforeEach
  void setUp() {
    sparkImpl =
        new AnalyticsBatchServiceImpl(sparkConfig, sparkJobService, sparkAnalyticsJobDao);
    clickHouseImpl =
        new ClickHouseBatchServiceImpl(
            chAnalyticsJobDao, computeService, funnelDefinitionDao, journeyDao, config);
    service = new RoutingAnalyticsBatchService(config, sparkImpl, clickHouseImpl);
    lenient().when(sparkConfig.getJobJarPath()).thenReturn("/jar.jar");
    lenient().when(sparkConfig.getFunnelsMainClass()).thenReturn("Funnels");
    lenient().when(sparkConfig.getJourneysMainClass()).thenReturn("Journeys");
    lenient().when(sparkConfig.getEventsMainClass()).thenReturn("Events");
    RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
  }

  @AfterEach
  void tearDown() {
    RxJavaPlugins.reset();
  }

  @Test
  void triggerFunnelsBatch_routesToClickHouseWhenConfigured() {
    when(config.isClickHouseEngine()).thenReturn(true);
    when(funnelDefinitionDao.listAllAuto()).thenReturn(Single.just(List.of()));

    assertThat(service.triggerFunnelsBatch().blockingGet()).isTrue();
    verify(funnelDefinitionDao).listAllAuto();
  }

  @Test
  void triggerFunnelsBatch_routesToSparkWhenNotClickHouse() {
    when(config.isClickHouseEngine()).thenReturn(false);
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
    AnalyticsJobEntity latest =
        new AnalyticsJobEntity(
            1L,
            AnalyticsJobType.FUNNELS_DAILY,
            null,
            null,
            null,
            null,
            null,
            null,
            now);
    when(sparkAnalyticsJobDao.getLatestJobByType(AnalyticsJobType.FUNNELS_DAILY))
        .thenReturn(Maybe.just(latest));

    assertThat(service.triggerFunnelsBatch().blockingGet()).isFalse();
    verify(sparkAnalyticsJobDao).getLatestJobByType(AnalyticsJobType.FUNNELS_DAILY);
  }

  @Test
  void triggerJourneyOnSaveJob_delegatesToActiveEngine() {
    when(config.isClickHouseEngine()).thenReturn(true);
    when(chAnalyticsJobDao.insertJob(
            eq(AnalyticsJobType.JOURNEY), eq(9L), isNull(), eq(AnalyticsJobStatus.RUNNING)))
        .thenReturn(Single.just(200L));
    when(computeService.computeJourney(9L)).thenReturn(Single.just(true));
    when(chAnalyticsJobDao.updateJobStatus(
            eq(200L),
            eq(AnalyticsJobStatus.SUCCEEDED),
            isNull(),
            any(LocalDateTime.class),
            any(LocalDateTime.class)))
        .thenReturn(Single.just(1));

    assertThat(service.triggerJourneyOnSaveJob(9L).blockingGet()).isTrue();
    verify(computeService).computeJourney(9L);
  }
}
