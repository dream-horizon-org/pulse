package org.dreamhorizon.pulseserver.service.sessionrca;

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
import org.dreamhorizon.pulseserver.dao.sessionrca.SessionRcaCacheDao;
import org.dreamhorizon.pulseserver.dao.sessionrca.models.SessionRcaCacheRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.rootcause.models.DegradingInteraction;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperFactory;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionRcaServiceTest {

  private static final String PROJECT_ID = "project-1";
  private static final LocalDate ANCHOR = LocalDate.of(2025, 3, 10);
  private static final Instant WINDOW_END = Instant.parse("2025-03-10T14:00:00Z");
  private static final LocalDateTime WINDOW_END_LDT =
      LocalDateTime.ofInstant(WINDOW_END, ZoneOffset.UTC);

  @Mock
  private RootCauseConfig rootCauseConfig;

  @Mock
  private ClickhouseQueryService clickhouseQueryService;

  @Mock
  private SessionRcaCacheDao sessionRcaCacheDao;

  private SessionRcaService service;

  @BeforeEach
  void setUp() {
    lenient().when(rootCauseConfig.getLookbackDays()).thenReturn(7);
    lenient().when(rootCauseConfig.getSimilarityThresholdPct()).thenReturn(75);
    lenient().when(rootCauseConfig.getMaxSegments()).thenReturn(4);
    lenient().when(rootCauseConfig.getMinSegmentVolumePct()).thenReturn(5.0);
    lenient().when(rootCauseConfig.isHybridDimensionOrderingEnabled()).thenReturn(false);
    lenient()
        .when(sessionRcaCacheDao.upsert(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(Completable.complete());
    service =
        new SessionRcaService(
            rootCauseConfig,
            clickhouseQueryService,
            sessionRcaCacheDao,
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
      GetRawUserEventsResponseDto.Field field = new GetRawUserEventsResponseDto.Field();
      field.setName(key);
      fields.add(field);
    }
    List<GetRawUserEventsResponseDto.RowField> rowFields = new ArrayList<>();
    for (String key : keys) {
      GetRawUserEventsResponseDto.RowField rowField = new GetRawUserEventsResponseDto.RowField();
      rowField.setValue(rowMap.get(key));
      rowFields.add(rowField);
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

  private static Map<String, Object> sessionBaseline(
      long volume, double qualityMean, double qualityStd) {
    Map<String, Object> baseline = new LinkedHashMap<>();
    baseline.put(SessionRcaMetricsRegistry.VOLUME, volume);
    baseline.put(SessionRcaMetricsRegistry.VOLUME_WITH_APDEX, volume);
    baseline.put(SessionRcaMetricsRegistry.QUALITY_SCORE, qualityMean);
    baseline.put(SessionRcaMetricsRegistry.QUALITY_SCORE_MEAN, qualityMean);
    baseline.put(SessionRcaMetricsRegistry.QUALITY_SCORE_STD, qualityStd);
    return baseline;
  }

  private static Map<String, Object> sessionSegmentMetricRow(
      String dimension, String value, long volume, double qualityScore) {
    Map<String, Object> row = new LinkedHashMap<>();
    row.put(dimension, value);
    row.put(SessionRcaMetricsRegistry.VOLUME, volume);
    row.put(SessionRcaMetricsRegistry.QUALITY_SCORE, qualityScore);
    return row;
  }

  private static boolean isSessionBaselineQuery(String sql) {
    return sql.contains("count() AS " + SessionRcaMetricsRegistry.VOLUME)
        && sql.contains(SessionRcaMetricsRegistry.QUALITY_SCORE_STD)
        && !sql.contains("GROUP BY");
  }

  private static boolean isAggregateLowQualityCountQuery(String sql) {
    return sql.contains(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT) && !sql.contains("GROUP BY");
  }

  private static boolean isPercentileQuery(String sql) {
    return sql.contains("quantile(0.20)");
  }

  private static boolean isSessionSegmentMetricsQuery(String sql) {
    return sql.contains("GROUP BY")
        && sql.contains(" AS " + SessionRcaMetricsRegistry.QUALITY_SCORE)
        && !sql.contains(SessionRcaMetricsRegistry.INTERACTION_NAME);
  }

  private static boolean isExampleSessionsQuery(String sql) {
    // Degrading-interactions SQL embeds "SELECT sessionId FROM … session_summary" in a subquery;
    // match only the top-level example-sessions query (no otel_traces outer query).
    return sql.contains("SELECT sessionId") && !sql.contains("FROM otel.otel_traces");
  }

  private static boolean isDegradingInteractionsQuery(String sql) {
    return sql.contains("FROM otel.otel_traces")
        && sql.contains(SessionRcaMetricsRegistry.INTERACTION_NAME);
  }

  private static Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>>
      answerDefaultComputeFlow(Map<String, Object> baseline, long totalLowQuality, InvocationOnMock inv) {
    return answerDefaultComputeFlow(baseline, totalLowQuality, inv, 100L);
  }

  /**
   * @param strongSignalLqPerDimension low_quality_count returned for dimension-ranking queries
   *     ({@code GROUP BY &lt;dim&gt;} with {@code AS low_quality_count}). Use &lt; 75 when
   *     {@code totalLowQuality} is 100 and similarity is 75% so no hierarchical first-dim pick
   *     triggers (FLAT mode tests).
   */
  private static Single<GetQueryDataResponseDto<GetRawUserEventsResponseDto>>
      answerDefaultComputeFlow(
          Map<String, Object> baseline,
          long totalLowQuality,
          InvocationOnMock inv,
          long strongSignalLqPerDimension) {
    String sql = inv.getArgument(1, String.class);
    @SuppressWarnings("unchecked")
    List<Object> bindValues = inv.getArgument(3, List.class);
    int bindCount = bindValues.size();

    if (isSessionBaselineQuery(sql)) {
      return Single.just(singleRowTableResponse(baseline));
    }
    if (isAggregateLowQualityCountQuery(sql)) {
      return Single.just(
          singleRowTableResponse(Map.of(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, totalLowQuality)));
    }
    if (isPercentileQuery(sql)) {
      return Single.just(singleRowTableResponse(Map.of("p20", 1000L, "p80", 5000L)));
    }
    if (isExampleSessionsQuery(sql)) {
      return Single.just(
          singleRowTableResponse(Map.of("sessionId", "session-1")));
    }
    if (isDegradingInteractionsQuery(sql)) {
      LinkedHashMap<String, Object> degRow = new LinkedHashMap<>();
      degRow.put(SessionRcaMetricsRegistry.INTERACTION_NAME, "checkout");
      degRow.put(SessionRcaMetricsRegistry.INTERACTION_COUNT, 3L);
      degRow.put(SessionRcaMetricsRegistry.AVG_APDEX, 0.2);
      degRow.put(SessionRcaMetricsRegistry.DEGRADATION_WEIGHT, 2.4);
      return Single.just(singleRowTableResponse(degRow));
    }
    if (isSessionSegmentMetricsQuery(sql)) {
      if (sql.contains("GROUP BY platform") && bindCount == 4) {
        return Single.just(
            singleRowTableResponse(
                sessionSegmentMetricRow("platform", "Android", 50L, 0.2)));
      }
      if (sql.contains("GROUP BY osVersion") && bindCount == 4) {
        return Single.just(
            singleRowTableResponse(
                sessionSegmentMetricRow("osVersion", "14", 50L, 0.2)));
      }
      if (sql.contains("GROUP BY platform, osVersion") && bindCount == 5) {
        return Single.just(
            singleRowTableResponse(
                sessionSegmentMetricRow("platform", "Android", 50L, 0.2)));
      }
      return Single.just(emptyTableResponse());
    }
    if (sql.contains("GROUP BY platform") && bindCount == 4) {
      return Single.just(
          singleRowTableResponse(
              Map.of(
                  "platform",
                  "Android",
                  SessionRcaMetricsRegistry.LOW_QUALITY_COUNT,
                  strongSignalLqPerDimension)));
    }
    if (sql.contains("GROUP BY osVersion") && bindCount == 4) {
      return Single.just(
          singleRowTableResponse(
              Map.of(
                  "osVersion",
                  "14",
                  SessionRcaMetricsRegistry.LOW_QUALITY_COUNT,
                  strongSignalLqPerDimension)));
    }
    if (sql.contains("GROUP BY appVersion") && bindCount == 4) {
      return Single.just(
          singleRowTableResponse(
              Map.of("appVersion", "2.1", SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 10L)));
    }
    return Single.just(emptyTableResponse());
  }

  @Nested
  class WindowValidation {

    @Test
    void shouldMapIllegalLookbackToBadRequest() {
      when(rootCauseConfig.getLookbackDays()).thenReturn(0);

      assertThatThrownBy(() -> service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet())
          .isInstanceOf(WebApplicationException.class)
          .satisfies(
              throwable -> {
                WebApplicationException wae = (WebApplicationException) throwable;
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
              () -> service.getSessionRca(PROJECT_ID, ANCHOR, endBeforeStart).blockingGet())
          .isInstanceOf(WebApplicationException.class);

      verify(clickhouseQueryService, never())
          .executeRootCauseQuery(anyString(), anyString(), anyList(), anyList());
    }
  }

  @Nested
  class CacheBehavior {

    @Test
    void shouldReturnDeserializedResultWhenCacheHit() {
      SessionRcaCacheRow row =
          SessionRcaCacheRow.builder()
              .projectId(PROJECT_ID)
              .date(ANCHOR)
              .baseline(
                  "{\"volume\":10,\"volume_with_apdex\":10,\"quality_score\":0.8,"
                      + "\"quality_score_mean\":0.8,\"quality_score_std\":0.05}")
              .segments("[]")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getBaseline()).containsEntry(SessionRcaMetricsRegistry.VOLUME, 10);
      assertThat(result.getSegments()).isEmpty();
      verify(clickhouseQueryService, never())
          .executeRootCauseQuery(anyString(), anyString(), anyList(), anyList());
    }

    @Test
    void shouldTreatBlankBaselineAndSegmentsInCacheAsEmptyCollections() {
      SessionRcaCacheRow row =
          SessionRcaCacheRow.builder()
              .baseline("   ")
              .segments("")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.of(row)));

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getBaseline()).isEmpty();
      assertThat(result.getSegments()).isEmpty();
    }

    @Test
    void shouldRecomputeWhenBaselineJsonInCacheIsInvalid() {
      SessionRcaCacheRow badRow =
          SessionRcaCacheRow.builder()
              .projectId(PROJECT_ID)
              .date(ANCHOR)
              .baseline("not-json")
              .segments("[]")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.of(badRow)));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                if (isSessionBaselineQuery(sql)) {
                  return Single.just(singleRowTableResponse(sessionBaseline(50L, 0.8, 0.05)));
                }
                if (isAggregateLowQualityCountQuery(sql)) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 0L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      verify(clickhouseQueryService, atLeastOnce())
          .executeRootCauseQuery(anyString(), anyString(), anyList(), anyList());
      verify(sessionRcaCacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRecomputeWhenSegmentsJsonInCacheIsInvalid() {
      SessionRcaCacheRow badRow =
          SessionRcaCacheRow.builder()
              .projectId(PROJECT_ID)
              .date(ANCHOR)
              .baseline("{\"volume\":10}")
              .segments("not-json")
              .mode("flat")
              .windowEndUtc(WINDOW_END_LDT)
              .cachedAt(LocalDateTime.now(ZoneOffset.UTC))
              .build();
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.of(badRow)));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                if (isSessionBaselineQuery(sql)) {
                  return Single.just(singleRowTableResponse(sessionBaseline(50L, 0.8, 0.05)));
                }
                if (isAggregateLowQualityCountQuery(sql)) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 0L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      verify(sessionRcaCacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldRecomputeWhenForceRefreshTrue() {
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                if (isSessionBaselineQuery(sql)) {
                  return Single.just(singleRowTableResponse(sessionBaseline(40L, 0.8, 0.05)));
                }
                if (isAggregateLowQualityCountQuery(sql)) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 0L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result =
          service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END, true).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      verify(sessionRcaCacheDao, never()).findByKey(any(), any());
      verify(sessionRcaCacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  class ComputePaths {

    @Test
    void shouldReturnNoDataWhenBaselineQueryReturnsNoRows() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(Single.just(emptyTableResponse()));

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
      verify(sessionRcaCacheDao, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldTreatIncompleteClickhouseJobAsEmptyBaseline() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      GetQueryDataResponseDto<GetRawUserEventsResponseDto> incomplete =
          GetQueryDataResponseDto.<GetRawUserEventsResponseDto>builder().jobComplete(false).build();
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenReturn(Single.just(incomplete));

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
    }

    @Test
    void shouldReturnNoDataWhenBaselineRowHasZeroVolume() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                if (isSessionBaselineQuery(sql)) {
                  return Single.just(singleRowTableResponse(sessionBaseline(0L, 0.8, 0.05)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getNoDataAvailable()).isTrue();
      verify(sessionRcaCacheDao, never()).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturnEverythingGoodWhenVolumePositiveAndZeroLowQualitySessions() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                if (isSessionBaselineQuery(sql)) {
                  return Single.just(singleRowTableResponse(sessionBaseline(200L, 0.8, 0.05)));
                }
                if (isAggregateLowQualityCountQuery(sql)) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 0L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getEverythingGood()).isTrue();
      assertThat(result.getSegments()).isEmpty();
      verify(sessionRcaCacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldFinishWithFlatModeWhenLowQualityExistsButNoSegmentCandidates() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = sessionBaseline(100L, 0.8, 0.05);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                if (isSessionBaselineQuery(sql)) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (isAggregateLowQualityCountQuery(sql)) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 10L)));
                }
                if (isPercentileQuery(sql)) {
                  return Single.just(singleRowTableResponse(Map.of("p20", 0L, "p80", Long.MAX_VALUE)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).isEmpty();
      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      verify(sessionRcaCacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldBuildHierarchicalModeWhenTwoDimensionsMatchSimilarityThreshold() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(2);
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = sessionBaseline(500L, 0.8, 0.05);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(inv -> answerDefaultComputeFlow(baseline, 100L, inv));

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.HIERARCHICAL);
      assertThat(result.getSegments()).isNotEmpty();
      verify(sessionRcaCacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldBuildMultipleFlatSegmentsWhenNoDimensionMatchesSimilarityThreshold() {
      when(rootCauseConfig.getMaxSegments()).thenReturn(2);
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = sessionBaseline(300L, 0.8, 0.05);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(inv -> answerDefaultComputeFlow(baseline, 100L, inv, 50L));

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
      assertThat(result.getSegments()).hasSize(2);
      assertThat(result.getSegments().get(0).getLabel()).isEqualTo("platform: Android");
      assertThat(result.getSegments().get(1).getLabel()).isEqualTo("osVersion: 14");
      verify(sessionRcaCacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldUseHybridDimensionOrderingWhenEnabled() {
      when(rootCauseConfig.isHybridDimensionOrderingEnabled()).thenReturn(true);
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = sessionBaseline(300L, 0.8, 0.05);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(inv -> answerDefaultComputeFlow(baseline, 100L, inv));

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getMode()).isNotNull();
      verify(sessionRcaCacheDao).upsert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldExcludeSegmentBelowMinVolumePct() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = sessionBaseline(500L, 0.8, 0.05);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                @SuppressWarnings("unchecked")
                List<Object> bindValues = inv.getArgument(3, List.class);
                int bindCount = bindValues.size();

                if (isSessionBaselineQuery(sql)) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (isAggregateLowQualityCountQuery(sql)) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 100L)));
                }
                if (isPercentileQuery(sql)) {
                  return Single.just(singleRowTableResponse(Map.of("p20", 1000L, "p80", 5000L)));
                }
                if (isSessionSegmentMetricsQuery(sql)
                    && sql.contains("GROUP BY platform")
                    && bindCount == 4) {
                  return Single.just(
                      singleRowTableResponse(
                          sessionSegmentMetricRow("platform", "Android", 1L, 0.2)));
                }
                if (sql.contains("GROUP BY platform") && bindCount == 4) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("platform", "Android", SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 10L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).isEmpty();
      assertThat(result.getMode()).isEqualTo(RootCauseAnalysisMode.FLAT);
    }

    @Test
    void shouldMarkCriticalImpactWhenZScoreBelowMinusTwo() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = sessionBaseline(500L, 0.8, 0.05);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                @SuppressWarnings("unchecked")
                List<Object> bindValues = inv.getArgument(3, List.class);
                int bindCount = bindValues.size();

                if (isSessionBaselineQuery(sql)) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (isAggregateLowQualityCountQuery(sql)) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 100L)));
                }
                if (isPercentileQuery(sql)) {
                  return Single.just(singleRowTableResponse(Map.of("p20", 1000L, "p80", 5000L)));
                }
                if (isExampleSessionsQuery(sql)) {
                  return Single.just(
                      singleRowTableResponse(Map.of("sessionId", "session-1")));
                }
                if (isDegradingInteractionsQuery(sql)) {
                  return Single.just(emptyTableResponse());
                }
                if (isSessionSegmentMetricsQuery(sql)
                    && sql.contains("GROUP BY platform")
                    && bindCount == 4) {
                  return Single.just(
                      singleRowTableResponse(
                          sessionSegmentMetricRow("platform", "Android", 50L, 0.1)));
                }
                if (sql.contains("GROUP BY platform") && bindCount == 4) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("platform", "Android", SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 10L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).hasSize(1);
      assertThat(result.getSegments().get(0).getMetrics())
          .containsEntry(SessionRcaMetricsRegistry.IMPACT, SessionRcaMetricsRegistry.IMPACT_CRITICAL);
      assertThat(result.getSegments().get(0).getExampleSessionIds()).containsExactly("session-1");
    }

    @Test
    void shouldUseRelativeImpactRuleWhenQualityStdIsNearZero() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = sessionBaseline(500L, 0.8, 0.0);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                @SuppressWarnings("unchecked")
                List<Object> bindValues = inv.getArgument(3, List.class);
                int bindCount = bindValues.size();

                if (isSessionBaselineQuery(sql)) {
                  return Single.just(singleRowTableResponse(baseline));
                }
                if (isAggregateLowQualityCountQuery(sql)) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 100L)));
                }
                if (isPercentileQuery(sql)) {
                  return Single.just(singleRowTableResponse(Map.of("p20", 1000L, "p80", 5000L)));
                }
                if (isExampleSessionsQuery(sql)) {
                  return Single.just(emptyTableResponse());
                }
                if (isSessionSegmentMetricsQuery(sql)
                    && sql.contains("GROUP BY platform")
                    && bindCount == 4) {
                  return Single.just(
                      singleRowTableResponse(
                          sessionSegmentMetricRow("platform", "Android", 50L, 0.5)));
                }
                if (sql.contains("GROUP BY platform") && bindCount == 4) {
                  return Single.just(
                      singleRowTableResponse(
                          Map.of("platform", "Android", SessionRcaMetricsRegistry.LOW_QUALITY_COUNT, 10L)));
                }
                return Single.just(emptyTableResponse());
              });

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).hasSize(1);
      assertThat(result.getSegments().get(0).getMetrics())
          .containsEntry(SessionRcaMetricsRegistry.IMPACT, SessionRcaMetricsRegistry.IMPACT_CRITICAL);
    }

    @Test
    void shouldAttachDegradingInteractionsWhenSegmentQualityBelowBaseline() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = sessionBaseline(500L, 0.8, 0.05);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(inv -> answerDefaultComputeFlow(baseline, 100L, inv));

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).isNotEmpty();
      DegradingInteraction interaction =
          result.getSegments().get(0).getDegradingInteractions().get(0);
      assertThat(interaction.getInteractionName()).isEqualTo("checkout");
      assertThat(interaction.getInteractionCount()).isEqualTo(3L);
    }

    @Test
    void shouldContinueWhenDegradingInteractionsQueryFails() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = sessionBaseline(500L, 0.8, 0.05);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                if (isDegradingInteractionsQuery(sql)) {
                  return Single.error(new RuntimeException("clickhouse unavailable"));
                }
                return answerDefaultComputeFlow(baseline, 100L, inv);
              });

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).isNotEmpty();
      assertThat(result.getSegments().get(0).getDegradingInteractions()).isNull();
    }

    @Test
    void shouldReturnSegmentWithoutEvidenceWhenExampleSessionsQueryFails() {
      when(sessionRcaCacheDao.findByKey(PROJECT_ID, ANCHOR))
          .thenReturn(Single.just(Optional.empty()));
      Map<String, Object> baseline = sessionBaseline(500L, 0.8, 0.05);
      when(clickhouseQueryService.executeRootCauseQuery(anyString(), anyString(), anyList(), anyList()))
          .thenAnswer(
              inv -> {
                String sql = inv.getArgument(1, String.class);
                if (isExampleSessionsQuery(sql)) {
                  return Single.error(new RuntimeException("example sessions failed"));
                }
                return answerDefaultComputeFlow(baseline, 100L, inv);
              });

      RootCauseResult result = service.getSessionRca(PROJECT_ID, ANCHOR, WINDOW_END).blockingGet();

      assertThat(result.getSegments()).isNotEmpty();
      assertThat(result.getSegments().get(0).getExampleSessionIds()).isEmpty();
    }
  }
}
