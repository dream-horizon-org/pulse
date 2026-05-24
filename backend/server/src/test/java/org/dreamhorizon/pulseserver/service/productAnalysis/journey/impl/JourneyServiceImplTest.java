package org.dreamhorizon.pulseserver.service.productAnalysis.journey.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.config.AnalyticsEngineConfig;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag.FunnelJourneyTagDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.models.JourneyResultRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag.FunnelJourneyTagEntityType;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journeyresults.JourneyResultsDao;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.AnalysisBasis;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.CreateJourneyRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.journey.models.JourneyListQueryParams;
import org.dreamhorizon.pulseserver.service.analytics.AnalyticsBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JourneyServiceImplTest {

  private static final String PROJECT = "proj-j";

  @Mock
  JourneyDao journeyDao;

  @Mock
  FunnelJourneyTagDao funnelJourneyTagDao;

  @Mock
  JourneyResultsDao journeyResultsDao;

  @Mock
  AnalyticsBatchService analyticsBatchService;

  @Mock
  org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobDao analyticsJobDao;

  @Mock
  org.dreamhorizon.pulseserver.service.analytics.ClickHouseComputeService clickHouseComputeService;

  @Mock
  AnalyticsEngineConfig analyticsEngineConfig;

  JourneyServiceImpl service;

  @BeforeEach
  void setUp() {
    lenient().when(analyticsEngineConfig.isClickHouseEngine()).thenReturn(true);
    service =
        new JourneyServiceImpl(
            journeyDao,
            funnelJourneyTagDao,
            journeyResultsDao,
            analyticsBatchService,
            analyticsJobDao,
            clickHouseComputeService,
            analyticsEngineConfig);
  }

  private CreateJourneyRequest validCreateRequest() {
    return CreateJourneyRequest.builder().name(" J ").anchorEvent(" open ").build();
  }

  private JourneyRow storedRow() {
    return JourneyRow.builder()
        .id(20L)
        .projectId(PROJECT)
        .name("J")
        .description(null)
        .anchorEvent("open")
        .direction("START")
        .depth(5)
        .mode("UNIQUE_USERS")
        .filtersJson(null)
        .startTime(null)
        .endTime(null)
        .journeyType("AUTO")
        .expiry(null)
        .dateRangeDays(7)
        .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
        .updatedAt(Instant.parse("2025-01-02T00:00:00Z"))
        .createdBy("u@x.com")
        .latestJobStatus("SUCCEEDED")
        .totalCount(0L)
        .build();
  }

  @Test
  void create_persistsAndTriggersJob() {
    when(journeyDao.insert(any(JourneyRow.class))).thenReturn(Single.just(8L));
    when(funnelJourneyTagDao.replaceTags(
            eq(PROJECT), eq(FunnelJourneyTagEntityType.JOURNEY), eq(8L), eq(List.of())))
        .thenReturn(Completable.complete());
    when(analyticsBatchService.triggerJourneyOnSaveJob(8L)).thenReturn(Single.just(true));

    assertThat(service.create(PROJECT, validCreateRequest(), "u@x.com").blockingGet()).isEqualTo(8L);
    verify(analyticsBatchService).triggerJourneyOnSaveJob(8L);
  }

  @Test
  void list_returnsEmptyWhenNoJourneys() {
    when(journeyDao.listByProject(eq(PROJECT), any())).thenReturn(Single.just(List.of()));
    when(journeyDao.listDistinctCreatedBy(PROJECT)).thenReturn(Single.just(List.of()));
    when(funnelJourneyTagDao.listDistinctTagsForProject(PROJECT)).thenReturn(Single.just(List.of()));

    JourneyListQueryParams q = new JourneyListQueryParams();
    q.setPage(1);
    q.setPageSize(10);

    var resp = service.list(PROJECT, q).blockingGet();
    assertThat(resp.getItems()).isEmpty();
  }

  @Test
  void get_loadsJourneyAndGraph() {
    JourneyRow row = storedRow();
    when(journeyDao.findByProjectAndId(PROJECT, 20L)).thenReturn(Maybe.just(row));
    when(journeyResultsDao.queryLatest(PROJECT, 20L, "START"))
        .thenReturn(Single.just(Collections.emptyList()));
    when(funnelJourneyTagDao.listTagsForEntity(
            PROJECT, FunnelJourneyTagEntityType.JOURNEY, 20L))
        .thenReturn(Single.just(List.of()));

    var resp = service.get(PROJECT, 20L).blockingGet();
    assertThat(resp.getId()).isEqualTo(20L);
  }

  @Test
  void get_setsLastRunAtFromResults() {
    Instant runTime = Instant.parse("2026-05-01T10:00:00Z");
    when(journeyDao.findByProjectAndId(PROJECT, 20L)).thenReturn(Maybe.just(storedRow()));
    when(journeyResultsDao.queryLatest(PROJECT, 20L, "START"))
        .thenReturn(Single.just(List.of(
            JourneyResultRow.builder()
                .direction("START").posFrom(-1).eventFrom("").posTo(0).eventTo("App_Launch")
                .userCount(100L).runTime(runTime).build())));
    when(funnelJourneyTagDao.listTagsForEntity(PROJECT, FunnelJourneyTagEntityType.JOURNEY, 20L))
        .thenReturn(Single.just(List.of()));

    assertThat(service.get(PROJECT, 20L).blockingGet().getLastRunAt()).isEqualTo(runTime);
  }

  @Test
  void get_throwsWhenMissing() {
    when(journeyDao.findByProjectAndId(PROJECT, 1L)).thenReturn(Maybe.empty());

    assertThatThrownBy(() -> service.get(PROJECT, 1L).blockingGet()).isNotNull();
  }

  @Test
  void delete_removesTagsAndRow() {
    when(funnelJourneyTagDao.deleteAllForEntity(
            PROJECT, FunnelJourneyTagEntityType.JOURNEY, 4L))
        .thenReturn(Completable.complete());
    when(journeyDao.delete(PROJECT, 4L)).thenReturn(Single.just(1));
    when(analyticsJobDao.deleteByReference(
            org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType.JOURNEY, 4L))
        .thenReturn(Single.just(0));
    when(clickHouseComputeService.deleteJourneyResults(PROJECT, 4L))
        .thenReturn(Single.just(true));

    service.delete(PROJECT, 4L).blockingAwait();
    verify(journeyDao).delete(PROJECT, 4L);
    verify(analyticsJobDao).deleteByReference(
        org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType.JOURNEY, 4L);
    verify(clickHouseComputeService).deleteJourneyResults(PROJECT, 4L);
  }
}
