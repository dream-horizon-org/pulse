package org.dreamhorizon.pulseserver.service.rootcause;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
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
    lenient().when(rootCauseConfig.getCacheTtlHours()).thenReturn(24);
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
    void shouldTolerateInvalidJsonBaselineInCacheRow() {
      RootCauseCacheRow row =
          RootCauseCacheRow.builder()
              .baseline("not-valid-json")
              .segments("also-bad")
              .mode("flat")
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result =
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getBaseline()).isEmpty();
      assertThat(result.getSegments()).isEmpty();
    }

    @Test
    void shouldRecomputeWhenCacheEntryIsExpired() {
      RootCauseCacheRow stale =
          RootCauseCacheRow.builder()
              .baseline("{}")
              .segments("[]")
              .mode("flat")
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(48))
              .build();
      when(cacheDao.findByKey(PROJECT_ID, INTERACTION, ANALYSIS_DATE))
          .thenReturn(Single.just(Optional.of(stale)));

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
          service.getRootCause(PROJECT_ID, INTERACTION, ANALYSIS_DATE).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
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
  }
}
