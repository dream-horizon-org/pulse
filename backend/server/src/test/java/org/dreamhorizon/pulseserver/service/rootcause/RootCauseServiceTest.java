package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.ws.rs.WebApplicationException;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;
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

  @Mock
  private RootCauseConfig rootCauseConfig;

  @Mock
  private ClickhouseQueryService clickhouseQueryService;

  @Mock
  private RootCauseCacheDao cacheDao;

  private RootCauseService service;

  @BeforeEach
  void setUp() {
    lenient().when(rootCauseConfig.getLookbackDays()).thenReturn(7);
    lenient().when(rootCauseConfig.getSimilarityThresholdPct()).thenReturn(75);
    lenient().when(rootCauseConfig.getMaxSegments()).thenReturn(4);
    lenient()
        .when(rootCauseConfig.getDimensionOrder())
        .thenReturn(List.of("Platform", "OsVersion", "AppVersion"));
    lenient()
        .when(cacheDao.upsert(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Completable.complete());
    service =
        new RootCauseService(
            rootCauseConfig, clickhouseQueryService, cacheDao, new ObjectMapperUtil());
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

  @Nested
  class CacheBehavior {

    @Test
    void shouldReturnDeserializedResultWhenCacheHitIsFresh() {
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .baseline("{\"volume\":10}")
              .segments("[]")
              .mode("flat")
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getMode()).isEqualTo("flat");
      assertThat(result.getBaseline()).containsEntry("volume", 10);
      assertThat(result.getSegments()).isEmpty();
      verify(clickhouseQueryService, never()).executeQueryOrCreateJob(any(QueryConfiguration.class));
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
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

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
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      assertThatThrownBy(() -> service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet())
          .isInstanceOf(WebApplicationException.class)
          .satisfies(
              t -> {
                WebApplicationException wae = (WebApplicationException) t;
                assertThat(wae.getResponse().getStatus())
                    .isEqualTo(ServiceError.INTERNAL_SERVER_ERROR.getHttpStatusCode());
              });

      verify(clickhouseQueryService, never()).executeQueryOrCreateJob(any(QueryConfiguration.class));
    }

    @Test
    void shouldFailWhenCacheSegmentsJsonIsInvalid() {
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .baseline("{}")
              .segments("not-a-list")
              .mode("flat")
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      assertThatThrownBy(() -> service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet())
          .isInstanceOf(WebApplicationException.class);

      verify(clickhouseQueryService, never()).executeQueryOrCreateJob(any(QueryConfiguration.class));
    }

    @Test
    void shouldReturnCachedRowWithoutRecomputeRegardlessOfCachedAtAge() {
      RootCauseCacheRow stale =
          RootCauseCacheRow.builder()
              .baseline("{\"volume\":3}")
              .segments("[]")
              .mode("flat")
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(48))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(stale)));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getBaseline()).containsEntry("volume", 3);
      verify(clickhouseQueryService, never()).executeQueryOrCreateJob(any(QueryConfiguration.class));
      verify(cacheDao, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRecomputeWhenForceRefreshTrue() {
      Map<String, Object> baseline = new LinkedHashMap<>();
      baseline.put(RootCauseMetricsRegistry.VOLUME, 50L);
      baseline.put("problematic_count", 0L);
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(0, QueryConfiguration.class).getQuery();
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE, true).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      verify(cacheDao, never()).findByKey(any(), any(), any());
      verify(cacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  class ComputePaths {

    @Test
    void shouldReturnNoDataWhenBaselineQueryReturnsNoRows() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenReturn(Single.just(emptyTableResponse()));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
      verify(cacheDao, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldTreatIncompleteClickhouseJobAsEmptyBaseline() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> incomplete =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder()
              .jobComplete(false)
              .build();
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenReturn(Single.just(incomplete));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
    }

    @Test
    void shouldReturnNoDataWhenBaselineRowHasZeroVolume() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = new LinkedHashMap<>();
      baseline.put(RootCauseMetricsRegistry.VOLUME, 0L);
      baseline.put("problematic_count", 5L);
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(0, QueryConfiguration.class).getQuery();
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
      verify(cacheDao, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnEverythingGoodWhenVolumePositiveAndNoProblematicEvents() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = new LinkedHashMap<>();
      baseline.put(RootCauseMetricsRegistry.VOLUME, 200L);
      baseline.put("problematic_count", 0L);
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(0, QueryConfiguration.class).getQuery();
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      assertThat(result.getSegments()).isEmpty();
      verify(cacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldFinishWithFlatModeWhenProblematicVolumeButNoSegmentCandidates() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = new LinkedHashMap<>();
      baseline.put(RootCauseMetricsRegistry.VOLUME, 100L);
      baseline.put("problematic_count", 10L);
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(0, QueryConfiguration.class).getQuery();
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getSegments()).isEmpty();
      assertThat(result.getMode()).isEqualTo("flat");
      verify(cacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldBuildHierarchicalModeWhenTwoDimensionsMatchSimilarityThreshold() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(2);
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(500L, 100L);

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(0, QueryConfiguration.class).getQuery();
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
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getMode()).isEqualTo("hierarchical");
      assertThat(result.getSegments()).hasSize(2);
      assertThat(result.getSegments().get(0).getLabel()).doesNotContain(":");
      verify(cacheDao, times(1)).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldOmitFlatSegmentWhenSegmentMetricsQueryReturnsNoRows() {
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.empty()));

      Map<String, Object> baseline = baselineWithVolumeAndProblematic(100L, 20L);
      AtomicInteger platformBreakdownPass = new AtomicInteger(0);

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(0, QueryConfiguration.class).getQuery();
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
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getSegments()).isEmpty();
      assertThat(result.getMode()).isEqualTo("flat");
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
      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenReturn(Single.just(noDataPayload));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
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

      when(clickhouseQueryService.executeQueryOrCreateJob(any(QueryConfiguration.class)))
          .thenAnswer(
              inv -> {
                String q = inv.getArgument(0, QueryConfiguration.class).getQuery();
                boolean isBaselineQuery = !q.contains("GROUP BY");
                if (isBaselineQuery) {
                  return Single.just(response);
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
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
