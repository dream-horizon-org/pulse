package org.dreamhorizon.pulseserver.service.errorattribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.service.errorattribution.ErrorAttributionResult.RiskRatioRow;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ErrorAttributionServiceTest {

  private static final String PROJECT = "p1";
  private static final String INTERACTION = "tap_pay";
  private static final Instant START = Instant.parse("2026-04-01T00:00:00Z");
  private static final Instant END = Instant.parse("2026-04-08T15:00:00Z");
  private static final LocalDate ANCHOR = LocalDate.of(2026, 4, 8);

  @Mock private RootCauseConfig rootCauseConfig;
  @Mock private ClickhouseQueryService clickhouseQueryService;
  @Mock private RootCauseCacheDao rootCauseCacheDao;
  @Mock private ErrorAttributionDrillDownService errorAttributionDrillDownService;

  private ErrorAttributionService service;

  @BeforeEach
  void setUp() {
    lenient().when(rootCauseConfig.getLookbackDays()).thenReturn(8);
    lenient()
        .when(rootCauseConfig.getMinPoorSessionsForErrorAttribution())
        .thenReturn(RootCauseConfig.DEFAULT_MIN_POOR_SESSIONS_FOR_ERROR_ATTRIBUTION);
    lenient()
        .when(rootCauseCacheDao.findByKey(anyString(), anyString(), any(LocalDate.class)))
        .thenReturn(Single.just(Optional.empty()));
    lenient()
        .when(rootCauseCacheDao.upsertPreservingRcaRow(any(), anyString(), any()))
        .thenReturn(Completable.complete());
    service =
        new ErrorAttributionService(
            clickhouseQueryService,
            rootCauseCacheDao,
            new ObjectMapperUtil(),
            rootCauseConfig,
            errorAttributionDrillDownService);
  }

  private static Map<String, Object> baseCountsRow() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("n_u", 2000L);
    m.put("n_poor_u", 1500L);
    m.put("n_treated_crash", 0L);
    m.put("n_control_crash", 2000L);
    m.put("n_treated_low_crash", 0L);
    m.put("n_control_low_crash", 100L);
    m.put("n_treated_anr", 0L);
    m.put("n_control_anr", 2000L);
    m.put("n_treated_low_anr", 0L);
    m.put("n_control_low_anr", 100L);
    m.put("n_treated_nf", 0L);
    m.put("n_control_nf", 2000L);
    m.put("n_treated_low_nf", 0L);
    m.put("n_control_low_nf", 100L);
    m.put("n_treated_api", 0L);
    m.put("n_control_api", 2000L);
    m.put("n_treated_low_api", 0L);
    m.put("n_control_low_api", 100L);
    return m;
  }

  private static GetQueryDataResponseDto<GetRawUserEventsResponseDto> responseWithRow(
      Map<String, Object> rowMap) {
    List<String> keys = new ArrayList<>(rowMap.keySet());
    List<GetRawUserEventsResponseDto.Field> fields = new ArrayList<>();
    for (String key : keys) {
      GetRawUserEventsResponseDto.Field f = new GetRawUserEventsResponseDto.Field();
      f.setName(key);
      fields.add(f);
    }
    List<GetRawUserEventsResponseDto.RowField> rowFields = new ArrayList<>();
    for (String key : keys) {
      GetRawUserEventsResponseDto.RowField rf = new GetRawUserEventsResponseDto.RowField();
      rf.setValue(rowMap.get(key));
      rowFields.add(rf);
    }
    GetRawUserEventsResponseDto.Row row = new GetRawUserEventsResponseDto.Row();
    row.setRowFields(rowFields);
    GetRawUserEventsResponseDto.Schema schema = new GetRawUserEventsResponseDto.Schema(fields);
    GetRawUserEventsResponseDto data =
        GetRawUserEventsResponseDto.builder()
            .schema(schema)
            .rows(List.of(row))
            .build();
    return GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
        .jobComplete(true)
        .data(data)
        .build();
  }

  @Nested
  class EmptyClickHouse {

    @Test
    void shouldReturnInsufficientDataWithEmptyTreatedArmsWhenNoRows() {
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(
              Single.just(
                  GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
                      .jobComplete(true)
                      .data(
                          GetRawUserEventsResponseDto.builder()
                              .schema(new GetRawUserEventsResponseDto.Schema(List.of()))
                              .rows(List.of())
                              .build())
                      .build()));

      ErrorAttributionResult r =
          service.getErrorAttribution(PROJECT, INTERACTION, START, END, true).blockingGet();

      assertThat(r.getNU()).isZero();
      assertThat(r.getNPoorInU()).isZero();
      assertThat(r.getTrackBInsufficientData()).isTrue();
      assertThat(r.getJointWinners()).isNull();
      assertThat(r.getRiskRatios()).hasSize(4);
      assertThat(r.getRiskRatios().get(0).getRrUndefinedReason())
          .isEqualTo(ErrorAttributionResult.RR_EMPTY_TREATED_ARM);
      assertThat(r.getRiskRatios().get(0).getP1()).isNull();
      assertThat(r.getCachedAt()).isNull();
      assertThat(r.getDisclaimer()).isEqualTo(ErrorAttributionService.DISCLAIMER);
    }
  }

  @Nested
  class RiskRatioMatrix {

    @Test
    void shouldClassifyEmptyControlBeforeInfiniteRr() {
      Map<String, Object> row = baseCountsRow();
      row.put("n_treated_crash", 5L);
      row.put("n_control_crash", 0L);
      row.put("n_treated_low_crash", 3L);
      row.put("n_control_low_crash", 0L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(Single.just(responseWithRow(row)));

      ErrorAttributionResult r =
          service.getErrorAttribution(PROJECT, INTERACTION, START, END, true).blockingGet();

      RiskRatioRow crash = r.getRiskRatios().get(0);
      assertThat(crash.getRrUndefinedReason()).isEqualTo(ErrorAttributionResult.RR_EMPTY_CONTROL_ARM);
    }

    @Test
    void shouldEmitInfiniteRrReasonWhenP2ZeroAndP1Positive() {
      Map<String, Object> row = baseCountsRow();
      row.put("n_treated_crash", 10L);
      row.put("n_control_crash", 4L);
      row.put("n_treated_low_crash", 2L);
      row.put("n_control_low_crash", 0L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(Single.just(responseWithRow(row)));

      ErrorAttributionResult r =
          service.getErrorAttribution(PROJECT, INTERACTION, START, END, true).blockingGet();

      RiskRatioRow crash = r.getRiskRatios().get(0);
      assertThat(crash.getRrUndefinedReason()).isEqualTo(ErrorAttributionResult.RR_INFINITE_RR);
      assertThat(crash.getRr()).isNull();
      assertThat(r.getJointWinners()).containsExactly("crash");
    }

    @Test
    void shouldTieJointWinnersAfterFourDpRounding() {
      Map<String, Object> row = baseCountsRow();
      // crash: 0.12/0.1 = 1.2
      row.put("n_treated_crash", 100L);
      row.put("n_control_crash", 100L);
      row.put("n_treated_low_crash", 12L);
      row.put("n_control_low_crash", 10L);
      // anr: 0.12/0.1 = 1.2 (scaled counts)
      row.put("n_treated_anr", 200L);
      row.put("n_control_anr", 200L);
      row.put("n_treated_low_anr", 24L);
      row.put("n_control_low_anr", 20L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(Single.just(responseWithRow(row)));

      ErrorAttributionResult r =
          service.getErrorAttribution(PROJECT, INTERACTION, START, END, true).blockingGet();

      assertThat(r.getNPoorInU()).isGreaterThanOrEqualTo(1000L);
      assertThat(r.getJointWinners()).containsExactlyInAnyOrder("crash", "anr");
    }

    @Test
    void shouldOmitJointWinnersWhenPoorGateFails() {
      Map<String, Object> row = baseCountsRow();
      row.put("n_poor_u", 10L);
      row.put("n_treated_crash", 100L);
      row.put("n_control_crash", 100L);
      row.put("n_treated_low_crash", 50L);
      row.put("n_control_low_crash", 10L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(Single.just(responseWithRow(row)));

      ErrorAttributionResult r =
          service.getErrorAttribution(PROJECT, INTERACTION, START, END, true).blockingGet();

      assertThat(r.getTrackBInsufficientData()).isTrue();
      assertThat(r.getJointWinners()).isNull();
      assertThat(r.getMinPoorSessionsForErrorAttribution())
          .isEqualTo(RootCauseConfig.DEFAULT_MIN_POOR_SESSIONS_FOR_ERROR_ATTRIBUTION);
    }
  }

  @Nested
  class CacheWrite {

    @Test
    void shouldSkipPreservingUpsertWhenNoRcaRowExists() throws Exception {
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(
              Single.just(
                  GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
                      .jobComplete(true)
                      .data(
                          GetRawUserEventsResponseDto.builder()
                              .schema(new GetRawUserEventsResponseDto.Schema(List.of()))
                              .rows(List.of())
                              .build())
                      .build()));
      when(rootCauseCacheDao.findByKey(anyString(), anyString(), any(LocalDate.class)))
          .thenReturn(Single.just(Optional.empty()));

      service.getErrorAttribution(PROJECT, INTERACTION, START, END, true).blockingGet();

      Thread.sleep(300);
      verify(rootCauseCacheDao, never()).upsertPreservingRcaRow(any(), anyString(), any());
    }

    @Test
    void shouldCallPreservingUpsertWhenRcaRowExistsAfterCompute() throws Exception {
      RootCauseCacheRow existing =
          RootCauseCacheRow.builder()
              .projectId(PROJECT)
              .interactionName(INTERACTION)
              .date(ANCHOR)
              .windowEndUtc(END.atZone(ZoneOffset.UTC).toLocalDateTime())
              .mode("flat")
              .baseline("{}")
              .segments("[]")
              .build();
      Map<String, Object> row = baseCountsRow();
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(Single.just(responseWithRow(row)));
      when(rootCauseCacheDao.findByKey(anyString(), anyString(), any(LocalDate.class)))
          .thenReturn(Single.just(Optional.of(existing)));

      service.getErrorAttribution(PROJECT, INTERACTION, START, END, true).blockingGet();

      Thread.sleep(300);
      ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
      verify(rootCauseCacheDao).upsertPreservingRcaRow(eq(existing), jsonCaptor.capture(), any());
      assertThat(jsonCaptor.getValue()).contains("\"diagnosticSpecVersion\":\"1\"");
      assertThat(jsonCaptor.getValue()).contains("\"cachedAt\":");
    }
  }

  @Nested
  class ReadThroughCache {

    @Test
    void shouldReturnCachedJsonWithoutClickHouseWhenValid() {
      ErrorAttributionResult cachedBody =
          ErrorAttributionResult.builder()
              .trackBInsufficientData(false)
              .nU(100L)
              .nPoorInU(2000L)
              .riskRatios(List.of())
              .jointWinners(List.of("crash"))
              .analysisPhase("1")
              .track("B")
              .diagnosticSpecVersion(ErrorAttributionService.SPEC_VERSION)
              .disclaimer(ErrorAttributionService.DISCLAIMER)
              .cachedAt(Instant.parse("2026-04-08T10:00:00Z"))
              .build();
      String json = new ObjectMapperUtil().writeValueAsString(cachedBody);
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .projectId(PROJECT)
              .interactionName(INTERACTION)
              .date(ANCHOR)
              .windowEndUtc(END.atZone(ZoneOffset.UTC).toLocalDateTime())
              .errorAttributionJson(json)
              .build();
      when(rootCauseCacheDao.findByKey(eq(PROJECT), eq(INTERACTION), eq(ANCHOR)))
          .thenReturn(Single.just(Optional.of(row)));

      ErrorAttributionResult r =
          service.getErrorAttribution(PROJECT, INTERACTION, START, END, false).blockingGet();

      assertThat(r.getCachedAt()).isEqualTo(cachedBody.getCachedAt());
      verify(clickhouseQueryService, never()).executeRootCauseQuery(anyString(), anyString(), anyList(), anyList());
    }
  }
}
