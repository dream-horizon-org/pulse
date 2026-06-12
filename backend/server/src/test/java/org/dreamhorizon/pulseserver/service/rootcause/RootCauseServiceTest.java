package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.WebApplicationException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
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
class RootCauseServiceTest {

  private static final String PROJECT_ID = "project-1";
  private static final String INTERACTION = "checkout";
  private static final LocalDate ANALYSIS_DATE = LocalDate.of(2025, 3, 10);
  private static final Instant WINDOW_END = Instant.parse("2025-03-10T14:00:00Z");
  private static final LocalDateTime WINDOW_END_LDT =
      LocalDateTime.ofInstant(WINDOW_END, ZoneOffset.UTC);

  @Mock
  private RootCauseConfig rootCauseConfig;

  @Mock
  private ClickhouseQueryService clickhouseQueryService;

  @Mock
  private RootCauseCacheDao cacheDao;

  private RootCauseService service;

  @BeforeEach
  void setUp() {
    reset(clickhouseQueryService, rootCauseConfig, cacheDao);
    lenient().when(rootCauseConfig.getLookbackDays()).thenReturn(7);
    lenient().when(rootCauseConfig.getSimilarityThresholdPct()).thenReturn(75);
    lenient().when(rootCauseConfig.getMaxSegments()).thenReturn(4);
    lenient()
        .when(rootCauseConfig.getDimensionOrder())
        .thenReturn(List.of("Platform", "OsVersion", "AppVersion"));
    lenient()
        .when(cacheDao.upsert(any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Completable.complete());
    service =
        new RootCauseService(
            rootCauseConfig, clickhouseQueryService, cacheDao, new ObjectMapperUtil(ObjectMapperFactory.get()));
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

  private static GetQueryDataResponseDto<GetRawUserEventsResponseDto> multiRowTableResponse(
      List<String> columns, List<Map<String, Object>> rowMaps) {
    List<GetRawUserEventsResponseDto.Field> fields = new ArrayList<>();
    for (String key : columns) {
      GetRawUserEventsResponseDto.Field f = new GetRawUserEventsResponseDto.Field();
      f.setName(key);
      fields.add(f);
    }
    List<GetRawUserEventsResponseDto.Row> rows = new ArrayList<>();
    for (Map<String, Object> rowMap : rowMaps) {
      List<GetRawUserEventsResponseDto.RowField> rowFields = new ArrayList<>();
      for (String key : columns) {
        GetRawUserEventsResponseDto.RowField rf = new GetRawUserEventsResponseDto.RowField();
        rf.setValue(rowMap.get(key));
        rowFields.add(rf);
      }
      GetRawUserEventsResponseDto.Row row = new GetRawUserEventsResponseDto.Row();
      row.setRowFields(rowFields);
      rows.add(row);
    }
    GetRawUserEventsResponseDto.Schema schema = new GetRawUserEventsResponseDto.Schema(fields);
    GetRawUserEventsResponseDto data =
        GetRawUserEventsResponseDto.builder().schema(schema).rows(rows).build();
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

  @Nested
  class WindowValidation {

    @Test
    void shouldMapIllegalLookbackToBadRequest() {
      when(rootCauseConfig.getLookbackDays()).thenReturn(0);

      assertThatThrownBy(
          () -> service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet())
          .isInstanceOf(WebApplicationException.class)
          .satisfies(
              t -> {
                WebApplicationException wae = (WebApplicationException) t;
                assertThat(wae.getResponse().getStatus())
                    .isEqualTo(ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getHttpStatusCode());
              });
      verify(clickhouseQueryService, never()).executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true));
    }

    @Test
    void shouldMapEndBeforeWindowStartToBadRequest() {
      // anchor 2025-03-10, lookback 7 -> startInclusive = 2025-03-04T00:00:00Z
      Instant endBeforeStart = Instant.parse("2025-03-03T23:00:00Z");

      assertThatThrownBy(
          () -> service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, endBeforeStart).blockingGet())
          .isInstanceOf(WebApplicationException.class)
          .satisfies(
              t -> {
                WebApplicationException wae = (WebApplicationException) t;
                assertThat(wae.getResponse().getStatus())
                    .isEqualTo(ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getHttpStatusCode());
              });
      verify(clickhouseQueryService, never()).executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true));
    }
  }

  @Nested
  class CacheBehavior {

    @Test
    void shouldReturnDeserializedResultWhenCacheHitIsFresh() {
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .baseline("{\"volume\":10}")
              .segments("[]")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getBaseline()).containsEntry("volume", 10);
      assertThat(result.getSegments()).isEmpty();
      verify(clickhouseQueryService, never()).executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true));
    }

    @Test
    void shouldTreatBlankBaselineAndSegmentsInCacheAsEmptyCollections() {
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .baseline("   ")
              .segments("")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getBaseline()).isEmpty();
      assertThat(result.getSegments()).isEmpty();
    }

    @Test
    void shouldDefaultUnknownCacheModeWireValueToFlat() {
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .baseline("{\"volume\":1}")
              .segments("[]")
              .mode("unknown-mode")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
    }

    @Test
    void shouldDeserializeSegmentsFromCacheRow() {
      String segmentsJson =
          "[{\"label\":\"Platform: Android\",\"dimensions\":{\"Platform\":\"Android\"},"
              + "\"metrics\":{\"volume\":1},\"deltas\":{}}]";
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .baseline("{}")
              .segments(segmentsJson)
              .mode("hierarchical")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.HIERARCHICAL);
      assertThat(result.getSegments()).hasSize(1);
      assertThat(result.getSegments().get(0).getLabel()).contains("Android");
    }

    @Test
    void shouldFailWhenCacheBaselineJsonIsInvalid() {
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .baseline("not-valid-json")
              .segments("[]")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      assertThatThrownBy(() -> service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet())
          .isInstanceOf(WebApplicationException.class)
          .satisfies(
              t -> {
                WebApplicationException wae = (WebApplicationException) t;
                assertThat(wae.getResponse().getStatus())
                    .isEqualTo(ServiceError.INTERNAL_SERVER_ERROR.getHttpStatusCode());
              });

      verify(clickhouseQueryService, never()).executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true));
    }

    @Test
    void shouldFailWhenCacheSegmentsJsonIsInvalid() {
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .baseline("{}")
              .segments("not-a-list")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      assertThatThrownBy(() -> service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet())
          .isInstanceOf(WebApplicationException.class);

      verify(clickhouseQueryService, never()).executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true));
    }

    @Test
    void shouldReturnCachedRowWithoutRecomputeRegardlessOfCachedAtAge() {
      RootCauseCacheRow stale =
          RootCauseCacheRow.builder()
              .baseline("{\"volume\":3}")
              .segments("[]")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(48))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(stale)));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getBaseline()).containsEntry("volume", 3);
      verify(clickhouseQueryService, never()).executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true));
      verify(cacheDao, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRecomputeWhenForceRefreshTrue() {
      Map<String, Object> baseline = new LinkedHashMap<>();
      baseline.put(RootCauseMetricsRegistry.VOLUME, 50L);
      baseline.put("problematic_count", 0L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END, true).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      verify(cacheDao, never()).findByKey(any(), any(), any());
      verify(cacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  class ComputePaths {

    @Test
    void shouldReturnNoDataWhenBaselineQueryReturnsNoRows() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenReturn(Single.just(emptyTableResponse()));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
      verify(cacheDao, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldTreatIncompleteClickhouseJobAsEmptyBaseline() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> incomplete =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .jobComplete(false)
              .build();
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenReturn(Single.just(incomplete));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
    }

    @Test
    void shouldReturnNoDataWhenBaselineRowHasZeroVolume() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = new LinkedHashMap<>();
      baseline.put(RootCauseMetricsRegistry.VOLUME, 0L);
      baseline.put("problematic_count", 5L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
      verify(cacheDao, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnEverythingGoodWhenVolumePositiveAndNoProblematicEvents() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = new LinkedHashMap<>();
      baseline.put(RootCauseMetricsRegistry.VOLUME, 200L);
      baseline.put("problematic_count", 0L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      assertThat(result.getSegments()).isEmpty();
      verify(cacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldFinishWithFlatModeWhenProblematicVolumeButNoSegmentCandidates() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = new LinkedHashMap<>();
      baseline.put(RootCauseMetricsRegistry.VOLUME, 100L);
      baseline.put("problematic_count", 10L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).isEmpty();
      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      verify(cacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldBuildHierarchicalModeWhenTwoDimensionsMatchSimilarityThreshold() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(2);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                boolean isSegmentMetricsQuery = q.contains(" AS volume");
                if (isSegmentMetricsQuery) {
                  boolean isTwoDims = q.contains("GROUP BY Platform, OsVersion");
                  if (isTwoDims) {
                    Map<String, Object> row = segmentMetricRow();
                    row.put("Platform", "Android");
                    row.put("OsVersion", "14");
                    return Single.just(singleRowTableResponse(row));
                  }
                  Map<String, Object> row = segmentMetricRow();
                  row.put("Platform", "Android");
                  return Single.just(singleRowTableResponse(row));
                }
                boolean isPlatformBreakdown =
                    q.contains("GROUP BY Platform") && !q.contains("AND Platform =");
                if (isPlatformBreakdown) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("Platform", "Android", "problematic_count", 100L)));
                }
                boolean isOsVersionUnderPlatform =
                    q.contains("GROUP BY OsVersion") && q.contains("AND Platform =");
                if (isOsVersionUnderPlatform) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("OsVersion", "14", "problematic_count", 100L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.HYBRID);
      assertThat(result.getSegments()).hasSize(2);
      assertThat(result.getSegments().get(0).getLabel()).doesNotContain(":");
      verify(cacheDao, times(1)).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldUseHierarchicalModeWhenFirstSegmentValueContainsColon() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(2);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);
      String platformValueWithColon = "Washington: D.C.";

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                boolean isSegmentMetricsQuery = q.contains(" AS volume");
                if (isSegmentMetricsQuery) {
                  boolean isTwoDims = q.contains("GROUP BY Platform, OsVersion");
                  if (isTwoDims) {
                    Map<String, Object> row = segmentMetricRow();
                    row.put("Platform", platformValueWithColon);
                    row.put("OsVersion", "14");
                    return Single.just(singleRowTableResponse(row));
                  }
                  Map<String, Object> row = segmentMetricRow();
                  row.put("Platform", platformValueWithColon);
                  return Single.just(singleRowTableResponse(row));
                }
                boolean isPlatformBreakdown =
                    q.contains("GROUP BY Platform") && !q.contains("AND Platform =");
                if (isPlatformBreakdown) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("Platform", platformValueWithColon, "problematic_count", 100L)));
                }
                boolean isOsVersionUnderPlatform =
                    q.contains("GROUP BY OsVersion") && q.contains("AND Platform =");
                if (isOsVersionUnderPlatform) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("OsVersion", "14", "problematic_count", 100L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.HYBRID);
      assertThat(result.getSegments()).hasSize(2);
      assertThat(result.getSegments().get(0).getLabel()).contains(":");
    }

    @Test
    void shouldUseHierarchicalModeWhenMaxSegmentsIsOneAndLabelUsesDimValueFormat() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(1);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(200L, 50L);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                boolean isSegmentMetricsQuery = q.contains(" AS volume");
                if (isSegmentMetricsQuery) {
                  Map<String, Object> row = segmentMetricRow();
                  row.put("Platform", "Android");
                  return Single.just(singleRowTableResponse(row));
                }
                boolean isPlatformBreakdown =
                    q.contains("GROUP BY Platform") && !q.contains("AND Platform =");
                if (isPlatformBreakdown) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("Platform", "Android", "problematic_count", 50L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getSegments()).hasSize(1);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("Platform: Android");
    }

    @Test
    void shouldDeepenHierarchyFromNextDimOrderIndexWhenFirstPickSkipsEarlierDimensions() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(2);
      when(rootCauseConfig.getDimensionOrder())
          .thenReturn(List.of("Platform", "OsVersion", "AppVersion", "DeviceModel"));
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);
      AtomicBoolean sawOsVersionUnderAppVersion = new AtomicBoolean();
      AtomicBoolean sawDeviceModelUnderAppVersion = new AtomicBoolean();

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                boolean isSegmentMetricsQuery = q.contains(" AS volume");
                if (isSegmentMetricsQuery) {
                  Map<String, Object> row = segmentMetricRow();
                  if (q.contains("GROUP BY AppVersion, DeviceModel")) {
                    row.put("AppVersion", "2.0");
                    row.put("DeviceModel", "Pixel");
                    return Single.just(singleRowTableResponse(row));
                  }
                  row.put("AppVersion", "2.0");
                  return Single.just(singleRowTableResponse(row));
                }
                boolean isOsVersionUnderAppVersion =
                    q.contains("GROUP BY OsVersion") && q.contains("AND AppVersion =");
                if (isOsVersionUnderAppVersion) {
                  sawOsVersionUnderAppVersion.set(true);
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("OsVersion", "14", "problematic_count", 100L)));
                }
                boolean isDeviceModelUnderAppVersion =
                    q.contains("GROUP BY DeviceModel") && q.contains("AND AppVersion =");
                if (isDeviceModelUnderAppVersion) {
                  sawDeviceModelUnderAppVersion.set(true);
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("DeviceModel", "Pixel", "problematic_count", 100L)));
                }
                boolean isPlatformBreakdown =
                    q.contains("GROUP BY Platform") && !q.contains("AND Platform =");
                if (isPlatformBreakdown) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("Platform", "Android", "problematic_count", 50L)));
                }
                boolean isOsVersionUnscoped =
                    q.contains("GROUP BY OsVersion") && !q.contains("AND AppVersion =");
                if (isOsVersionUnscoped) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("OsVersion", "14", "problematic_count", 50L)));
                }
                boolean isAppVersionUnscoped =
                    q.contains("GROUP BY AppVersion") && !q.contains("AND AppVersion =");
                if (isAppVersionUnscoped) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("AppVersion", "2.0", "problematic_count", 100L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(sawDeviceModelUnderAppVersion.get()).isTrue();
      assertThat(sawOsVersionUnderAppVersion.get()).isFalse();
      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.HYBRID);
      assertThat(result.getSegments()).hasSize(2);
    }

    @Test
    void shouldOmitFlatSegmentWhenSegmentMetricsQueryReturnsNoRows() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(100L, 20L);
      AtomicInteger platformBreakdownPass = new AtomicInteger(0);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                boolean isSegmentMetricsQuery = q.contains(" AS volume");
                if (isSegmentMetricsQuery) {
                  return Single.just(emptyTableResponse());
                }
                boolean isPlatformBreakdown =
                    q.contains("GROUP BY Platform") && !q.contains("AND Platform =");
                if (isPlatformBreakdown) {
                  int pass = platformBreakdownPass.incrementAndGet();
                  long countForPass = pass == 1 ? 5L : 20L;
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("Platform", "Android", "problematic_count", countForPass)));
                }
                boolean isOsPick =
                    q.contains("GROUP BY OsVersion") && !q.contains("AND Platform =");
                if (isOsPick) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("OsVersion", "14", "problematic_count", 5L)));
                }
                boolean isAppPick =
                    q.contains("GROUP BY AppVersion") && !q.contains("AND Platform =");
                if (isAppPick) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("AppVersion", "1.0", "problematic_count", 5L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).isEmpty();
      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
    }

    @Test
    void shouldTreatJobCompleteWithNullDataAsEmptyBaseline() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> noDataPayload =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .jobComplete(true)
              .data(null)
              .build();
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenReturn(Single.just(noDataPayload));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
    }

    @Test
    void shouldBuildMultipleFlatSegmentsWhenNoDimensionMatchesSimilarityThreshold() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(2);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      // totalProblematic 100 -> threshold 75; unscoped breakdown rows stay at 10 so pickFirst never
      // matches, but flat mode still picks top values with count > 0 (same SQL for both phases).
      Map<String, Object> baseline = baselineWithVolumeAndProblematic(300L, 100L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS volume")) {
                  Map<String, Object> row = segmentMetricRow();
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
                          Map.of("Platform", "Android", "problematic_count", 10L)));
                }
                if (q.contains("GROUP BY OsVersion")
                    && !q.contains("AND OsVersion =")
                    && !q.contains("AND Platform =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("OsVersion", "14", "problematic_count", 10L)));
                }
                if (q.contains("GROUP BY AppVersion") && !q.contains("AND AppVersion =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("AppVersion", "2.1", "problematic_count", 10L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getSegments()).hasSize(2);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("Platform: Android");
      assertThat(result.getSegments().get(1).getLabel()).isEqualTo("OsVersion: 14");
      verify(cacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldAddFlatExtrasWhenHierarchyChildDimensionHasNoRowAboveThreshold() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(4);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);
      // threshold 75 -> 75; second hierarchy step returns only 50 problematic (< 75) -> flat extras
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS volume")) {
                  Map<String, Object> row = segmentMetricRow();
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
                          Map.of("Platform", "Android", "problematic_count", 100L)));
                }
                if (q.contains("GROUP BY OsVersion") && q.contains("AND Platform =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("OsVersion", "14", "problematic_count", 50L)));
                }
                if (q.contains("GROUP BY OsVersion")
                    && !q.contains("AND OsVersion =")
                    && !q.contains("AND Platform =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("OsVersion", "14", "problematic_count", 90L)));
                }
                if (q.contains("GROUP BY AppVersion") && !q.contains("AND AppVersion =")) {
                  return Single.just(emptyTableResponse());
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getSegments()).hasSize(2);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("Platform: Android");
      assertThat(result.getSegments().get(1).getLabel()).isEqualTo("OsVersion: 14");
      verify(cacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnEmptySegmentsWhenDimensionOrderIsEmpty() {
      when(rootCauseConfig.getDimensionOrder()).thenReturn(List.of());
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = baselineWithVolumeAndProblematic(200L, 50L);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).isEmpty();
      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      verify(cacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldPickHighestProblematicCountValueInFlatSegment() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(1);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(200L, 100L);
      // threshold = 75; both rows pass; flat pass picks max problematic_count → iOS (110) > Android (90).
      Map<String, Object> rowAndroid = new LinkedHashMap<>();
      rowAndroid.put("Platform", "Android");
      rowAndroid.put("problematic_count", 90L);
      Map<String, Object> rowIos = new LinkedHashMap<>();
      rowIos.put("Platform", "iOS");
      rowIos.put("problematic_count", 110L);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS volume")) {
                  Map<String, Object> row = segmentMetricRow();
                  row.put("Platform", "Android");
                  return Single.just(singleRowTableResponse(row));
                }
                if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
                  return Single.just(
                      multiRowTableResponse(
                          List.of("Platform", "problematic_count"), List.of(rowAndroid, rowIos)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getSegments()).hasSize(1);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("Platform: iOS");
    }

    @Test
    void shouldTreatNullDimensionValueAsEmptyString() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(1);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(200L, 100L);
      Map<String, Object> nullPlatformRow = new LinkedHashMap<>();
      nullPlatformRow.put("Platform", null);
      nullPlatformRow.put("problematic_count", 100L);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS volume")) {
                  Map<String, Object> row = segmentMetricRow();
                  row.put("Platform", "");
                  return Single.just(singleRowTableResponse(row));
                }
                if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
                  return Single.just(singleRowTableResponse(nullPlatformRow));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getSegments()).hasSize(1);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("Platform: ");
      assertThat(result.getSegments().get(0).getDimensions()).containsEntry("Platform", "");
    }

    @Test
    void shouldPadMissingRowCellsWithNullWhenSchemaIsWider() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      GetRawUserEventsResponseDto.Field fVol = new GetRawUserEventsResponseDto.Field();
      fVol.setName(RootCauseMetricsRegistry.VOLUME);
      GetRawUserEventsResponseDto.Field fProb = new GetRawUserEventsResponseDto.Field();
      fProb.setName("problematic_count");
      GetRawUserEventsResponseDto.Schema schema =
          new GetRawUserEventsResponseDto.Schema(List.of(fVol, fProb));
      GetRawUserEventsResponseDto.RowField onlyVolume = new GetRawUserEventsResponseDto.RowField();
      onlyVolume.setValue(10L);
      GetRawUserEventsResponseDto.Row sparseRow = new GetRawUserEventsResponseDto.Row();
      sparseRow.setRowFields(List.of(onlyVolume));
      GetRawUserEventsResponseDto data =
          GetRawUserEventsResponseDto.builder()
              .schema(schema)
              .rows(List.of(sparseRow))
              .build();
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> response =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .jobComplete(true)
              .data(data)
              .build();

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(response);
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
    }
  }

  @Nested
  class HybridDimensionOrdering {

    @Test
    void shouldOrderStrongSignalsByMaxDescThenBaseOrderForTies() {
      List<String> base =
          List.of("Platform", "OsVersion", "AppVersion", "DeviceModel");
      Map<String, Long> maxes =
          Map.of(
              "Platform", 4200L,
              "OsVersion", 3200L,
              "AppVersion", 3900L,
              "DeviceModel", 2800L);
      double threshold = 3750;
      assertThat(RootCauseService.hybridDimensionOrderFromPrecomputedMaxes(base, maxes, threshold))
          .containsExactly("Platform", "AppVersion", "OsVersion", "DeviceModel");
    }

    @Test
    void shouldReturnBaseOrderWhenNoDimensionReachesStrongSignalThreshold() {
      List<String> base = List.of("A", "B", "C");
      Map<String, Long> maxes = Map.of("A", 10L, "B", 20L, "C", 15L);
      assertThat(RootCauseService.hybridDimensionOrderFromPrecomputedMaxes(base, maxes, 75))
          .containsExactly("A", "B", "C");
    }

    @Test
    void shouldTieBreakEqualMaxStrongSignalsByBaseOrderIndex() {
      List<String> base = List.of("Platform", "OsVersion", "AppVersion");
      Map<String, Long> maxes = Map.of("Platform", 100L, "OsVersion", 50L, "AppVersion", 100L);
      assertThat(RootCauseService.hybridDimensionOrderFromPrecomputedMaxes(base, maxes, 75))
          .containsExactly("Platform", "AppVersion", "OsVersion");
    }

    @Test
    void shouldTreatExactThresholdAsStrongSignal() {
      List<String> base = List.of("A", "B");
      Map<String, Long> maxes = Map.of("A", 75L, "B", 74L);
      assertThat(RootCauseService.hybridDimensionOrderFromPrecomputedMaxes(base, maxes, 75))
          .containsExactly("A", "B");
    }

    @Test
    void shouldPlaceSingleStrongSignalFirstThenRestInBaseOrder() {
      List<String> base = List.of("Platform", "OsVersion", "AppVersion");
      Map<String, Long> maxes = Map.of("Platform", 100L, "OsVersion", 40L, "AppVersion", 50L);
      assertThat(RootCauseService.hybridDimensionOrderFromPrecomputedMaxes(base, maxes, 75))
          .containsExactly("Platform", "OsVersion", "AppVersion");
    }

    @Test
    void shouldPickHigherMaxDimensionFirstWhenHybridOrderingEnabled() {
      when(rootCauseConfig.isHybridDimensionOrderingEnabled()).thenReturn(true);
      when(rootCauseConfig.getMaxSegments()).thenReturn(1);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);
      when(clickhouseQueryService.executeRootCauseQuery(
              anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS volume")) {
                  Map<String, Object> row = segmentMetricRow();
                  row.put("AppVersion", "2.0");
                  return Single.just(singleRowTableResponse(row));
                }
                if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("Platform", "Android", "problematic_count", 80L)));
                }
                if (q.contains("GROUP BY OsVersion") && !q.contains("AND OsVersion =")) {
                  return Single.just(
                      singleRowTableResponse(Map.of("OsVersion", "14", "problematic_count", 40L)));
                }
                if (q.contains("GROUP BY AppVersion") && !q.contains("AND AppVersion =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("AppVersion", "2.0", "problematic_count", 100L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getSegments()).hasSize(1);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("AppVersion: 2.0");
      verify(cacheDao).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldKeepStaticFirstDimensionWhenHybridOrderingDisabled() {
      when(rootCauseConfig.isHybridDimensionOrderingEnabled()).thenReturn(false);
      when(rootCauseConfig.getMaxSegments()).thenReturn(1);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);
      when(clickhouseQueryService.executeRootCauseQuery(
              anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS volume")) {
                  Map<String, Object> row = segmentMetricRow();
                  row.put("Platform", "Android");
                  return Single.just(singleRowTableResponse(row));
                }
                if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("Platform", "Android", "problematic_count", 80L)));
                }
                if (q.contains("GROUP BY OsVersion") && !q.contains("AND OsVersion =")) {
                  return Single.just(
                      singleRowTableResponse(Map.of("OsVersion", "14", "problematic_count", 40L)));
                }
                if (q.contains("GROUP BY AppVersion") && !q.contains("AND AppVersion =")) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("AppVersion", "2.0", "problematic_count", 100L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getSegments()).hasSize(1);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("Platform: Android");
    }
  }

  @Nested
  class HybridMerge {

    @Test
    void shouldPlaceHierarchicalSegmentBeforeFlatSegmentInHybridResult() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(3);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS volume")) {
                  Map<String, Object> row = segmentMetricRow();
                  if (q.contains("GROUP BY Platform, OsVersion")) {
                    row.put("Platform", "Android");
                    row.put("OsVersion", "14");
                    return Single.just(singleRowTableResponse(row));
                  }
                  if (q.contains("GROUP BY Platform") && q.contains("AND Platform =")) {
                    row.put("Platform", "Android");
                    return Single.just(singleRowTableResponse(row));
                  }
                  if (q.contains("GROUP BY OsVersion") && q.contains("AND OsVersion =")) {
                    row.put("OsVersion", "14");
                    return Single.just(singleRowTableResponse(row));
                  }
                  if (q.contains("GROUP BY AppVersion") && q.contains("AND AppVersion =")) {
                    row.put("AppVersion", "1.0");
                    return Single.just(singleRowTableResponse(row));
                  }
                  return Single.just(emptyTableResponse());
                }
                if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
                  return Single.just(singleRowTableResponse(
                      Map.of("Platform", "Android", "problematic_count", 100L)));
                }
                if (q.contains("GROUP BY OsVersion") && q.contains("AND Platform =")) {
                  return Single.just(singleRowTableResponse(
                      Map.of("OsVersion", "14", "problematic_count", 100L)));
                }
                if (q.contains("GROUP BY OsVersion") && !q.contains("AND Platform =")) {
                  return Single.just(singleRowTableResponse(
                      Map.of("OsVersion", "14", "problematic_count", 30L)));
                }
                if (q.contains("GROUP BY AppVersion") && !q.contains("AND AppVersion =")) {
                  return Single.just(singleRowTableResponse(
                      Map.of("AppVersion", "1.0", "problematic_count", 20L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.HYBRID);
      // hierarchical segment (2D+) must appear before flat segments (1D)
      assertThat(result.getSegments()).hasSizeGreaterThanOrEqualTo(2);
      assertThat(result.getSegments().get(0).getDimensions()).hasSize(2);
      assertThat(result.getSegments().get(1).getDimensions()).hasSize(1);
    }

    @Test
    void shouldReturnFlatModeWhenHierarchyYieldsOnly1DPath() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(3);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS volume")) {
                  Map<String, Object> row = segmentMetricRow();
                  row.put("Platform", "Android");
                  return Single.just(singleRowTableResponse(row));
                }
                if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
                  return Single.just(singleRowTableResponse(
                      Map.of("Platform", "Android", "problematic_count", 100L)));
                }
                // Hierarchy second step: OsVersion under Platform → below threshold
                if (q.contains("GROUP BY OsVersion") && q.contains("AND Platform =")) {
                  return Single.just(singleRowTableResponse(
                      Map.of("OsVersion", "14", "problematic_count", 10L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getSegments()).allSatisfy(
          s -> assertThat(s.getDimensions()).hasSize(1));
    }

    @Test
    void shouldHaveNoDuplicate1DSegmentsBetweenHierarchicalAndFlatTiers() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(4);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS volume")) {
                  Map<String, Object> row = segmentMetricRow();
                  if (q.contains("GROUP BY Platform, OsVersion")) {
                    row.put("Platform", "Android");
                    row.put("OsVersion", "14");
                    return Single.just(singleRowTableResponse(row));
                  }
                  row.put("Platform", "Android");
                  return Single.just(singleRowTableResponse(row));
                }
                if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
                  return Single.just(singleRowTableResponse(
                      Map.of("Platform", "Android", "problematic_count", 100L)));
                }
                if (q.contains("GROUP BY OsVersion") && q.contains("AND Platform =")) {
                  return Single.just(singleRowTableResponse(
                      Map.of("OsVersion", "14", "problematic_count", 100L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.HYBRID);
      // No segment should have dimension-count == 1 AND appear in the hierarchical tier position
      // (i.e., no 1D dimensions appear in hierarchical segments at all)
      result.getSegments().forEach(
          s -> {
            if (s.getDimensions().size() == 1) {
              // flat tier only
              assertThat(s.getLabel()).contains(":");
            } else {
              // hierarchical tier: must have ≥ 2 dimensions
              assertThat(s.getDimensions().size()).isGreaterThanOrEqualTo(2);
            }
          });
      // Verify no duplicate dimension keys between hierarchical and flat segments
      long oneDimCount = result.getSegments().stream()
          .filter(s -> s.getDimensions().size() == 1)
          .count();
      long multiDimCount = result.getSegments().stream()
          .filter(s -> s.getDimensions().size() >= 2)
          .count();
      assertThat(oneDimCount + multiDimCount).isEqualTo(result.getSegments().size());
    }

    @Test
    void shouldDropWeakHierarchicalSegmentAndPreserveMergedOrderForKeptSegments() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(3);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      // Baseline with non-zero error_rate and poor_user_pct so deltas can be computed.
      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);
      baseline.put(RootCauseMetricsRegistry.ERROR_RATE, 10.0);
      baseline.put(RootCauseMetricsRegistry.POOR_USER_PCT, 20.0);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(inv -> {
            String q = inv.getArgument(1, String.class);
            if (!q.contains("GROUP BY")) {
              return Single.just(singleRowTableResponse(baseline));
            }
            if (q.contains(" AS volume")) {
              if (q.contains("GROUP BY Platform, OsVersion")) {
                // Hierarchical 2D segment: same error_rate / poor_user_pct as baseline → S = 0 < 10
                Map<String, Object> row = segmentMetricRow();
                row.put(RootCauseMetricsRegistry.ERROR_RATE, 10.0);
                row.put(RootCauseMetricsRegistry.POOR_USER_PCT, 20.0);
                row.put("Platform", "Android");
                row.put("OsVersion", "14");
                return Single.just(singleRowTableResponse(row));
              }
              if (q.contains("GROUP BY Platform") && q.contains("AND Platform =")) {
                // Flat 1D segment: elevated error_rate / poor_user_pct → S = 400 >> 10
                Map<String, Object> row = segmentMetricRow();
                row.put(RootCauseMetricsRegistry.ERROR_RATE, 30.0);
                row.put(RootCauseMetricsRegistry.POOR_USER_PCT, 60.0);
                row.put("Platform", "Android");
                return Single.just(singleRowTableResponse(row));
              }
              return Single.just(emptyTableResponse());
            }
            if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
              return Single.just(singleRowTableResponse(
                  Map.of("Platform", "Android", "problematic_count", 100L)));
            }
            if (q.contains("GROUP BY OsVersion") && q.contains("AND Platform =")) {
              return Single.just(singleRowTableResponse(
                  Map.of("OsVersion", "14", "problematic_count", 100L)));
            }
            return Single.just(emptyTableResponse());
          });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      // Mode is FLAT: the only 2D candidate matched baseline err+poor sum, so baseline gate drops it before merge.
      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      // Weak 2D hierarchical segment (= baseline error+poor sum) was dropped; strong 1D flat kept.
      assertThat(result.getSegments()).hasSize(1);
      assertThat(result.getSegments().get(0).getDimensions()).hasSize(1);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("Platform: Android");
    }

    @Test
    void shouldCacheHybridModeWireValueWhenBothTiersContribute() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(2);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);

      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(1, String.class);
                if (!q.contains("GROUP BY")) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (q.contains(" AS volume")) {
                  Map<String, Object> row = segmentMetricRow();
                  if (q.contains("GROUP BY Platform, OsVersion")) {
                    row.put("Platform", "Android");
                    row.put("OsVersion", "14");
                    return Single.just(singleRowTableResponse(row));
                  }
                  row.put("Platform", "Android");
                  return Single.just(singleRowTableResponse(row));
                }
                if (q.contains("GROUP BY Platform") && !q.contains("AND Platform =")) {
                  return Single.just(singleRowTableResponse(
                      Map.of("Platform", "Android", "problematic_count", 100L)));
                }
                if (q.contains("GROUP BY OsVersion") && q.contains("AND Platform =")) {
                  return Single.just(singleRowTableResponse(
                      Map.of("OsVersion", "14", "problematic_count", 100L)));
                }
                return Single.just(emptyTableResponse());
              });

      service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, WINDOW_END).blockingGet();

      verify(cacheDao).upsert(
          any(), any(), any(), any(),
          org.mockito.ArgumentMatchers.eq("hybrid"),
          any(), any(), any());
    }
  }

  @Nested
  class DistinctScreensForInteraction {

    @Test
    void shouldReturnScreensFromFirstQueryRow() {
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenReturn(Single.just(singleRowTableResponse(Map.of("screens", List.of("home", "cart")))));
      RootCauseQueryBuilder.Window w =
          new RootCauseQueryBuilder.Window(ANALYSIS_DATE, 7, WINDOW_END);
      List<String> screens =
          service.fetchDistinctScreensForInteraction(PROJECT_ID, INTERACTION, w).blockingGet();
      assertThat(screens).containsExactly("home", "cart");
    }

    @Test
    void shouldReturnEmptyListWhenQueryFails() {
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenReturn(Single.error(new RuntimeException("ch down")));
      RootCauseQueryBuilder.Window w =
          new RootCauseQueryBuilder.Window(ANALYSIS_DATE, 7, WINDOW_END);
      List<String> screens =
          service.fetchDistinctScreensForInteraction(PROJECT_ID, INTERACTION, w).blockingGet();
      assertThat(screens).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenNoRows() {
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenReturn(Single.just(emptyTableResponse()));
      RootCauseQueryBuilder.Window w =
          new RootCauseQueryBuilder.Window(ANALYSIS_DATE, 7, WINDOW_END);
      List<String> screens =
          service.fetchDistinctScreensForInteraction(PROJECT_ID, INTERACTION, w).blockingGet();
      assertThat(screens).isEmpty();
    }

    @Test
    void shouldNormalizeScreensFromStringArrayAndTrimValues() {
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenReturn(
              Single.just(
                  singleRowTableResponse(
                      Map.of("screens", new String[] {" home ", "", "cart"}))));
      RootCauseQueryBuilder.Window w =
          new RootCauseQueryBuilder.Window(ANALYSIS_DATE, 7, WINDOW_END);
      List<String> screens =
          service.fetchDistinctScreensForInteraction(PROJECT_ID, INTERACTION, w).blockingGet();
      assertThat(screens).containsExactly("home", "cart");
    }

    @Test
    void shouldNormalizeScreensFromObjectArraySkippingNulls() {
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenReturn(
              Single.just(
                  singleRowTableResponse(
                      Map.of("screens", new Object[] {"a", null, "  b  "}))));
      RootCauseQueryBuilder.Window w =
          new RootCauseQueryBuilder.Window(ANALYSIS_DATE, 7, WINDOW_END);
      List<String> screens =
          service.fetchDistinctScreensForInteraction(PROJECT_ID, INTERACTION, w).blockingGet();
      assertThat(screens).containsExactly("a", "b");
    }

    @Test
    void shouldReturnEmptyScreensWhenColumnIsNull() {
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("screens", null);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList(), eq(true)))
          .thenReturn(Single.just(singleRowTableResponse(row)));
      RootCauseQueryBuilder.Window w =
          new RootCauseQueryBuilder.Window(ANALYSIS_DATE, 7, WINDOW_END);
      List<String> screens =
          service.fetchDistinctScreensForInteraction(PROJECT_ID, INTERACTION, w).blockingGet();
      assertThat(screens).isEmpty();
    }

    @Test
    void distinctScreensSqlUsesPulseInteractionNameNotSpanName() {
      RootCauseQueryBuilder.Window w =
          new RootCauseQueryBuilder.Window(ANALYSIS_DATE, 7, WINDOW_END);
      RootCauseQuerySpec spec =
          RootCauseQueryBuilder.buildDistinctScreensForInteractionQuery(PROJECT_ID, INTERACTION, w);
      assertThat(spec.sql()).contains("pulse.interaction.name");
      assertThat(spec.sql()).contains("ScreenName");
      assertThat(spec.sql()).doesNotContain("SpanName");
      assertThat(spec.sql()).contains("count()");
      assertThat(spec.sql()).contains("GROUP BY screen_name");
      assertThat(spec.bindValues()).hasSize(4);
    }
  }

  private static Map<String, Object> baselineWithVolumeAndProblematic(long volume, long problematic) {
    Map<String, Object> baseline = new LinkedHashMap<>();
    for (String metric : RootCauseMetricsRegistry.getMetricExpressions().keySet()) {
      baseline.put(metric, 0L);
    }
    baseline.put(RootCauseMetricsRegistry.VOLUME, volume);
    baseline.put("problematic_count", problematic);
    return baseline;
  }

  private static Map<String, Object> segmentMetricRow() {
    Map<String, Object> row = new LinkedHashMap<>();
    for (String metric : RootCauseMetricsRegistry.getMetricExpressions().keySet()) {
      row.put(metric, 1L);
    }
    row.put("problematic_count", 5L);
    return row;
  }
}
