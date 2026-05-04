package org.dreamhorizon.pulseserver.service.productAnalysis.funnel.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag.FunnelJourneyTagDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneljourneytag.FunnelJourneyTagEntityType;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.FunnelResultsDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funnelresults.models.FunnelResultRow;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.CreateFunnelDefinitionRequest;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelDefinitionStep;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelListQueryParams;
import org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models.FunnelType;
import org.dreamhorizon.pulseserver.resources.productAnalysis.models.FunnelJourneyTagsListResponse;
import org.dreamhorizon.pulseserver.service.analytics.AnalyticsBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FunnelServiceImplTest {

  private static final String PROJECT = "proj-1";

  @Mock
  FunnelDefinitionDao funnelDefinitionDao;

  @Mock
  FunnelJourneyTagDao funnelJourneyTagDao;

  @Mock
  FunnelResultsDao funnelResultsDao;

  @Mock
  AnalyticsBatchService analyticsBatchService;

  @Mock
  org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobDao analyticsJobDao;

  @Mock
  org.dreamhorizon.pulseserver.service.analytics.ClickHouseComputeService clickHouseComputeService;

  FunnelServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new FunnelServiceImpl(
            funnelDefinitionDao,
            funnelJourneyTagDao,
            funnelResultsDao,
            analyticsBatchService,
            analyticsJobDao,
            clickHouseComputeService);
  }

  private CreateFunnelDefinitionRequest validCreateRequest() {
    return CreateFunnelDefinitionRequest.builder()
        .name(" My Funnel ")
        .steps(List.of(FunnelDefinitionStep.builder().eventName("e1").build()))
        .build();
  }

  private FunnelDefinitionRow storedRow() {
    return FunnelDefinitionRow.builder()
        .id(10L)
        .projectId(PROJECT)
        .name("My Funnel")
        .description(null)
        .funnelType("AUTO")
        .stepOrderType("ORDERED")
        .stepsJson("[{\"eventName\":\"e1\"}]")
        .windowSeconds(86400L)
        .mode("UNIQUE_USERS")
        .filtersJson(null)
        .dateRangeDays(7)
        .startTime(null)
        .endTime(null)
        .expiry(null)
        .createdAt(Instant.parse("2025-01-01T00:00:00Z"))
        .updatedAt(Instant.parse("2025-01-02T00:00:00Z"))
        .createdBy("u@x.com")
        .latestJobStatus("SUCCEEDED")
        .totalCount(0L)
        .build();
  }

  @Test
  void create_persistsAndTriggersJob() {
    when(funnelDefinitionDao.insert(any(FunnelDefinitionRow.class))).thenReturn(Single.just(5L));
    when(funnelJourneyTagDao.replaceTags(
            eq(PROJECT), eq(FunnelJourneyTagEntityType.FUNNEL), eq(5L), eq(List.of())))
        .thenReturn(Completable.complete());
    when(analyticsBatchService.triggerFunnelOnSaveJob(5L)).thenReturn(Single.just(true));

    assertThat(service.create(PROJECT, validCreateRequest(), "u@x.com").blockingGet()).isEqualTo(5L);
    verify(analyticsBatchService).triggerFunnelOnSaveJob(5L);
  }

  @Test
  void create_rejectsEmptySteps() {
    CreateFunnelDefinitionRequest req =
        CreateFunnelDefinitionRequest.builder().name("x").steps(List.of()).build();

    assertThatThrownBy(() -> service.create(PROJECT, req, "u").blockingGet()).isNotNull();
  }

  @Test
  void listDistinctTags_mapsResponse() {
    when(funnelJourneyTagDao.listDistinctTagsForProject(PROJECT))
        .thenReturn(Single.just(List.of("alpha", "beta")));

    FunnelJourneyTagsListResponse resp = service.listDistinctTags(PROJECT).blockingGet();
    assertThat(resp.getTags()).containsExactly("alpha", "beta");
  }

  @Test
  void list_returnsEmptyWhenNoFunnels() {
    when(funnelDefinitionDao.listByProject(eq(PROJECT), any())).thenReturn(Single.just(List.of()));
    when(funnelDefinitionDao.listDistinctCreatedBy(PROJECT)).thenReturn(Single.just(List.of()));
    when(funnelJourneyTagDao.listDistinctTagsForProject(PROJECT)).thenReturn(Single.just(List.of()));

    FunnelListQueryParams q = new FunnelListQueryParams();
    q.setPage(1);
    q.setPageSize(10);

    var resp = service.list(PROJECT, q).blockingGet();
    assertThat(resp.getItems()).isEmpty();
    assertThat(resp.getTotalCount()).isZero();
  }

  @Test
  void get_loadsFunnelAndResults() {
    when(funnelDefinitionDao.findByProjectAndId(PROJECT, 10L))
        .thenReturn(Maybe.just(storedRow()));
    when(funnelResultsDao.queryLatest(PROJECT, 10L)).thenReturn(Single.just(Collections.emptyList()));
    when(funnelJourneyTagDao.listTagsForEntity(
            PROJECT, FunnelJourneyTagEntityType.FUNNEL, 10L))
        .thenReturn(Single.just(List.of("t1")));

    var resp = service.get(PROJECT, 10L).blockingGet();
    assertThat(resp.getId()).isEqualTo(10L);
    assertThat(resp.getTags()).containsExactly("t1");
  }

  @Test
  void get_setsLastRunAtFromResults() {
    Instant runTime = Instant.parse("2026-05-01T10:00:00Z");
    when(funnelDefinitionDao.findByProjectAndId(PROJECT, 10L))
        .thenReturn(Maybe.just(storedRow()));
    when(funnelResultsDao.queryLatest(PROJECT, 10L))
        .thenReturn(Single.just(List.of(
            FunnelResultRow.builder()
                .stepIndex(0).stepName("A").userCount(100L).conversionPct(100.0)
                .runTime(runTime).build())));
    when(funnelJourneyTagDao.listTagsForEntity(PROJECT, FunnelJourneyTagEntityType.FUNNEL, 10L))
        .thenReturn(Single.just(List.of()));

    assertThat(service.get(PROJECT, 10L).blockingGet().getLastRunAt()).isEqualTo(runTime);
  }

  @Test
  void get_throwsWhenMissing() {
    when(funnelDefinitionDao.findByProjectAndId(PROJECT, 99L)).thenReturn(Maybe.empty());

    assertThatThrownBy(() -> service.get(PROJECT, 99L).blockingGet()).isNotNull();
  }

  @Test
  void delete_removesTagsAndRow() {
    when(funnelJourneyTagDao.deleteAllForEntity(
            PROJECT, FunnelJourneyTagEntityType.FUNNEL, 3L))
        .thenReturn(Completable.complete());
    when(funnelDefinitionDao.delete(PROJECT, 3L)).thenReturn(Single.just(1));
    // Cascading delete: analytics_jobs cleanup + ClickHouse results cleanup. Both are
    // best-effort post-deletion side effects; stub success so the chain completes.
    when(analyticsJobDao.deleteByReference(
            org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType.FUNNEL, 3L))
        .thenReturn(Single.just(0));
    when(clickHouseComputeService.deleteFunnelResults(PROJECT, 3L))
        .thenReturn(Single.just(true));

    service.delete(PROJECT, 3L).blockingAwait();
    verify(funnelDefinitionDao).delete(PROJECT, 3L);
    verify(analyticsJobDao).deleteByReference(
        org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType.FUNNEL, 3L);
    verify(clickHouseComputeService).deleteFunnelResults(PROJECT, 3L);
  }
}
