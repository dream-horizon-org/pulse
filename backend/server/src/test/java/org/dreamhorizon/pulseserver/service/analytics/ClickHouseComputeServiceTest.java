package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDateTime;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseWriteClient;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobDao;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobStatus;
import org.dreamhorizon.pulseserver.dao.analyticsjob.AnalyticsJobType;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClickHouseComputeServiceTest {

  private static final String PROJECT = "proj-x";

  @Mock
  FunnelDefinitionDao funnelDefinitionDao;

  @Mock
  JourneyDao journeyDao;

  @Mock
  AnalyticsJobDao analyticsJobDao;

  @Mock
  ClickhouseWriteClient clickhouseWriteClient;

  ClickHouseComputeService service;

  @BeforeEach
  void setUp() {
    service = new ClickHouseComputeService(
        funnelDefinitionDao, journeyDao, analyticsJobDao, clickhouseWriteClient);
  }

  /**
   * Stubs the per-item analytics_jobs lifecycle so batch tests don't have to set it up
   * individually. RUNNING insert returns id=1, status updates return rowCount=1.
   */
  private void stubAnalyticsJobLifecycle() {
    when(analyticsJobDao.insertJob(
            ArgumentMatchers.any(AnalyticsJobType.class),
            ArgumentMatchers.anyLong(),
            ArgumentMatchers.isNull(),
            ArgumentMatchers.eq(AnalyticsJobStatus.RUNNING)))
        .thenReturn(Single.just(1L));
    when(analyticsJobDao.updateJobStatus(
            ArgumentMatchers.anyLong(),
            ArgumentMatchers.any(AnalyticsJobStatus.class),
            ArgumentMatchers.any(),
            ArgumentMatchers.any(LocalDateTime.class),
            ArgumentMatchers.any(LocalDateTime.class)))
        .thenReturn(Single.just(1));
  }

  private FunnelDefinitionRow funnelRow() {
    return FunnelDefinitionRow.builder()
        .id(42L)
        .projectId(PROJECT)
        .name("F")
        .funnelType("AUTO")
        .stepOrderType("ORDERED")
        .mode("UNIQUE_USERS")
        .dateRangeDays(7)
        .windowSeconds(3600L)
        .stepsJson("[{\"eventName\":\"e1\"}]")
        .filtersJson(null)
        .build();
  }

  private JourneyRow journeyRow() {
    return journeyRowWithDirection(10L, "START");
  }

  @Test
  void computeFunnel_failsWhenNotFound() {
    when(funnelDefinitionDao.findById(1L)).thenReturn(Maybe.empty());

    assertThatThrownBy(() -> service.computeFunnel(1L).blockingGet())
        .hasMessageContaining("Funnel not found");
  }

  @Test
  void computeFunnel_executesInsertWhenSqlNonBlank() {
    when(funnelDefinitionDao.findById(42L)).thenReturn(Maybe.just(funnelRow()));
    when(clickhouseWriteClient.executeSql(anyString())).thenReturn(Single.just(true));

    assertThat(service.computeFunnel(42L).blockingGet()).isTrue();
    verify(clickhouseWriteClient).executeSql(org.mockito.ArgumentMatchers.argThat(s -> s != null && s.contains("INSERT INTO otel.funnel_results")));
  }

  @Test
  void computeJourney_failsWhenNotFound() {
    when(journeyDao.findById(1L)).thenReturn(Maybe.empty());

    assertThatThrownBy(() -> service.computeJourney(1L).blockingGet())
        .hasMessageContaining("Journey not found");
  }

  @Test
  void computeJourney_runsInsert() {
    when(journeyDao.findById(10L)).thenReturn(Maybe.just(journeyRow()));
    when(clickhouseWriteClient.executeSql(anyString())).thenReturn(Single.just(true));

    assertThat(service.computeJourney(10L).blockingGet()).isTrue();
    verify(clickhouseWriteClient).executeSql(org.mockito.ArgumentMatchers.argThat(s -> s.contains("INSERT INTO otel.journey_results")));
  }

  @Test
  void computeFunnelBatch_emptyList_returnsTrueWithoutClient() {
    assertThat(service.computeFunnelBatch(PROJECT, List.of()).blockingGet()).isTrue();
    assertThat(service.computeFunnelBatch(PROJECT, null).blockingGet()).isTrue();
  }

  @Test
  void computeJourneyBatch_splitsStartAndEnd() {
    JourneyRow start = journeyRowWithDirection(1L, "START");
    JourneyRow end = journeyRowWithDirection(2L, "END");
    when(clickhouseWriteClient.executeSql(anyString())).thenReturn(Single.just(true));
    when(journeyDao.touchUpdatedAt(ArgumentMatchers.anyLong())).thenReturn(Single.just(1));
    stubAnalyticsJobLifecycle();

    assertThat(service.computeJourneyBatch(PROJECT, List.of(start, end)).blockingGet()).isTrue();
    verify(clickhouseWriteClient, times(2)).executeSql(ArgumentMatchers.anyString());
  }

  @Test
  void computeJourneyBatch_recordsPerJourneyJobRowsWithReferenceId() {
    // Each journey in the batch must get its own analytics_jobs row with reference_id
    // set to the journey id, so the listing's latest_job_status subquery resolves.
    JourneyRow start = journeyRowWithDirection(1L, "START");
    JourneyRow end = journeyRowWithDirection(2L, "END");
    when(clickhouseWriteClient.executeSql(anyString())).thenReturn(Single.just(true));
    when(journeyDao.touchUpdatedAt(ArgumentMatchers.anyLong())).thenReturn(Single.just(1));
    stubAnalyticsJobLifecycle();

    service.computeJourneyBatch(PROJECT, List.of(start, end)).blockingGet();

    verify(analyticsJobDao).insertJob(
        ArgumentMatchers.eq(AnalyticsJobType.JOURNEY),
        ArgumentMatchers.eq(1L),
        ArgumentMatchers.isNull(),
        ArgumentMatchers.eq(AnalyticsJobStatus.RUNNING));
    verify(analyticsJobDao).insertJob(
        ArgumentMatchers.eq(AnalyticsJobType.JOURNEY),
        ArgumentMatchers.eq(2L),
        ArgumentMatchers.isNull(),
        ArgumentMatchers.eq(AnalyticsJobStatus.RUNNING));
    verify(journeyDao).touchUpdatedAt(1L);
    verify(journeyDao).touchUpdatedAt(2L);
  }

  @Test
  void computeFunnelBatch_recordsPerFunnelJobRowsWithReferenceId() {
    // Each funnel in the batch must get its own analytics_jobs row with reference_id
    // set to the funnel id, so the listing's latest_job_status subquery resolves.
    FunnelDefinitionRow f1 = FunnelDefinitionRow.builder()
        .id(7L).projectId(PROJECT).name("F1").funnelType("AUTO").stepOrderType("ORDERED")
        .mode("UNIQUE_USERS").dateRangeDays(7).windowSeconds(3600L)
        .stepsJson("[{\"eventName\":\"e1\"}]").filtersJson(null).build();
    FunnelDefinitionRow f2 = FunnelDefinitionRow.builder()
        .id(8L).projectId(PROJECT).name("F2").funnelType("AUTO").stepOrderType("ORDERED")
        .mode("UNIQUE_USERS").dateRangeDays(7).windowSeconds(3600L)
        .stepsJson("[{\"eventName\":\"e1\"}]").filtersJson(null).build();
    when(clickhouseWriteClient.executeSql(anyString())).thenReturn(Single.just(true));
    when(funnelDefinitionDao.touchUpdatedAt(ArgumentMatchers.anyLong())).thenReturn(Single.just(1));
    stubAnalyticsJobLifecycle();

    service.computeFunnelBatch(PROJECT, List.of(f1, f2)).blockingGet();

    verify(analyticsJobDao).insertJob(
        ArgumentMatchers.eq(AnalyticsJobType.FUNNEL),
        ArgumentMatchers.eq(7L),
        ArgumentMatchers.isNull(),
        ArgumentMatchers.eq(AnalyticsJobStatus.RUNNING));
    verify(analyticsJobDao).insertJob(
        ArgumentMatchers.eq(AnalyticsJobType.FUNNEL),
        ArgumentMatchers.eq(8L),
        ArgumentMatchers.isNull(),
        ArgumentMatchers.eq(AnalyticsJobStatus.RUNNING));
    verify(funnelDefinitionDao).touchUpdatedAt(7L);
    verify(funnelDefinitionDao).touchUpdatedAt(8L);
  }

  private JourneyRow journeyRowWithDirection(long id, String direction) {
    return JourneyRow.builder()
        .id(id)
        .projectId(PROJECT)
        .name("J")
        .description(null)
        .anchorEvent("anchor")
        .direction(direction)
        .depth(3)
        .mode("UNIQUE_USERS")
        .filtersJson(null)
        .startTime(null)
        .endTime(null)
        .journeyType("AUTO")
        .expiry(null)
        .dateRangeDays(7)
        .createdAt(null)
        .updatedAt(null)
        .createdBy(null)
        .latestJobStatus(null)
        .totalCount(0L)
        .build();
  }

  @Test
  void executeInsert_blankSql_skipsClient() {
    assertThat(service.executeInsert(PROJECT, "  ").blockingGet()).isTrue();
    assertThat(service.executeInsert(PROJECT, null).blockingGet()).isTrue();
  }

  @Test
  void deleteFunnelResults_cascadesAcrossResultsAndDropoffBridgeTables() {
    // FunnelService.delete depends on this cascade — without it, drop-off bridge rows for
    // a deleted funnel keep serving the panel until 90d TTL, and re-creating a funnel that
    // reuses the same id would mix old + new rows.
    when(clickhouseWriteClient.executeSql(anyString())).thenReturn(Single.just(true));

    assertThat(service.deleteFunnelResults(PROJECT, 42L).blockingGet()).isTrue();

    verify(clickhouseWriteClient).executeSql(org.mockito.ArgumentMatchers.argThat(
        s -> s.contains("DELETE FROM otel.funnel_results")
            && s.contains("ProjectId = 'proj-x'")
            && s.contains("FunnelId = 42")));
    verify(clickhouseWriteClient).executeSql(org.mockito.ArgumentMatchers.argThat(
        s -> s.contains("DELETE FROM otel.funnel_session_state")
            && s.contains("FunnelId = 42")));
    verify(clickhouseWriteClient).executeSql(org.mockito.ArgumentMatchers.argThat(
        s -> s.contains("DELETE FROM otel.funnel_user_state")
            && s.contains("FunnelId = 42")));
    verify(clickhouseWriteClient).executeSql(org.mockito.ArgumentMatchers.argThat(
        s -> s.contains("DELETE FROM otel.funnel_dropoff_attribution")
            && s.contains("FunnelId = 42")));
  }

  @Test
  void deleteFunnelResults_swallowsBridgeFailureWhenPrimaryDeleteSucceeds() {
    // Primary funnel_results delete must succeed independently — bridge cleanup failures
    // are best-effort and shouldn't surface to FunnelService.delete.
    when(clickhouseWriteClient.executeSql(org.mockito.ArgumentMatchers.argThat(
        s -> s.contains("DELETE FROM otel.funnel_results"))))
        .thenReturn(Single.just(true));
    when(clickhouseWriteClient.executeSql(org.mockito.ArgumentMatchers.argThat(
        s -> s.contains("DELETE FROM otel.funnel_session_state"))))
        .thenReturn(Single.error(new RuntimeException("bridge ch boom")));

    assertThat(service.deleteFunnelResults(PROJECT, 42L).blockingGet()).isTrue();
  }
}
