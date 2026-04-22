package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.dreamhorizon.pulseserver.config.SparkConfig;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobDao;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobEntity;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobStatus;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType;
import org.dreamhorizon.pulseserver.service.spark.SparkJobService;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobRequest;
import org.dreamhorizon.pulseserver.service.spark.models.SparkJobResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsBatchServiceImplTest {

  @Mock
  SparkConfig sparkConfig;

  @Mock
  SparkJobService sparkJobService;

  @Mock
  AnalyticsJobDao analyticsJobDao;

  AnalyticsBatchServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new AnalyticsBatchServiceImpl(sparkConfig, sparkJobService, analyticsJobDao);
    lenient().when(sparkConfig.getJobJarPath()).thenReturn("/jar.jar");
    lenient().when(sparkConfig.getFunnelsMainClass()).thenReturn("Funnels");
    lenient().when(sparkConfig.getJourneysMainClass()).thenReturn("Journeys");
    lenient().when(sparkConfig.getEventsMainClass()).thenReturn("Events");
  }

  @Test
  void triggerFunnelsBatch_skipsWhenJobAlreadyRanToday() {
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
    when(analyticsJobDao.getLatestJobByType(AnalyticsJobType.FUNNELS_DAILY))
        .thenReturn(Maybe.just(latest));

    assertThat(service.triggerFunnelsBatch().blockingGet()).isFalse();
    verify(sparkJobService, never()).submitJob(any());
  }

  @Test
  void triggerFunnelsBatch_submitsWhenNoJobToday() {
    when(analyticsJobDao.getLatestJobByType(AnalyticsJobType.FUNNELS_DAILY))
        .thenReturn(Maybe.empty());
    when(analyticsJobDao.insertJob(
            eq(AnalyticsJobType.FUNNELS_DAILY), isNull(), isNull(), eq(AnalyticsJobStatus.PENDING)))
        .thenReturn(Single.just(50L));
    when(sparkJobService.submitJob(any(SparkJobRequest.class)))
        .thenReturn(
            Single.just(
                SparkJobResponse.builder().jobRunId("run-1").applicationId("app").build()));
    when(analyticsJobDao.updateJobIdAndStatus(eq(50L), eq("run-1"), eq(AnalyticsJobStatus.SUBMITTED)))
        .thenReturn(Single.just(1));

    assertThat(service.triggerFunnelsBatch().blockingGet()).isTrue();

    ArgumentCaptor<SparkJobRequest> cap = ArgumentCaptor.forClass(SparkJobRequest.class);
    verify(sparkJobService).submitJob(cap.capture());
    assertThat(cap.getValue().getMainClass()).isEqualTo("Funnels");
  }

  @Test
  void triggerEventsBatch_delegatesToDailyJob() {
    when(analyticsJobDao.getLatestJobByType(AnalyticsJobType.EVENTS_INCREMENTAL))
        .thenReturn(Maybe.empty());
    when(analyticsJobDao.insertJob(
            eq(AnalyticsJobType.EVENTS_INCREMENTAL), isNull(), isNull(), eq(AnalyticsJobStatus.PENDING)))
        .thenReturn(Single.just(60L));
    when(sparkJobService.submitJob(any(SparkJobRequest.class)))
        .thenReturn(
            Single.just(
                SparkJobResponse.builder().jobRunId("run-2").applicationId("app").build()));
    when(analyticsJobDao.updateJobIdAndStatus(eq(60L), eq("run-2"), eq(AnalyticsJobStatus.SUBMITTED)))
        .thenReturn(Single.just(1));

    assertThat(service.triggerEventsBatch().blockingGet()).isTrue();
  }

  @Test
  void triggerFunnelOnSaveJob_submitsSparkJob() {
    when(analyticsJobDao.insertJob(
            eq(AnalyticsJobType.FUNNEL), eq(7L), isNull(), eq(AnalyticsJobStatus.PENDING)))
        .thenReturn(Single.just(70L));
    when(sparkJobService.submitJob(any(SparkJobRequest.class)))
        .thenReturn(
            Single.just(
                SparkJobResponse.builder().jobRunId("run-3").applicationId("app").build()));
    when(analyticsJobDao.updateJobIdAndStatus(eq(70L), eq("run-3"), eq(AnalyticsJobStatus.SUBMITTED)))
        .thenReturn(Single.just(1));

    assertThat(service.triggerFunnelOnSaveJob(7L).blockingGet()).isTrue();
  }
}
