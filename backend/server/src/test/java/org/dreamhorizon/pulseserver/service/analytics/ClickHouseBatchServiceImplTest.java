package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import io.reactivex.rxjava3.schedulers.Schedulers;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.dreamhorizon.pulseserver.config.AnalyticsEngineConfig;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobDao;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobEntity;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobStatus;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickHouseBatchServiceImplTest {

  @Mock
  AnalyticsJobDao analyticsJobDao;

  @Mock
  ClickHouseComputeService computeService;

  @Mock
  FunnelDefinitionDao funnelDefinitionDao;

  @Mock
  JourneyDao journeyDao;

  @Mock
  AnalyticsEngineConfig analyticsEngineConfig;

  ClickHouseBatchServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new ClickHouseBatchServiceImpl(
            analyticsJobDao, computeService, funnelDefinitionDao, journeyDao, analyticsEngineConfig);
    RxJavaPlugins.setIoSchedulerHandler(s -> Schedulers.trampoline());
  }

  @AfterEach
  void tearDown() {
    RxJavaPlugins.reset();
  }

  @Test
  void triggerEventsBatch_isNoOpTrue() {
    assertThat(service.triggerEventsBatch().blockingGet()).isTrue();
  }

  @Test
  void triggerFunnelsBatch_returnsTrueWhenNoAutoFunnels() {
    when(funnelDefinitionDao.listAllAuto()).thenReturn(Single.just(List.of()));

    assertThat(service.triggerFunnelsBatch().blockingGet()).isTrue();
  }

  @Test
  void triggerFunnelsBatch_skipsWhenDailyAlreadyRanToday() {
    FunnelDefinitionRow def =
        FunnelDefinitionRow.builder()
            .id(1L)
            .projectId("p")
            .name("n")
            .description(null)
            .funnelType("AUTO")
            .stepOrderType("ORDERED")
            .stepsJson("[{\"eventName\":\"e\"}]")
            .windowSeconds(1L)
            .mode("UNIQUE_USERS")
            .filtersJson(null)
            .dateRangeDays(7)
            .startTime(null)
            .endTime(null)
            .expiry(null)
            .createdAt(java.time.Instant.now())
            .updatedAt(null)
            .createdBy(null)
            .latestJobStatus(null)
            .totalCount(0L)
            .build();
    when(funnelDefinitionDao.listAllAuto()).thenReturn(Single.just(List.of(def)));
    when(analyticsJobDao.getLatestJobByType(AnalyticsJobType.FUNNELS_DAILY))
        .thenAnswer(
            inv -> {
              LocalDate today = LocalDate.now(ZoneOffset.UTC);
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
                      today.atTime(12, 0));
              return Maybe.just(latest);
            });

    assertThat(service.triggerFunnelsBatch().blockingGet()).isFalse();
  }

  @Test
  void triggerJourneysBatch_returnsTrueWhenNoAutoJourneys() {
    when(journeyDao.listAllAuto()).thenReturn(Single.just(List.of()));

    assertThat(service.triggerJourneysBatch().blockingGet()).isTrue();
  }

  @Test
  void triggerFunnelOnSaveJob_insertsRunningAndCompletesCompute() {
    when(analyticsJobDao.insertJob(
            eq(AnalyticsJobType.FUNNEL), eq(9L), isNull(), eq(AnalyticsJobStatus.RUNNING)))
        .thenReturn(Single.just(100L));
    when(computeService.computeFunnel(9L)).thenReturn(Single.just(true));
    when(analyticsJobDao.updateJobStatus(
            eq(100L),
            eq(AnalyticsJobStatus.SUCCEEDED),
            isNull(),
            any(LocalDateTime.class),
            any(LocalDateTime.class)))
        .thenReturn(Single.just(1));

    assertThat(service.triggerFunnelOnSaveJob(9L).blockingGet()).isTrue();
    verify(computeService).computeFunnel(9L);
  }

  @Test
  void triggerJourneyOnSaveJob_insertsRunningAndCompletesCompute() {
    when(analyticsJobDao.insertJob(
            eq(AnalyticsJobType.JOURNEY), eq(3L), isNull(), eq(AnalyticsJobStatus.RUNNING)))
        .thenReturn(Single.just(200L));
    when(computeService.computeJourney(3L)).thenReturn(Single.just(true));
    when(analyticsJobDao.updateJobStatus(
            eq(200L),
            eq(AnalyticsJobStatus.SUCCEEDED),
            isNull(),
            any(LocalDateTime.class),
            any(LocalDateTime.class)))
        .thenReturn(Single.just(1));

    assertThat(service.triggerJourneyOnSaveJob(3L).blockingGet()).isTrue();
    verify(computeService).computeJourney(3L);
  }
}
