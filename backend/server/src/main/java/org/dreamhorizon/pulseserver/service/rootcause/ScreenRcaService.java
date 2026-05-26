package org.dreamhorizon.pulseserver.service.rootcause;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.ws.rs.WebApplicationException;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.ScreenRootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.ScreenRootCauseCacheRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperUtil;

/**
 * Screen-scoped RCA over {@code app.click} logs (same segmentation algorithm as {@link RootCauseService},
 * driver metric {@link ScreenRcaQueryBuilder#BAD_FRUSTRATION}). Read-through cache in {@code
 * otel.screen_root_cause_cache} keyed like interaction {@code root_cause_cache}: project, entity name
 * ({@code screen_name}), and anchor {@code date}.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ScreenRcaService {

  private static final String CACHE_FIELD_BASELINE = "baseline";
  private static final String CACHE_FIELD_SEGMENTS = "segments";
  private static final String CACHE_PARSE_FAILED_DETAIL =
      "ClickHouse screen_root_cause_cache row has invalid JSON in %s";

  private final RootCauseConfig config;
  private final ClickhouseQueryService clickhouseQueryService;
  private final ScreenRootCauseCacheDao screenRootCauseCacheDao;
  private final ObjectMapperUtil objectMapperUtil;

  /**
   * Screen RCA for the same window as interaction RCA: {@code lookbackDays} ending at {@code windowEndExclusiveUtc}
   * (see {@link RootCauseQueryBuilder.Window}).
   */
  public Single<RootCauseResult> getScreenRootCause(
      String projectId,
      String screenName,
      LocalDate anchorDateUtc,
      Instant windowEndExclusiveUtc) {
    return getScreenRootCause(
        projectId, screenName, anchorDateUtc, windowEndExclusiveUtc, false);
  }

  /**
   * @param forceRefresh when true, skips {@code screen_root_cause_cache} read and recomputes
   */
  public Single<RootCauseResult> getScreenRootCause(
      String projectId,
      String screenName,
      LocalDate anchorDateUtc,
      Instant windowEndExclusiveUtc,
      boolean forceRefresh) {
    final RootCauseQueryBuilder.Window window;
    try {
      window =
          new RootCauseQueryBuilder.Window(anchorDateUtc, config.getLookbackDays(), windowEndExclusiveUtc);
    } catch (IllegalArgumentException e) {
      return Single.error(ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(e.getMessage()));
    }
    return readThroughCache(projectId, screenName, anchorDateUtc, window, forceRefresh);
  }

  private Single<RootCauseResult> readThroughCache(
      String projectId,
      String screenName,
      LocalDate anchorDateUtc,
      RootCauseQueryBuilder.Window window,
      boolean forceRefresh) {
    if (forceRefresh) {
      return computeAndPersistCache(projectId, screenName, anchorDateUtc, window);
    }
    return screenRootCauseCacheDao
        .findByKey(projectId, screenName, anchorDateUtc)
        .flatMap(
            opt -> {
              if (opt.isEmpty()) {
                return computeAndPersistCache(projectId, screenName, anchorDateUtc, window);
              }
              try {
                return Single.just(fromCacheRow(opt.get()));
              } catch (WebApplicationException e) {
                log.warn(
                    "screen_root_cause_cache invalid row for project={}, screen={}, date={}: {}",
                    projectId,
                    screenName,
                    anchorDateUtc,
                    e.getMessage());
                return computeAndPersistCache(projectId, screenName, anchorDateUtc, window);
              }
            });
  }

  private Single<RootCauseResult> computeAndPersistCache(
      String projectId,
      String screenName,
      LocalDate anchorDateUtc,
      RootCauseQueryBuilder.Window window) {
    return compute(projectId, screenName, window)
        .flatMap(
            result -> {
              if (result.getNoDataAvailable() != null && result.getNoDataAvailable()) {
                return Single.just(result);
              }
              RootCauseAnalysisMode mergeMode = result.getMode();
              List<RootCauseSegment> gated =
                  applySignalGate(result.getSegments(), result.getBaseline(), screenName);
              if (gated != result.getSegments()) {
                result =
                    result
                        .toBuilder()
                        .segments(gated)
                        .mode(RootCauseAnalysisMode.forSegmentShapeAfterGate(gated))
                        .build();
              }
              if (log.isDebugEnabled()) {
                log.debug(
                    "[SCREEN-RCA-SEGMENT] RootCauseAnalysisMode after pipeline: screen={}, mergeMode={}, "
                        + "finalMode={} (matches payload/cache), segmentCount={}",
                    screenName,
                    mergeMode,
                    result.getMode(),
                    result.getSegments() == null ? 0 : result.getSegments().size());
              }
              String baselineJson = objectMapperUtil.writeValueAsString(result.getBaseline());
              String segmentsJson = objectMapperUtil.writeValueAsString(result.getSegments());
              RootCauseAnalysisMode modeForCache =
                  result.getMode() != null ? result.getMode() : RootCauseAnalysisMode.FLAT;
              LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
              return screenRootCauseCacheDao
                  .upsert(
                      projectId,
                      screenName,
                      anchorDateUtc,
                      window.endExclusive,
                      modeForCache.getWireValue(),
                      baselineJson,
                      segmentsJson,
                      now)
                  .andThen(
                      Single.just(
                          result.toBuilder()
                              .cachedAt(now.atZone(ZoneOffset.UTC).toInstant())
                              .build()));
            });
  }

  private RootCauseResult fromCacheRow(ScreenRootCauseCacheRow row) {
    Map<String, Object> baseline = parseJsonMapOrThrow(row, row.getBaseline(), CACHE_FIELD_BASELINE);
    enrichDerivedMetrics(baseline);
    List<RootCauseSegment> segments =
        parseJsonSegmentsOrThrow(row, row.getSegments(), CACHE_FIELD_SEGMENTS).stream()
            .map(seg -> enrichSegmentFromCache(baseline, seg))
            .toList();
    return RootCauseResult.builder()
        .baseline(baseline)
        .segments(segments)
        .mode(RootCauseAnalysisMode.fromWireValue(row.getMode()))
        .cachedAt(row.getCachedAt().atZone(ZoneOffset.UTC).toInstant())
        .build();
  }

  private static RootCauseSegment enrichSegmentFromCache(
      Map<String, Object> baseline, RootCauseSegment segment) {
    if (segment == null) {
      return null;
    }
    Map<String, Object> metrics =
        segment.getMetrics() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(segment.getMetrics());
    enrichDerivedMetrics(metrics);
    Map<String, Double> deltas =
        computeScreenDeltas(baseline, metrics, segment.getDeltas() == null ? Map.of() : segment.getDeltas());
    return segment.toBuilder().metrics(metrics).deltas(deltas).build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseJsonMapOrThrow(
      ScreenRootCauseCacheRow cacheRow, String json, String fieldName) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> parsed = objectMapperUtil.readValue(json, Map.class);
      if (parsed == null) {
        throw new IllegalStateException("parsed map is null");
      }
      return parsed;
    } catch (Exception e) {
      throw screenRootCauseCacheJsonInvalid(cacheRow, fieldName, e);
    }
  }

  private List<RootCauseSegment> parseJsonSegmentsOrThrow(
      ScreenRootCauseCacheRow cacheRow, String json, String fieldName) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<?> list = objectMapperUtil.readValue(json, List.class);
      if (list == null) {
        throw new IllegalStateException("parsed list is null");
      }
      return list.stream()
          .map(m -> objectMapperUtil.convertValue(m, RootCauseSegment.class))
          .toList();
    } catch (Exception e) {
      throw screenRootCauseCacheJsonInvalid(cacheRow, fieldName, e);
    }
  }

  private WebApplicationException screenRootCauseCacheJsonInvalid(
      ScreenRootCauseCacheRow cacheRow, String fieldName, Exception cause) {
    log.error(
        "Invalid screen_root_cause_cache JSON ({}): projectId={} screenName={} date={} — {}",
        String.format(CACHE_PARSE_FAILED_DETAIL, fieldName),
        cacheRow.getProjectId(),
        cacheRow.getScreenName(),
        cacheRow.getDate(),
        cause.getMessage(),
        cause);
    return ServiceError.INTERNAL_SERVER_ERROR.getException();
  }

  /**
   * After merge+cap: keep segments whose {@code bad_frustration / click_volume} strictly exceeds the
   * same ratio on the screen baseline (aligned with interaction RCA’s rate gate). Preserves order of
   * kept segments. Returns the input list unchanged when no rows are dropped.
   */
  private List<RootCauseSegment> applySignalGate(
      List<RootCauseSegment> segments, Map<String, Object> baseline, String screenName) {
    if (segments == null || segments.isEmpty()) {
      return segments;
    }
    String badKey = ScreenRcaQueryBuilder.BAD_FRUSTRATION;
    String volKey = ScreenRcaQueryBuilder.CLICK_VOLUME;
    List<RootCauseSegment> kept =
        SegmentSignalGate.filterSegmentsRateAboveBaseline(segments, baseline, badKey, volKey);
    if (kept.size() == segments.size()) {
      return segments;
    }
    double baselineRate =
        SegmentSignalGate.metricRate(baseline, badKey, volKey).orElse(Double.NaN);
    if (log.isDebugEnabled()) {
      for (RootCauseSegment s : segments) {
        if (!kept.contains(s)) {
          double segRate =
              SegmentSignalGate.metricRate(s.getMetrics(), badKey, volKey).orElse(Double.NaN);
          log.debug(
              "[SCREEN-RCA-SEGMENT] Drop segment at or below baseline bad_frustration rate: screen={}, label={}, "
                  + "segmentRate={}, baselineRate={}",
              screenName,
              s.getLabel(),
              segRate,
              baselineRate);
        }
      }
    }
    log.info(
        "[SCREEN-RCA-SEGMENT] Signal gate filtered segments (slice vs baseline {}/{}): screen={}, kept={}/{}, baselineRate={}",
        badKey,
        volKey,
        screenName,
        kept.size(),
        segments.size(),
        baselineRate);
    return kept;
  }

  private Single<RootCauseResult> compute(
      String projectId, String screenName, RootCauseQueryBuilder.Window window) {
    return runBaseline(projectId, screenName, window)
        .flatMap(baselineRowOpt -> {
          if (baselineRowOpt.isEmpty()) {
            return Single.just(RootCauseResult.builder()
                .noDataAvailable(true)
                .message("No data available")
                .baseline(Map.of())
                .segments(List.of())
                .build());
          }
          Map<String, Object> baselineRow = baselineRowOpt.get();
          long volume = NumberCoercionUtils.toLong(baselineRow.get(ScreenRcaQueryBuilder.CLICK_VOLUME));
          if (volume == 0) {
            return Single.just(RootCauseResult.builder()
                .noDataAvailable(true)
                .message("No data available")
                .baseline(toBaselineMap(baselineRow))
                .segments(List.of())
                .build());
          }
          long totalBad = NumberCoercionUtils.toLong(baselineRow.get(ScreenRcaQueryBuilder.BAD_FRUSTRATION));
          Map<String, Object> baseline = toBaselineMap(baselineRow);
          if (totalBad == 0) {
            return Single.just(RootCauseResult.builder()
                .everythingGood(true)
                .message("Everything is good")
                .baseline(baseline)
                .segments(List.of())
                .mode(RootCauseAnalysisMode.FLAT)
                .build());
          }
          return runAlgorithm(projectId, screenName, window, baseline, totalBad)
              .map(outcome -> RootCauseResult.builder()
                  .baseline(baseline)
                  .segments(outcome.segments())
                  .mode(outcome.mode())
                  .build());
        });
  }

  private record SegmentsWithMode(List<RootCauseSegment> segments, RootCauseAnalysisMode mode) {}

  private Single<Optional<Map<String, Object>>> runBaseline(
      String projectId, String screenName, RootCauseQueryBuilder.Window window) {
    RootCauseQuerySpec query =
        ScreenRcaQueryBuilder.buildBaselineQuery(
            projectId, screenName, window.startInclusive, window.endExclusive);
    return executeQuery(projectId, query)
        .map(rows -> rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0)));
  }

  private Single<SegmentsWithMode> runAlgorithm(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      long totalBad) {
    double threshold = totalBad * (config.getSimilarityThresholdPct() / 100.0);
    int maxSegments = config.getMaxSegments();
    boolean hybridEnabled = config.isHybridDimensionOrderingEnabled();

    log.debug(
        "[SCREEN-RCA-SEGMENT] Algorithm start: screen={}, totalBad={}, threshold={} ({}%), maxSegments={}, hybridEnabled={}",
        screenName,
        totalBad,
        threshold,
        config.getSimilarityThresholdPct(),
        maxSegments,
        hybridEnabled);

    Single<List<String>> dimOrderSingle =
        hybridEnabled
            ? computeHybridDimensionOrder(
                projectId, screenName, window, config.getDimensionOrder(), threshold)
            : Single.just(config.getDimensionOrder());

    return dimOrderSingle.flatMap(dimOrder -> {
      log.info("[SCREEN-RCA-SEGMENT] Dimension order: {} (hybridEnabled={})", dimOrder, hybridEnabled);
      return buildFlatSegments(projectId, screenName, window, baseline, dimOrder, maxSegments)
          .flatMap(flatCandidates ->
              pickFirstDimension(projectId, screenName, window, dimOrder, threshold, totalBad)
                  .flatMap(optFirst -> {
                    if (optFirst.isEmpty()) {
                      log.debug("[SCREEN-RCA-SEGMENT] No first dimension picked, returning flat-only");
                      return Single.just(new SegmentsWithMode(flatCandidates, RootCauseAnalysisMode.FLAT));
                    }
                    FirstDimensionPick first = optFirst.get();
                    return buildHierarchicalCandidates(
                            projectId, screenName, window, baseline, dimOrder,
                            maxSegments, totalBad, threshold,
                            first.dimOrderIndex(), List.of(first.path()))
                        .map(hierarchicalCandidates -> {
                          RcaHybridMergeOutcome.Result out =
                              RcaHybridMergeOutcome.mergeForScreen(
                                  "[SCREEN-RCA-SEGMENT]",
                                  baseline,
                                  hierarchicalCandidates,
                                  flatCandidates,
                                  dimOrder,
                                  maxSegments);
                          return new SegmentsWithMode(out.segments(), out.mode());
                        });
                  }));
    });
  }

  private Single<List<String>> computeHybridDimensionOrder(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      List<String> baseOrder,
      double strongSignalThreshold) {
    if (baseOrder.isEmpty()) {
      return Single.just(List.of());
    }
    log.debug(
        "[SCREEN-RCA-SEGMENT] Hybrid order computation start: screen={}, threshold={}",
        screenName,
        strongSignalThreshold);
    List<Single<Map.Entry<String, Long>>> maxQueries =
        baseOrder.stream()
            .map(
                dim ->
                    getMaxBadFrustrationForDimension(projectId, screenName, window, dim)
                        .map(max -> Map.entry(dim, max)))
            .toList();
    return Single.zip(
        maxQueries,
        results -> {
          Map<String, Long> dimMaxMap = new HashMap<>();
          for (Object r : results) {
            @SuppressWarnings("unchecked")
            Map.Entry<String, Long> e = (Map.Entry<String, Long>) r;
            dimMaxMap.put(e.getKey(), e.getValue());
          }
          List<String> order =
              RootCauseService.hybridDimensionOrderFromPrecomputedMaxes(
                  baseOrder, dimMaxMap, strongSignalThreshold);
          log.debug(
              "[SCREEN-RCA-SEGMENT] Hybrid order computed: baseOrder={}, dimMaxMap={}, threshold={},"
                  + " finalOrder={}",
              baseOrder,
              dimMaxMap,
              strongSignalThreshold,
              order);
          return order;
        });
  }

  private Single<Long> getMaxBadFrustrationForDimension(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      String dimension) {
    RootCauseQuerySpec spec =
        ScreenRcaQueryBuilder.buildBadFrustrationByDimensionQuery(
            projectId,
            screenName,
            window.startInclusive,
            window.endExclusive,
            dimension,
            null);
    return executeQuery(projectId, spec)
        .map(
            rows ->
                rows.stream()
                    .mapToLong(
                        r ->
                            NumberCoercionUtils.toLong(
                                r.get(ScreenRcaQueryBuilder.BAD_FRUSTRATION)))
                    .max()
                    .orElse(0L));
  }

  private Single<Optional<FirstDimensionPick>> pickFirstDimension(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      List<String> dimOrder,
      double threshold,
      long totalBad) {
    return Observable.range(0, dimOrder.size())
        .concatMapMaybe(i -> {
          String dim = dimOrder.get(i);
          RootCauseQuerySpec q =
              ScreenRcaQueryBuilder.buildBadFrustrationByDimensionQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive, dim, null);
          return executeQuery(projectId, q)
              .flatMapMaybe(rows -> {
                Optional<SegmentPath> path = pickClosestToTotal(rows, dim, totalBad, threshold);
                return path.map(p -> Maybe.just(new FirstDimensionPick(i, p))).orElseGet(Maybe::empty);
              });
        })
        .firstElement()
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty());
  }

  private Single<List<RootCauseSegment>> buildFlatSegments(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<String> dimOrder,
      int maxSegments) {
    return buildFlatSegmentsFromIndex(
        projectId, screenName, window, baseline, dimOrder, maxSegments, 0, new ArrayList<>());
  }

  private Single<List<RootCauseSegment>> buildFlatSegmentsFromIndex(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<String> dimOrder,
      int maxSegments,
      int index,
      List<RootCauseSegment> accumulated) {
    if (accumulated.size() >= maxSegments || index >= dimOrder.size()) {
      return Single.just(accumulated);
    }
    String dim = dimOrder.get(index);
    RootCauseQuerySpec q =
        ScreenRcaQueryBuilder.buildBadFrustrationByDimensionQuery(
            projectId, screenName, window.startInclusive, window.endExclusive, dim, null);
    return executeQuery(projectId, q).flatMap(rows -> {
      Optional<Map.Entry<String, Long>> top = rows.stream()
          .map(r -> {
            Object raw = r.get(dim);
            String dimValue = normalizeDimensionValue(raw);
            return Map.entry(dimValue, NumberCoercionUtils.toLong(r.get(ScreenRcaQueryBuilder.BAD_FRUSTRATION)));
          })
          .filter(e -> e.getValue() > 0)
          .max(Map.Entry.comparingByValue());
      if (top.isEmpty()) {
        return buildFlatSegmentsFromIndex(
            projectId, screenName, window, baseline, dimOrder, maxSegments, index + 1, accumulated);
      }
      String value = top.get().getKey();
      Map<String, String> filters = Map.of(dim, value);
      String label = dim + ": " + value;
      return fetchSegmentMetrics(projectId, screenName, window, baseline, label, filters)
          .flatMap(optSeg -> {
            List<RootCauseSegment> next = new ArrayList<>(accumulated);
            optSeg.ifPresent(next::add);
            if (next.size() >= maxSegments) {
              return Single.just(next);
            }
            return buildFlatSegmentsFromIndex(
                projectId, screenName, window, baseline, dimOrder, maxSegments, index + 1, next);
          });
    });
  }

  /**
   * Drills the hierarchical path and returns only materialized segments with ≥ 2 dimensions.
   * Stops when the similarity threshold is not met — no flat extras (global flat pass owns 1D).
   */
  private Single<List<RootCauseSegment>> buildHierarchicalCandidates(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<String> dimOrder,
      int maxSegments,
      long totalBad,
      double threshold,
      int hierarchyStartDimIndex,
      List<SegmentPath> path) {
    if (path.size() >= maxSegments) {
      return materializeHierarchicalSegments(projectId, screenName, window, baseline, path);
    }
    Map<String, String> currentFilters =
        path.stream()
            .collect(
                Collectors.toMap(
                    SegmentPath::dimension,
                    SegmentPath::value,
                    (a, b) -> b,
                    LinkedHashMap::new));
    int nextDimIndex = hierarchyStartDimIndex + path.size();
    if (nextDimIndex >= dimOrder.size()) {
      return materializeHierarchicalSegments(projectId, screenName, window, baseline, path);
    }
    String nextDim = dimOrder.get(nextDimIndex);
    RootCauseQuerySpec q =
        ScreenRcaQueryBuilder.buildBadFrustrationByDimensionQuery(
            projectId, screenName, window.startInclusive, window.endExclusive, nextDim, currentFilters);
    return executeQuery(projectId, q)
        .flatMap(rows -> {
          Optional<SegmentPath> picked = pickClosestToTotal(rows, nextDim, totalBad, threshold);
          if (picked.isEmpty()) {
            return materializeHierarchicalSegments(projectId, screenName, window, baseline, path);
          }
          List<SegmentPath> newPath = new ArrayList<>(path);
          newPath.add(picked.get());
          return buildHierarchicalCandidates(
              projectId, screenName, window, baseline, dimOrder, maxSegments,
              totalBad, threshold, hierarchyStartDimIndex, newPath);
        });
  }

  /** Materializes the progressive path slices, keeping only segments with ≥ 2 dimensions. */
  private Single<List<RootCauseSegment>> materializeHierarchicalSegments(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<SegmentPath> path) {
    return materializeSegmentsFromIndex(
        projectId, screenName, window, baseline, path, 0, new LinkedHashMap<>(), new ArrayList<>())
        .map(segs -> segs.stream()
            .filter(s -> s.getDimensions() != null && s.getDimensions().size() >= 2)
            .toList());
  }

  private Single<List<RootCauseSegment>> materializeSegmentsFromIndex(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<SegmentPath> path,
      int index,
      LinkedHashMap<String, String> acc,
      List<RootCauseSegment> segments) {
    if (index >= path.size()) {
      return Single.just(segments);
    }
    SegmentPath p = path.get(index);
    LinkedHashMap<String, String> nextAcc;
    if (p.isFlatExtra) {
      nextAcc = new LinkedHashMap<>();
    } else {
      nextAcc = new LinkedHashMap<>(acc);
    }
    nextAcc.put(p.dimension, p.value);
    String label;
    if (p.isFlatExtra) {
      label = p.dimension + ": " + p.value;
    } else {
      label = path.size() == 1
          ? p.dimension + ": " + p.value
          : String.join(" + ", nextAcc.values());
    }
    return fetchSegmentMetrics(
            projectId, screenName, window, baseline, label, Map.copyOf(nextAcc))
        .flatMap(opt -> {
          List<RootCauseSegment> nextSegs = new ArrayList<>(segments);
          opt.ifPresent(nextSegs::add);
          return materializeSegmentsFromIndex(
              projectId, screenName, window, baseline, path, index + 1, nextAcc, nextSegs);
        });
  }

  private Single<Optional<RootCauseSegment>> fetchSegmentMetrics(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      String label,
      Map<String, String> dimensionFilters) {
    List<String> dims = new ArrayList<>(dimensionFilters.keySet());
    RootCauseQuerySpec q =
        ScreenRcaQueryBuilder.buildSegmentQuery(
            projectId, screenName, window.startInclusive, window.endExclusive, dims, dimensionFilters);
    return executeQuery(projectId, q)
        .map(rows -> {
          if (rows.isEmpty()) {
            return Optional.<RootCauseSegment>empty();
          }
          Map<String, Object> row = rows.get(0);
          Map<String, Object> metrics = new LinkedHashMap<>(row);
          enrichDerivedMetrics(metrics);
          Map<String, Double> deltas = computeScreenDeltas(baseline, metrics);
          RootCauseSegment segment = RootCauseSegment.builder()
              .label(label)
              .dimensions(new LinkedHashMap<>(dimensionFilters))
              .metrics(metrics)
              .deltas(deltas)
              .build();
          return Optional.of(segment);
        });
  }

  private Optional<SegmentPath> pickClosestToTotal(
      List<Map<String, Object>> rows,
      String dimensionColumn,
      long totalBad,
      double threshold) {
    SegmentPath best = null;
    long bestDiff = Long.MAX_VALUE;
    for (Map<String, Object> row : rows) {
      long count = NumberCoercionUtils.toLong(row.get(ScreenRcaQueryBuilder.BAD_FRUSTRATION));
      if (count < threshold) {
        continue;
      }
      long diff = Math.abs(count - totalBad);
      if (diff < bestDiff) {
        bestDiff = diff;
        Object val = row.get(dimensionColumn);
        best = new SegmentPath(dimensionColumn, normalizeDimensionValue(val), false);
      }
    }
    return Optional.ofNullable(best);
  }

  private static String normalizeDimensionValue(Object raw) {
    if (raw == null) {
      return ScreenRcaQueryBuilder.UNKNOWN_DIMENSION;
    }
    String s = raw.toString().strip();
    return s.isEmpty() ? ScreenRcaQueryBuilder.UNKNOWN_DIMENSION : s;
  }

  private static Map<String, Double> computeScreenDeltas(
      Map<String, Object> baseline, Map<String, Object> segment) {
    return computeScreenDeltas(baseline, segment, Map.of());
  }

  /**
   * Relative % change vs baseline for each screen RCA metric. Preserves unrelated keys in {@code existingDeltas}
   * when backfilling cache rows.
   */
  private static Map<String, Double> computeScreenDeltas(
      Map<String, Object> baseline,
      Map<String, Object> segment,
      Map<String, Double> existingDeltas) {
    Map<String, Double> deltas = new LinkedHashMap<>(existingDeltas);
    for (String metric : screenRcaMetricKeys()) {
      Object b = baseline.get(metric);
      Object s = segment.get(metric);
      if (b == null || s == null) {
        continue;
      }
      double bv = NumberCoercionUtils.toDouble(b);
      double sv = NumberCoercionUtils.toDouble(s);
      if (metric.equals(ScreenRcaQueryBuilder.CLICK_VOLUME)) {
        if (bv != 0) {
          deltas.put(metric, (sv / bv) * 100 - 100);
        }
      } else {
        if (bv != 0) {
          deltas.put(metric, ((sv - bv) / bv) * 100);
        }
      }
    }
    return deltas;
  }

  /**
   * {@code bad_frustration / click_volume * 100} when volume &gt; 0. Omits the key when undefined (both zero).
   */
  static void enrichDerivedMetrics(Map<String, Object> metrics) {
    if (metrics == null || metrics.isEmpty()) {
      return;
    }
    SegmentSignalGate.metricRate(
            metrics,
            ScreenRcaQueryBuilder.BAD_FRUSTRATION,
            ScreenRcaQueryBuilder.CLICK_VOLUME)
        .ifPresent(
            rate -> {
              if (Double.isFinite(rate)) {
                metrics.put(
                    ScreenRcaQueryBuilder.BAD_FRUSTRATION_PERCENTAGE, rate * 100.0);
              }
            });
  }

  private static List<String> screenRcaRawMetricKeys() {
    return List.of(
        ScreenRcaQueryBuilder.CLICK_VOLUME,
        ScreenRcaQueryBuilder.TAP_COUNT,
        ScreenRcaQueryBuilder.RAGE_COUNT,
        ScreenRcaQueryBuilder.DEAD_COUNT,
        ScreenRcaQueryBuilder.BAD_FRUSTRATION);
  }

  private static List<String> screenRcaMetricKeys() {
    List<String> keys = new ArrayList<>(screenRcaRawMetricKeys());
    keys.add(ScreenRcaQueryBuilder.BAD_FRUSTRATION_PERCENTAGE);
    return keys;
  }

  private static Map<String, Object> toBaselineMap(Map<String, Object> row) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (String key : screenRcaRawMetricKeys()) {
      if (row.containsKey(key)) {
        m.put(key, row.get(key));
      }
    }
    enrichDerivedMetrics(m);
    return m;
  }

  private Single<List<Map<String, Object>>> executeQuery(String projectId, RootCauseQuerySpec spec) {
    return clickhouseQueryService
        .executeRootCauseQuery(projectId, spec.sql(), spec.bindNames(), spec.bindValues())
        .map(this::rowsToMaps);
  }

  private List<Map<String, Object>> rowsToMaps(GetQueryDataResponseDto<GetRawUserEventsResponseDto> response) {
    if (!response.isJobComplete() || response.getData() == null) {
      return List.of();
    }
    GetRawUserEventsResponseDto data = response.getData();
    List<String> names = data.getSchema().getFields().stream()
        .map(GetRawUserEventsResponseDto.Field::getName)
        .toList();
    List<Map<String, Object>> out = new ArrayList<>();
    for (GetRawUserEventsResponseDto.Row row : data.getRows()) {
      Map<String, Object> m = new LinkedHashMap<>();
      for (int i = 0; i < names.size(); i++) {
        Object v = i < row.getRowFields().size() ? row.getRowFields().get(i).getValue() : null;
        m.put(names.get(i), v);
      }
      out.add(m);
    }
    return out;
  }

  private record FirstDimensionPick(int dimOrderIndex, SegmentPath path) {}

  private record SegmentPath(String dimension, String value, boolean isFlatExtra) {}
}
