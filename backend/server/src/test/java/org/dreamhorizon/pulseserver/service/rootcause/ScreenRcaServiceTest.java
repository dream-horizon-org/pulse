package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.ws.rs.WebApplicationException;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.ScreenRootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.ScreenRootCauseCacheRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperFactory;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScreenRcaServiceTest {

  private static final String PROJECT_ID = "project-1";
  private static final String SCREEN = "Checkout";
  private static final LocalDate ANCHOR = LocalDate.of(2025, 3, 10);
  private static final Instant WINDOW_END = Instant.parse("2025-03-10T14:00:00Z");
  private static final LocalDateTime WINDOW_END_LDT =
      LocalDateTime.ofInstant(WINDOW_END, ZoneOffset.UTC);

  @Mock
  private RootCauseConfig rootCauseConfig;

  @Mock
  private ClickhouseQueryService clickhouseQueryService;

  @Mock
  private ScreenRootCauseCacheDao screenRootCauseCacheDao;

  private ScreenRcaService service;

  @BeforeEach
  void setUp() {
    lenient().when(rootCauseConfig.getLookbackDays()).thenReturn(7);
    lenient().when(rootCauseConfig.getSimilarityThresholdPct()).thenReturn(75);
    lenient().when(rootCauseConfig.getMaxSegments()).thenReturn(4);
    lenient()
        .when(rootCauseConfig.getDimensionOrder())
        .thenReturn(List.of("Platform", "OsVersion", "AppVersion"));
    lenient().when(rootCauseConfig.isHybridDimensionOrderingEnabled()).thenReturn(false);
    lenient()
        .when(screenRootCauseCacheDao.upsert(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Completable.complete());
    service =
        new ScreenRcaService(
            rootCauseConfig,
            clickhouseQueryService,
            screenRootCauseCacheDao,
            new ObjectMapperUtil(ObjectMapperFactory.get()));
  }

  private static GetQueryDataResponseDto<GetRawUserEventsResponseDto> emptyTableResponse() {
    GetRawUserEventsResponseDto.Schema schema =
        new GetRawUserEventsResponseDto.Schema(new ArrayList<>());
    GetRawUserEventsResponseDto data =
        GetRawUserEventsResponseDto.builder().schema(schema).rows(List.of()).build();
    return GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
        .jobComplete(true)
        .data(data)
        .build();
  }

  private static GetQueryDataResponseDto<GetRawUserEventsResponseDto> singleRowTableResponse(
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

  private static Map<String, Object> screenBaseline(long volume, long badFrustration) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put(ScreenRcaQueryBuilder.CLICK_VOLUME, volume);
    m.put(ScreenRcaQueryBuilder.TAP_COUNT, Math.max(0, volume - badFrustration));
    m.put(ScreenRcaQueryBuilder.RAGE_COUNT, badFrustration / 2);
    m.put(ScreenRcaQueryBuilder.DEAD_COUNT, badFrustration - badFrustration / 2);
    m.put(ScreenRcaQueryBuilder.BAD_FRUSTRATION, badFrustration);
    return m;
  }

  private static Map<String, Object> screenSegmentMetricRow() {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put(ScreenRcaQueryBuilder.CLICK_VOLUME, 50L);
    row.put(ScreenRcaQueryBuilder.TAP_COUNT, 40L);
    row.put(ScreenRcaQueryBuilder.RAGE_COUNT, 5L);
    row.put(ScreenRcaQueryBuilder.DEAD_COUNT, 5L);
    row.put(ScreenRcaQueryBuilder.BAD_FRUSTRATION, 50L);
    return row;
  }

  @Nested
  class WindowValidation {

    @Test
    void shouldMapIllegalLookbackToBadRequest() {
      when(rootCauseConfig.getLookbackDays()).thenReturn(0);

      assertThatThrownBy(
              () -> service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet())
          .isInstanceOf(WebApplicationException.class)
          .satisfies(
              t -> {
                WebApplicationException wae = (WebApplicationException) t;
                assertThat(wae.getResponse().getStatus())
                    .isEqualTo(ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getHttpStatusCode());
              });
      verify(clickhouseQueryService, never())
          .executeRootCauseQuery(anyString(), anyString(), anyList(), anyList());
    }

    @Test
    void shouldMapEndBeforeWindowStartToBadRequest() {
      Instant endBeforeStart = Instant.parse("2025-03-03T23:00:00Z");

      assertThatThrownBy(
              () -> service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, endBeforeStart).blockingGet())
          .isInstanceOf(WebApplicationException.class);

      verify(clickhouseQueryService, never())
          .executeRootCauseQuery(anyString(), anyString(), anyList(), anyList());
    }
  }

  @Nested
  class CacheBehavior {

    @Test
    void shouldReturnDeserializedResultWhenCacheHit() {
      ScreenRootCauseCacheRow row =
          ScreenRootCauseCacheRow.builder()
              .projectId(PROJECT_ID)
              .screenName(SCREEN)
              .date(ANCHOR)
              .baseline("{\"click_volume\":10,\"tap_count\":9,\"rage_count\":0,\"dead_count\":1,\"bad_frustration\":1}")
              .segments("[]")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(screenRootCauseCacheDao.findByKey(PROJECT_ID, SCREEN, ANCHOR))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getBaseline()).containsEntry(ScreenRcaQueryBuilder.CLICK_VOLUME, 10);
      assertThat(result.getSegments()).isEmpty();
      verify(clickhouseQueryService, never())
          .executeRootCauseQuery(anyString(), anyString(), anyList(), anyList());
    }

    @Test
    void shouldTreatBlankBaselineAndSegmentsInCacheAsEmptyCollections() {
      ScreenRootCauseCacheRow row =
          ScreenRootCauseCacheRow.builder()
              .baseline("   ")
              .segments("")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(screenRootCauseCacheDao.findByKey(PROJECT_ID, SCREEN, ANCHOR))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getBaseline()).isEmpty();
      assertThat(result.getSegments()).isEmpty();
    }

    @Test
    void shouldRecomputeWhenBaselineJsonInCacheIsInvalid() {
      ScreenRootCauseCacheRow badRow =
          ScreenRootCauseCacheRow.builder()
              .projectId(PROJECT_ID)
              .screenName(SCREEN)
              .date(ANCHOR)
              .baseline("not-json")
              .segments("[]")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(screenRootCauseCacheDao.findByKey(PROJECT_ID, SCREEN, ANCHOR))
          .thenReturn(Single.just(Optional.of(badRow)));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(screenBaseline(50L, 0L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      verify(clickhouseQueryService, atLeastOnce())
          .executeRootCauseQuery(anyString(), anyString(), anyList(), anyList());
      verify(screenRootCauseCacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRecomputeWhenForceRefreshTrue() {
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(screenBaseline(40L, 0L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END, true).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      verify(screenRootCauseCacheDao, never()).findByKey(any(), any(), any());
      verify(screenRootCauseCacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  class ComputePaths {

    @Test
    void shouldReturnNoDataWhenBaselineQueryReturnsNoRows() {
      when(screenRootCauseCacheDao.findByKey(PROJECT_ID, SCREEN, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(Single.just(emptyTableResponse()));

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
      verify(screenRootCauseCacheDao, never())
          .upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldTreatIncompleteClickhouseJobAsEmptyBaseline() {
      when(screenRootCauseCacheDao.findByKey(PROJECT_ID, SCREEN, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> incomplete =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder().jobComplete(false).build();
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(Single.just(incomplete));

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
    }

    @Test
    void shouldReturnNoDataWhenBaselineRowHasZeroClickVolume() {
      when(screenRootCauseCacheDao.findByKey(PROJECT_ID, SCREEN, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(screenBaseline(0L, 5L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
      verify(screenRootCauseCacheDao, never())
          .upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnEverythingGoodWhenVolumePositiveAndZeroBadFrustration() {
      when(screenRootCauseCacheDao.findByKey(PROJECT_ID, SCREEN, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(screenBaseline(200L, 0L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      assertThat(result.getSegments()).isEmpty();
      verify(screenRootCauseCacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldFinishWithFlatModeWhenBadFrustrationButNoSegmentCandidates() {
      when(screenRootCauseCacheDao.findByKey(PROJECT_ID, SCREEN, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(screenBaseline(100L, 10L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).isEmpty();
      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      verify(screenRootCauseCacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldBuildHierarchicalModeWhenTwoDimensionsMatchSimilarityThreshold() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(2);
      when(screenRootCauseCacheDao.findByKey(PROJECT_ID, SCREEN, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = screenBaseline(500L, 100L);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS click_volume")) {
                  if (q.contains("GROUP BY Platform, OsVersion")) {
                    Map<String, Object> row = screenSegmentMetricRow();
                    row.put("Platform", "Android");
                    row.put("OsVersion", "14");
                    return Single.just(singleRowTableResponse(row));
                  }
                  Map<String, Object> row = screenSegmentMetricRow();
                  row.put("Platform", "Android");
                  return Single.just(singleRowTableResponse(row));
                }
                if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("Platform", "Android", ScreenRcaQueryBuilder.BAD_FRUSTRATION, 100L)));
                }
                if (q.contains("GROUP BY OsVersion") && q.contains("AND Platform =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("OsVersion", "14", ScreenRcaQueryBuilder.BAD_FRUSTRATION, 100L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.HIERARCHICAL);
      assertThat(result.getSegments()).hasSize(2);
      verify(screenRootCauseCacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldBuildMultipleFlatSegmentsWhenNoDimensionMatchesSimilarityThreshold() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(2);
      when(screenRootCauseCacheDao.findByKey(PROJECT_ID, SCREEN, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = screenBaseline(300L, 100L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS click_volume")) {
                  Map<String, Object> row = screenSegmentMetricRow();
                  if (q.contains("GROUP BY Platform") && q.contains("AND Platform =")) {
                    row.put("Platform", "Android");
                    return Single.just(singleRowTableResponse(row));
                  }
                  if (q.contains("GROUP BY OsVersion") && q.contains("AND OsVersion =")) {
                    row.put("OsVersion", "14");
                    return Single.just(singleRowTableResponse(row));
                  }
                  return Single.just(emptyTableResponse());
                }
                if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("Platform", "Android", ScreenRcaQueryBuilder.BAD_FRUSTRATION, 10L)));
                }
                if (q.contains("GROUP BY OsVersion")
                    && !q.contains("AND OsVersion =")
                    && !q.contains("AND Platform =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("OsVersion", "14", ScreenRcaQueryBuilder.BAD_FRUSTRATION, 10L)));
                }
                if (q.contains("GROUP BY AppVersion") && !q.contains("AND AppVersion =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("AppVersion", "2.1", ScreenRcaQueryBuilder.BAD_FRUSTRATION, 10L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getScreenRootCause(PROJECT_ID, SCREEN, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getSegments()).hasSize(2);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("Platform: Android");
      assertThat(result.getSegments().get(1).getLabel()).isEqualTo("OsVersion: 14");
      verify(screenRootCauseCacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }
  }
}
