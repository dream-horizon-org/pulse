package org.dreamhorizon.pulseserver.service.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseWriteClient;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.FunnelDefinitionDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.JourneyDao;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  ClickhouseWriteClient clickhouseWriteClient;

  ClickHouseComputeService service;

  @BeforeEach
  void setUp() {
    service = new ClickHouseComputeService(funnelDefinitionDao, journeyDao, clickhouseWriteClient);
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

    assertThat(service.computeJourneyBatch(PROJECT, List.of(start, end)).blockingGet()).isTrue();
    verify(clickhouseWriteClient, times(2)).executeSql(org.mockito.ArgumentMatchers.anyString());
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
}
