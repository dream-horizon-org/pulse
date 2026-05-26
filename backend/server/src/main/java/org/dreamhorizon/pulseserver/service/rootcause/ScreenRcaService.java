package org.dreamhorizon.pulseserver.service.rootcause;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.ws.rs.WebApplicationException;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.constant.ClickhouseConstants;
import org.dreamhorizon.pulseserver.dao.rootcause.ScreenRootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.ScreenRootCauseCacheRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.service.rootcause.models.ScreenRcaEvidences;
import org.dreamhorizon.pulseserver.service.rootcause.models.ScreenRcaMetrics;
import org.dreamhorizon.pulseserver.service.rootcause.models.ScreenRcaProblemResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.ScreenRcaSpecificIssue;
import org.dreamhorizon.pulseserver.service.rootcause.models.ScreenRcaV2Response;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperUtil;

/**
 * Screen-scoped RCA over {@code app.click} logs (same segmentation algorithm as {@link RootCauseService},
 * driver metric {@code affected_user_count} for bad-click segmentation). Read-through cache in {@code
 * otel.screen_root_cause_cache} keyed like interaction {@code root_cause_cache}: project, entity name
 * ({@code screen_name}), and anchor {@code date}.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ScreenRcaService {

  private static final ScreenRcaProblemResult SKIP = ScreenRcaProblemResult.builder().build();

  private static final String CACHE_FIELD_BASELINE = "baseline";
  private static final String CACHE_FIELD_SEGMENTS = "segments";
  private static final String CACHE_PARSE_FAILED_DETAIL =
      "ClickHouse screen_root_cause_cache row has invalid JSON in %s";

  private final RootCauseConfig config;
  private final ClickhouseQueryService clickhouseQueryService;
  private final ScreenRootCauseCacheDao screenRootCauseCacheDao;
  private final ObjectMapperUtil objectMapperUtil;

  /**
   * Screen RCA v2: Returns ranked list of problems across all 9 problem types.
   * Uses explicit window (Instant-based) for flexible time ranges.
   */
  public Single<ScreenRcaV2Response> getScreenRootCauseV2(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window) {
    return Single.zip(
        computeAllProblems(projectId, screenName, window).map(this::rankProblems),
        fetchTopAffectedSessions(projectId, screenName, window),
        checkHeatmapAvailable(projectId, screenName, window),
        (problems, sessions, heatmapAvailable) ->
            ScreenRcaV2Response.builder()
                .problems(problems)
                .evidences(ScreenRcaEvidences.builder()
                    .sessions(sessions)
                    .heatmapAvailable(heatmapAvailable)
                    .build())
                .build()
    );
  }

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
    List<RootCauseSegment> segments =
        parseJsonSegmentsOrThrow(row, row.getSegments(), CACHE_FIELD_SEGMENTS);
    return RootCauseResult.builder()
        .baseline(baseline)
        .segments(segments)
        .mode(RootCauseAnalysisMode.fromWireValue(row.getMode()))
        .cachedAt(row.getCachedAt().atZone(ZoneOffset.UTC).toInstant())
        .build();
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
          long totalBad =
              NumberCoercionUtils.toLong(baselineRow.get("affected_user_count"));
          if (totalBad == 0) {
            return Single.just(RootCauseResult.builder()
                .everythingGood(true)
                .message("Everything is good")
                .baseline(toBaselineMap(baselineRow))
                .segments(List.of())
                .mode(RootCauseAnalysisMode.FLAT)
                .build());
          }
          return runAlgorithm(projectId, screenName, window, baselineRow, totalBad)
              .map(outcome -> RootCauseResult.builder()
                  .baseline(toBaselineMap(baselineRow))
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

    return dimOrderSingle.flatMap(
        dimOrder -> {
          log.info(
              "[SCREEN-RCA-SEGMENT] Dimension order: {} (hybridEnabled={})",
              dimOrder,
              hybridEnabled);
          return pickFirstDimension(projectId, screenName, window, dimOrder, threshold, totalBad)
              .flatMap(
                  optFirst -> {
                    if (optFirst.isEmpty()) {
                      return buildFlatSegments(
                              projectId, screenName, window, baseline, dimOrder, maxSegments)
                          .map(
                              segments -> new SegmentsWithMode(
                                  segments, RootCauseAnalysisMode.FLAT));
                    }
                    FirstDimensionPick first = optFirst.get();
                    return buildHierarchyThenFlat(
                            projectId,
                            screenName,
                            window,
                            baseline,
                            dimOrder,
                            maxSegments,
                            totalBad,
                            threshold,
                            first.dimOrderIndex(),
                            List.of(first.path()))
                        .map(
                            segments -> new SegmentsWithMode(
                                segments, RootCauseAnalysisMode.HIERARCHICAL));
                  });
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
                    getMaxAffectedUsersForDimension(projectId, screenName, window, dim)
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

  private Single<Long> getMaxAffectedUsersForDimension(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      String dimension) {
    RootCauseQuerySpec spec =
        ScreenRcaQueryBuilder.buildBadClickByDimensionQuery(
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
                                r.get("affected_user_count")))
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
              ScreenRcaQueryBuilder.buildBadClickByDimensionQuery(
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
        ScreenRcaQueryBuilder.buildBadClickByDimensionQuery(
            projectId, screenName, window.startInclusive, window.endExclusive, dim, null);
    return executeQuery(projectId, q).flatMap(rows -> {
      Optional<Map.Entry<String, Long>> top = rows.stream()
          .map(r -> Map.entry(String.valueOf(r.get(dim)), NumberCoercionUtils.toLong(r.get("affected_user_count"))))
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

  private Single<List<RootCauseSegment>> buildHierarchyThenFlat(
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
      return materializeSegments(projectId, screenName, window, baseline, path);
    }
    Map<String, String> currentFilters = path.stream()
        .collect(Collectors.toMap(s -> s.dimension, s -> s.value, (a, b) -> b));
    int nextDimIndex = hierarchyStartDimIndex + path.size();
    if (nextDimIndex >= dimOrder.size()) {
      return materializeSegments(projectId, screenName, window, baseline, path);
    }
    String nextDim = dimOrder.get(nextDimIndex);
    RootCauseQuerySpec q =
        ScreenRcaQueryBuilder.buildBadClickByDimensionQuery(
            projectId, screenName, window.startInclusive, window.endExclusive, nextDim, currentFilters);
    return executeQuery(projectId, q)
        .flatMap(rows -> {
          Optional<SegmentPath> picked = pickClosestToTotal(rows, nextDim, totalBad, threshold);
          if (picked.isEmpty()) {
            java.util.Set<String> dimsInPath = path.stream()
                .map(s -> s.dimension)
                .collect(Collectors.toSet());
            List<SegmentPath> flatExtras = new ArrayList<>(path);
            return collectFlatExtrasFromDimensionIndex(
                projectId, screenName, window, dimOrder, maxSegments, 0, flatExtras, dimsInPath)
                .flatMap(finalPath ->
                    materializeSegments(projectId, screenName, window, baseline, finalPath));
          }
          List<SegmentPath> newPath = new ArrayList<>(path);
          newPath.add(picked.get());
          return buildHierarchyThenFlat(
              projectId, screenName, window, baseline, dimOrder, maxSegments,
              totalBad, threshold, hierarchyStartDimIndex, newPath);
        });
  }

  private Single<List<RootCauseSegment>> materializeSegments(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<SegmentPath> path) {
    return materializeSegmentsFromIndex(
        projectId, screenName, window, baseline, path, 0, new LinkedHashMap<>(), new ArrayList<>());
  }

  private Single<List<SegmentPath>> collectFlatExtrasFromDimensionIndex(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window,
      List<String> dimOrder,
      int maxSegments,
      int index,
      List<SegmentPath> flatExtras,
      java.util.Set<String> dimsInHierarchy) {
    if (flatExtras.size() >= maxSegments || index >= dimOrder.size()) {
      return Single.just(flatExtras);
    }
    String d = dimOrder.get(index);
    if (dimsInHierarchy.contains(d)) {
      return collectFlatExtrasFromDimensionIndex(
          projectId, screenName, window, dimOrder, maxSegments, index + 1, flatExtras, dimsInHierarchy);
    }
    RootCauseQuerySpec q2 =
        ScreenRcaQueryBuilder.buildBadClickByDimensionQuery(
            projectId, screenName, window.startInclusive, window.endExclusive, d, null);
    return executeQuery(projectId, q2).flatMap(r2 -> {
      Optional<Map.Entry<String, Long>> top = r2.stream()
          .map(row -> Map.entry(String.valueOf(row.get(d)), NumberCoercionUtils.toLong(row.get("affected_user_count"))))
          .filter(e -> e.getValue() > 0)
          .max(Map.Entry.comparingByValue());
      List<SegmentPath> next = new ArrayList<>(flatExtras);
      if (top.isPresent()) {
        next.add(new SegmentPath(d, top.get().getKey(), true));
      }
      if (next.size() >= maxSegments) {
        return Single.just(next);
      }
      return collectFlatExtrasFromDimensionIndex(
          projectId, screenName, window, dimOrder, maxSegments, index + 1, next, dimsInHierarchy);
    });
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
          Map<String, Double> deltas = computeScreenDeltas(baseline, row);
          RootCauseSegment segment = RootCauseSegment.builder()
              .label(label)
              .dimensions(new LinkedHashMap<>(dimensionFilters))
              .metrics(new LinkedHashMap<>(row))
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
      long count = NumberCoercionUtils.toLong(row.get("affected_user_count"));
      if (count < threshold) {
        continue;
      }
      long diff = Math.abs(count - totalBad);
      if (diff < bestDiff) {
        bestDiff = diff;
        Object val = row.get(dimensionColumn);
        best = new SegmentPath(dimensionColumn, val != null ? val.toString() : "", false);
      }
    }
    return Optional.ofNullable(best);
  }

  private Map<String, Double> computeScreenDeltas(Map<String, Object> baseline, Map<String, Object> segment) {
    Map<String, Double> deltas = new LinkedHashMap<>();
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

  private static List<String> screenRcaMetricKeys() {
    return List.of(
        ScreenRcaQueryBuilder.CLICK_VOLUME,
        ScreenRcaQueryBuilder.RAGE_COUNT,
        ScreenRcaQueryBuilder.DEAD_COUNT);
  }

  private static Map<String, Object> toBaselineMap(Map<String, Object> row) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (String key : screenRcaMetricKeys()) {
      if (row.containsKey(key)) {
        m.put(key, row.get(key));
      }
    }
    return m;
  }

  private Single<List<Map<String, Object>>> executeQuery(String projectId, RootCauseQuerySpec spec) {
    return clickhouseQueryService
        .executeRootCauseQuery(projectId, spec.sql(), spec.bindNames(), spec.bindValues())
        .map(this::rowsToMaps);
  }

  /**
   * Fetch total distinct screen sessions in the window (denominator for session-based rates).
   */
  private Single<Long> fetchTotalScreenSessions(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    String p3 = acc.nextName();
    acc.add(p0, projectId);
    acc.add(p1, screenName);
    String startStr =
        window.startInclusive.atOffset(ZoneOffset.UTC)
            .format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr =
        window.endExclusive.atOffset(ZoneOffset.UTC)
            .format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p2, startStr);
    acc.add(p3, endStr);
    String sql =
        "SELECT uniq(SessionId) AS total_screen_sessions FROM "
            + ClickhouseConstants.OTEL_TRACES_TABLE
            + " WHERE ProjectId = :" + p0
            + " AND nullIf(trimBoth(ScreenName), '') = :" + p1
            + " AND Timestamp >= toDateTime64(:" + p2 + ", 9, 'UTC')"
            + " AND Timestamp < toDateTime64(:" + p3 + ", 9, 'UTC')";
    RootCauseQuerySpec spec = acc.toSpec(sql);
    return executeQuery(projectId, spec)
        .map(
            rows -> {
              if (rows.isEmpty()) {
                return 0L;
              }
              Object val = rows.get(0).get("total_screen_sessions");
              return NumberCoercionUtils.toLong(val);
            });
  }

  private Single<List<String>> fetchTopAffectedSessions(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window) {
    RootCauseQueryBuilder.BindAccumulator acc = new RootCauseQueryBuilder.BindAccumulator();
    String p0 = acc.nextName();
    String p1 = acc.nextName();
    String p2 = acc.nextName();
    String p3 = acc.nextName();
    acc.add(p0, projectId);
    acc.add(p1, screenName);
    String startStr = window.startInclusive.atOffset(ZoneOffset.UTC)
        .format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    String endStr = window.endExclusive.atOffset(ZoneOffset.UTC)
        .format(ClickhouseConstants.CLICKHOUSE_TIMESTAMP_LITERAL);
    acc.add(p2, startStr);
    acc.add(p3, endStr);
    String sql = "SELECT SessionId AS session_id FROM " + ClickhouseConstants.OTEL_TRACES_TABLE
        + " WHERE ProjectId = :" + p0
        + " AND PulseType = 'screen_session'"
        + " AND nullIf(trimBoth(ScreenName), '') = :" + p1
        + " AND Timestamp >= toDateTime64(:" + p2 + ", 9, 'UTC')"
        + " AND Timestamp < toDateTime64(:" + p3 + ", 9, 'UTC')"
        + " ORDER BY Timestamp DESC LIMIT 3";
    RootCauseQuerySpec spec = acc.toSpec(sql);
    return executeQuery(projectId, spec)
        .map(rows -> rows.stream()
            .map(r -> String.valueOf(r.get("session_id")))
            .filter(s -> s != null && !s.isBlank() && !"null".equals(s))
            .collect(Collectors.toList()))
        .onErrorReturnItem(List.of());
  }

  private Single<Boolean> checkHeatmapAvailable(
      String projectId,
      String screenName,
      RootCauseQueryBuilder.Window window) {
    String startDate = window.startInclusive.atOffset(ZoneOffset.UTC).toLocalDate().toString();
    String endDate = window.endExclusive.atOffset(ZoneOffset.UTC).toLocalDate().toString();
    String sql = String.format(
        "SELECT count(*) AS cnt FROM otel.interaction_heatmaps_daily"
            + " WHERE ProjectId = '%s' AND ScreenName = '%s'"
            + " AND Date >= toDate('%s') AND Date < toDate('%s')",
        projectId.replace("'", "\\'"),
        screenName.replace("'", "\\'"),
        startDate,
        endDate);
    RootCauseQuerySpec spec = new RootCauseQueryBuilder.BindAccumulator().toSpec(sql);
    return executeQuery(projectId, spec)
        .map(rows -> {
          if (rows.isEmpty()) {
            return false;
          }
          return NumberCoercionUtils.toLong(rows.get(0).get("cnt")) > 0;
        })
        .onErrorReturnItem(false);
  }

  // ===== Screen RCA v2: Problem computation with hierarchical/flat segmentation =====

  private record ProblemAlgoConfig(
      Function<Map<String, Object>, Double> metricExtractor,
      BiFunction<String, Map<String, String>, RootCauseQuerySpec> dimQueryBuilder) {}

  private record TopSegmentV2(
      String label, Map<String, String> dimensionFilters, long affectedUserCount) {}

  private record DimPickV2(
      int dimIndex, String dimension, String value, long affectedUserCount) {}

  private record DimTopCandidate(
      String dim, String value, double metricValue, long affectedUserCount) {}

  private record SegmentLabelPair(String dimensionKey, String dimensionValue) {}

  /**
   * Parses {@code "DimensionKey:value"} labels (e.g. {@code "AppVersion:5.1.0"}).
   * Returns null for overall, blank, hierarchical ({@code "a + b"}), or labels without a colon.
   */
  private static SegmentLabelPair parseSegmentLabel(String label) {
    if (label == null || label.isBlank() || "overall".equalsIgnoreCase(label.trim())) {
      return null;
    }
    if (label.contains(" + ")) {
      return null;
    }
    int colonIdx = label.indexOf(':');
    if (colonIdx < 0) {
      return null;
    }
    String key = label.substring(0, colonIdx).trim();
    String value = label.substring(colonIdx + 1).trim();
    if (key.isEmpty() || value.isEmpty()) {
      return null;
    }
    return new SegmentLabelPair(key, value);
  }

  private static Map<String, String> resolveSegmentFilters(Optional<TopSegmentV2> optSeg) {
    if (optSeg.isEmpty()) {
      return null;
    }
    TopSegmentV2 seg = optSeg.get();
    if (seg.label() == null || seg.label().isBlank() || "overall".equalsIgnoreCase(seg.label().trim())) {
      return null;
    }
    if (seg.dimensionFilters() != null && !seg.dimensionFilters().isEmpty()) {
      return seg.dimensionFilters();
    }
    SegmentLabelPair parsed = parseSegmentLabel(seg.label());
    if (parsed == null) {
      return null;
    }
    return Map.of(parsed.dimensionKey(), parsed.dimensionValue());
  }

  /** RxJava3 {@link Single} cannot emit null; use empty Optional for missing segment metrics. */
  private static Single<Optional<ScreenRcaMetrics>> emptySegmentMetricsSingle() {
    return Single.just(Optional.empty());
  }

  private Single<Optional<ScreenRcaMetrics>> fetchRateSegmentMetrics(
      String projectId,
      RootCauseQuerySpec segmentQuery,
      Long totalSessions) {
    return executeQuery(projectId, segmentQuery)
        .map(rows -> {
          if (rows.isEmpty()) {
            return Optional.<ScreenRcaMetrics>empty();
          }
          long aff = NumberCoercionUtils.toLong(rows.get(0).get("affected_user_count"));
          double rate = totalSessions != null && totalSessions > 0
              ? (double) aff / totalSessions * 100
              : 0;
          return Optional.of(ScreenRcaMetrics.builder()
              .affectedVolume(Long.valueOf(aff))
              .rate(String.format("%.2f%%", rate))
              .build());
        })
        .onErrorReturnItem(Optional.empty());
  }

  private Single<Optional<ScreenRcaMetrics>> fetchPercentileSegmentMetrics(
      String projectId,
      RootCauseQuerySpec affectedQuery,
      RootCauseQuerySpec percentilesQuery,
      Long totalSessions) {
    return Single.zip(
        executeQuery(projectId, affectedQuery),
        executeQuery(projectId, percentilesQuery),
        (affRows, pctRows) -> {
          if (affRows.isEmpty()) {
            return Optional.<ScreenRcaMetrics>empty();
          }
          long aff = NumberCoercionUtils.toLong(affRows.get(0).get("affected_user_count"));
          double rate = totalSessions != null && totalSessions > 0
              ? (double) aff / totalSessions * 100
              : 0;
          ScreenRcaMetrics.ScreenRcaMetricsBuilder builder = ScreenRcaMetrics.builder()
              .affectedVolume(Long.valueOf(aff))
              .rate(String.format("%.2f%%", rate));
          if (!pctRows.isEmpty()) {
            Map<String, Object> pctRow = pctRows.get(0);
            Long p50 = pctRow.get(ScreenRcaQueryBuilder.P50_MS) == null
                ? null
                : (long) NumberCoercionUtils.toDouble(pctRow.get(ScreenRcaQueryBuilder.P50_MS));
            Long p95 = pctRow.get(ScreenRcaQueryBuilder.P95_MS) == null
                ? null
                : (long) NumberCoercionUtils.toDouble(pctRow.get(ScreenRcaQueryBuilder.P95_MS));
            builder.p50Ms(p50).p95Ms(p95);
          }
          return Optional.of(builder.build());
        })
        .onErrorReturnItem(Optional.empty());
  }

  private Single<Optional<ScreenRcaMetrics>> fetchBadClickSegmentMetrics(
      String projectId,
      RootCauseQuerySpec segmentQuery) {
    return executeQuery(projectId, segmentQuery)
        .map(rows -> {
          if (rows.isEmpty()) {
            return Optional.<ScreenRcaMetrics>empty();
          }
          Map<String, Object> row = rows.get(0);
          long segClickVol = NumberCoercionUtils.toLong(row.get(ScreenRcaQueryBuilder.CLICK_VOLUME));
          long segRageCount = NumberCoercionUtils.toLong(row.get(ScreenRcaQueryBuilder.RAGE_COUNT));
          long segDeadCount = NumberCoercionUtils.toLong(row.get(ScreenRcaQueryBuilder.DEAD_COUNT));
          double segBadClickRate =
              segClickVol > 0 ? (double) (segRageCount + segDeadCount) / segClickVol * 100 : 0;
          return Optional.of(ScreenRcaMetrics.builder()
              .affectedVolume(Long.valueOf(NumberCoercionUtils.toLong(row.get("affected_user_count"))))
              .rate(String.format("%.2f%%", segBadClickRate))
              .clickVolume(Long.valueOf(segClickVol))
              .rageCount(Long.valueOf(segRageCount))
              .deadCount(Long.valueOf(segDeadCount))
              .build());
        })
        .onErrorReturnItem(Optional.empty());
  }

  private Single<ScreenRcaProblemResult> computeCrashProblem(
      String projectId, String screenName, RootCauseQueryBuilder.Window window, Long totalSessions) {
    if (totalSessions == 0) {
      return Single.just(SKIP);
    }
    return executeQuery(projectId, ScreenRcaQueryBuilder.buildCrashBaselineQuery(
        projectId, screenName, window.startInclusive, window.endExclusive))
        .flatMap(rows -> {
          if (rows.isEmpty()) {
            return Single.just(SKIP);
          }
          Map<String, Object> baselineRow = rows.get(0);
          long baselineAffectedUserCount =
              NumberCoercionUtils.toLong(baselineRow.get("affected_user_count"));
          if (baselineAffectedUserCount == 0) {
            return Single.just(SKIP);
          }
          double crashRate =
              totalSessions > 0 ? (double) baselineAffectedUserCount / totalSessions * 100 : 0;
          ProblemAlgoConfig algo = new ProblemAlgoConfig(
              r -> NumberCoercionUtils.toDouble(r.get("affected_user_count")),
              (dim, filters) -> ScreenRcaQueryBuilder.buildCrashByDimensionQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive, dim, filters));
          return findTopSegmentV2(
              projectId, screenName, window, (double) baselineAffectedUserCount, algo)
              .flatMap(optSeg -> {
                String topLabel = optSeg.map(TopSegmentV2::label).orElse("overall");
                long segmentAffectedUsers = optSeg.map(TopSegmentV2::affectedUserCount)
                    .orElse(baselineAffectedUserCount);
                Map<String, String> filters = resolveSegmentFilters(optSeg);
                Single<Optional<ScreenRcaMetrics>> segmentMetricsSingle = filters != null
                    ? fetchRateSegmentMetrics(
                        projectId,
                        ScreenRcaQueryBuilder.buildCrashSegmentMetricsQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        totalSessions)
                    : emptySegmentMetricsSingle();
                Single<List<ScreenRcaSpecificIssue>> issuesSingle = optSeg
                    .filter(s -> !s.dimensionFilters().isEmpty())
                    .map(s -> {
                      Map.Entry<String, String> e = s.dimensionFilters().entrySet().iterator().next();
                      return fetchCrashSpecificIssues(
                          projectId, screenName, window, e.getKey(), e.getValue());
                    })
                    .orElse(Single.just(List.of()));
                return Single.zip(segmentMetricsSingle, issuesSingle, (segMetricsOpt, issues) ->
                    ScreenRcaProblemResult.builder()
                        .problemType("crashes")
                        .topSegment(topLabel)
                        .metricId(ScreenRcaQueryBuilder.CRASH_RATE)
                        .metrics(ScreenRcaMetrics.builder()
                            .affectedVolume(Long.valueOf(baselineAffectedUserCount))
                            .rate(String.format("%.2f%%", crashRate))
                            .build())
                        .segmentMetrics(segMetricsOpt
                            .map(sm -> sm.toBuilder().affectedVolume(Long.valueOf(segmentAffectedUsers)).build())
                            .orElse(null))
                        .affectedUserCount(Long.valueOf(segmentAffectedUsers))
                        .typePriorityOrdinal(getTypePriorityOrdinal("crashes"))
                        .specificIssues(issues)
                        .build());
              });
        });
  }

  private Single<ScreenRcaProblemResult> computeAnrProblem(
      String projectId, String screenName, RootCauseQueryBuilder.Window window, Long totalSessions) {
    if (totalSessions == 0) {
      return Single.just(SKIP);
    }
    return executeQuery(projectId, ScreenRcaQueryBuilder.buildAnrBaselineQuery(
        projectId, screenName, window.startInclusive, window.endExclusive))
        .flatMap(rows -> {
          if (rows.isEmpty()) {
            return Single.just(SKIP);
          }
          Map<String, Object> baselineRow = rows.get(0);
          long baselineAffectedUserCount =
              NumberCoercionUtils.toLong(baselineRow.get("affected_user_count"));
          if (baselineAffectedUserCount == 0) {
            return Single.just(SKIP);
          }
          double anrRate =
              totalSessions > 0 ? (double) baselineAffectedUserCount / totalSessions * 100 : 0;
          ProblemAlgoConfig algo = new ProblemAlgoConfig(
              r -> NumberCoercionUtils.toDouble(r.get("affected_user_count")),
              (dim, filters) -> ScreenRcaQueryBuilder.buildAnrByDimensionQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive, dim, filters));
          return findTopSegmentV2(
              projectId, screenName, window, (double) baselineAffectedUserCount, algo)
              .flatMap(optSeg -> {
                String topLabel = optSeg.map(TopSegmentV2::label).orElse("overall");
                long segmentAffectedUsers = optSeg.map(TopSegmentV2::affectedUserCount)
                    .orElse(baselineAffectedUserCount);
                Map<String, String> filters = resolveSegmentFilters(optSeg);
                Single<Optional<ScreenRcaMetrics>> segmentMetricsSingle = filters != null
                    ? fetchRateSegmentMetrics(
                        projectId,
                        ScreenRcaQueryBuilder.buildAnrSegmentMetricsQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        totalSessions)
                    : emptySegmentMetricsSingle();
                Single<List<ScreenRcaSpecificIssue>> issuesSingle = optSeg
                    .filter(s -> !s.dimensionFilters().isEmpty())
                    .map(s -> {
                      Map.Entry<String, String> e = s.dimensionFilters().entrySet().iterator().next();
                      return fetchAnrSpecificIssues(
                          projectId, screenName, window, e.getKey(), e.getValue());
                    })
                    .orElse(Single.just(List.of()));
                return Single.zip(segmentMetricsSingle, issuesSingle, (segMetricsOpt, issues) ->
                    ScreenRcaProblemResult.builder()
                        .problemType("anr")
                        .topSegment(topLabel)
                        .metricId(ScreenRcaQueryBuilder.ANR_RATE)
                        .metrics(ScreenRcaMetrics.builder()
                            .affectedVolume(Long.valueOf(baselineAffectedUserCount))
                            .rate(String.format("%.2f%%", anrRate))
                            .build())
                        .segmentMetrics(segMetricsOpt
                            .map(sm -> sm.toBuilder().affectedVolume(Long.valueOf(segmentAffectedUsers)).build())
                            .orElse(null))
                        .affectedUserCount(Long.valueOf(segmentAffectedUsers))
                        .typePriorityOrdinal(getTypePriorityOrdinal("anr"))
                        .specificIssues(issues)
                        .build());
              });
        });
  }

  private Single<ScreenRcaProblemResult> computeFrozenFrameProblem(
      String projectId, String screenName, RootCauseQueryBuilder.Window window, Long totalSessions) {
    return executeQuery(projectId, ScreenRcaQueryBuilder.buildFrozenFrameBaselineQuery(
        projectId, screenName, window.startInclusive, window.endExclusive))
        .flatMap(rows -> {
          if (rows.isEmpty()) {
            return Single.just(SKIP);
          }
          long baselineAffectedUserCount =
              NumberCoercionUtils.toLong(rows.get(0).get("affected_user_count"));
          if (baselineAffectedUserCount == 0) {
            return Single.just(SKIP);
          }
          double frozenRate =
              totalSessions > 0 ? (double) baselineAffectedUserCount / totalSessions * 100 : 0;
          ProblemAlgoConfig algo = new ProblemAlgoConfig(
              r -> NumberCoercionUtils.toDouble(r.get("affected_user_count")),
              (dim, filters) -> ScreenRcaQueryBuilder.buildFrozenFrameByDimensionQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive, dim, filters));
          return findTopSegmentV2(
              projectId, screenName, window, (double) baselineAffectedUserCount, algo)
              .flatMap(optSeg -> {
                long segmentAffectedUsers = optSeg.map(TopSegmentV2::affectedUserCount)
                    .orElse(baselineAffectedUserCount);
                Map<String, String> filters = resolveSegmentFilters(optSeg);
                Single<Optional<ScreenRcaMetrics>> segmentMetricsSingle = filters != null
                    ? fetchRateSegmentMetrics(
                        projectId,
                        ScreenRcaQueryBuilder.buildFrozenFrameSegmentMetricsQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        totalSessions)
                    : emptySegmentMetricsSingle();
                return segmentMetricsSingle.map(segMetricsOpt -> ScreenRcaProblemResult.builder()
                    .problemType("frozen_frames")
                    .topSegment(optSeg.map(TopSegmentV2::label).orElse("overall"))
                    .metricId(ScreenRcaQueryBuilder.FROZEN_FRAME_RATE)
                    .metrics(ScreenRcaMetrics.builder()
                        .affectedVolume(Long.valueOf(baselineAffectedUserCount))
                        .rate(String.format("%.2f%%", frozenRate))
                        .build())
                    .segmentMetrics(segMetricsOpt
                        .map(sm -> sm.toBuilder().affectedVolume(Long.valueOf(segmentAffectedUsers)).build())
                        .orElse(null))
                    .affectedUserCount(Long.valueOf(segmentAffectedUsers))
                    .typePriorityOrdinal(getTypePriorityOrdinal("frozen_frames"))
                    .build());
              });
        });
  }

  private Single<ScreenRcaProblemResult> computeSlowRenderProblem(
      String projectId, String screenName, RootCauseQueryBuilder.Window window, Long totalSessions) {
    return executeQuery(projectId, ScreenRcaQueryBuilder.buildSlowRenderingBaselineQuery(
        projectId, screenName, window.startInclusive, window.endExclusive))
        .flatMap(rows -> {
          if (rows.isEmpty()) {
            return Single.just(SKIP);
          }
          long baselineAffectedUserCount =
              NumberCoercionUtils.toLong(rows.get(0).get("affected_user_count"));
          if (baselineAffectedUserCount == 0) {
            return Single.just(SKIP);
          }
          double slowRate =
              totalSessions > 0 ? (double) baselineAffectedUserCount / totalSessions * 100 : 0;
          ProblemAlgoConfig algo = new ProblemAlgoConfig(
              r -> NumberCoercionUtils.toDouble(r.get("affected_user_count")),
              (dim, filters) -> ScreenRcaQueryBuilder.buildSlowRenderingByDimensionQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive, dim, filters));
          return findTopSegmentV2(
              projectId, screenName, window, (double) baselineAffectedUserCount, algo)
              .flatMap(optSeg -> {
                long segmentAffectedUsers = optSeg.map(TopSegmentV2::affectedUserCount)
                    .orElse(baselineAffectedUserCount);
                Map<String, String> filters = resolveSegmentFilters(optSeg);
                Single<Optional<ScreenRcaMetrics>> segmentMetricsSingle = filters != null
                    ? fetchRateSegmentMetrics(
                        projectId,
                        ScreenRcaQueryBuilder.buildSlowRenderingSegmentMetricsQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        totalSessions)
                    : emptySegmentMetricsSingle();
                return segmentMetricsSingle.map(segMetricsOpt -> ScreenRcaProblemResult.builder()
                    .problemType("slow_rendering")
                    .topSegment(optSeg.map(TopSegmentV2::label).orElse("overall"))
                    .metricId(ScreenRcaQueryBuilder.SLOW_FRAME_RATE)
                    .metrics(ScreenRcaMetrics.builder()
                        .affectedVolume(Long.valueOf(baselineAffectedUserCount))
                        .rate(String.format("%.2f%%", slowRate))
                        .build())
                    .segmentMetrics(segMetricsOpt
                        .map(sm -> sm.toBuilder().affectedVolume(Long.valueOf(segmentAffectedUsers)).build())
                        .orElse(null))
                    .affectedUserCount(Long.valueOf(segmentAffectedUsers))
                    .typePriorityOrdinal(getTypePriorityOrdinal("slow_rendering"))
                    .build());
              });
        });
  }

  private Single<ScreenRcaProblemResult> computeNetworkFailureProblem(
      String projectId, String screenName, RootCauseQueryBuilder.Window window, Long totalSessions) {
    return executeQuery(projectId, ScreenRcaQueryBuilder.buildNetworkFailureBaselineQuery(
        projectId, screenName, window.startInclusive, window.endExclusive))
        .flatMap(rows -> {
          if (rows.isEmpty()) {
            return Single.just(SKIP);
          }
          long baselineAffectedUserCount =
              NumberCoercionUtils.toLong(rows.get(0).get("affected_user_count"));
          if (baselineAffectedUserCount == 0) {
            return Single.just(SKIP);
          }
          double errorRate =
              totalSessions > 0 ? (double) baselineAffectedUserCount / totalSessions * 100 : 0;
          ProblemAlgoConfig algo = new ProblemAlgoConfig(
              r -> NumberCoercionUtils.toDouble(r.get("affected_user_count")),
              (dim, filters) -> ScreenRcaQueryBuilder.buildNetworkFailureByDimensionQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive, dim, filters));
          return findTopSegmentV2(
              projectId, screenName, window, (double) baselineAffectedUserCount, algo)
              .flatMap(optSeg -> {
                long segmentAffectedUsers = optSeg.map(TopSegmentV2::affectedUserCount)
                    .orElse(baselineAffectedUserCount);
                Map<String, String> filters = resolveSegmentFilters(optSeg);
                Single<Optional<ScreenRcaMetrics>> segmentMetricsSingle = filters != null
                    ? fetchRateSegmentMetrics(
                        projectId,
                        ScreenRcaQueryBuilder.buildNetworkFailureSegmentMetricsQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        totalSessions)
                    : emptySegmentMetricsSingle();
                return segmentMetricsSingle.map(segMetricsOpt -> ScreenRcaProblemResult.builder()
                    .problemType("network_failures")
                    .topSegment(optSeg.map(TopSegmentV2::label).orElse("overall"))
                    .metricId(ScreenRcaQueryBuilder.NETWORK_ERROR_RATE)
                    .metrics(ScreenRcaMetrics.builder()
                        .affectedVolume(Long.valueOf(baselineAffectedUserCount))
                        .rate(String.format("%.2f%%", errorRate))
                        .build())
                    .segmentMetrics(segMetricsOpt
                        .map(sm -> sm.toBuilder().affectedVolume(Long.valueOf(segmentAffectedUsers)).build())
                        .orElse(null))
                    .affectedUserCount(Long.valueOf(segmentAffectedUsers))
                    .typePriorityOrdinal(getTypePriorityOrdinal("network_failures"))
                    .build());
              });
        });
  }

  private Single<ScreenRcaProblemResult> computeNetworkLatencyProblem(
      String projectId, String screenName, RootCauseQueryBuilder.Window window, Long totalSessions) {
    return executeQuery(projectId, ScreenRcaQueryBuilder.buildNetworkLatencyBaselineQuery(
        projectId, screenName, window.startInclusive, window.endExclusive))
        .flatMap(rows -> {
          if (rows.isEmpty()) {
            return Single.just(SKIP);
          }
          long baselineAffectedUserCount =
              NumberCoercionUtils.toLong(rows.get(0).get("affected_user_count"));
          if (baselineAffectedUserCount == 0) {
            return Single.just(SKIP);
          }
          double badLatencyRate =
              totalSessions > 0 ? (double) baselineAffectedUserCount / totalSessions * 100 : 0;
          ProblemAlgoConfig algo = new ProblemAlgoConfig(
              r -> NumberCoercionUtils.toDouble(r.get("affected_user_count")),
              (dim, filters) -> ScreenRcaQueryBuilder.buildNetworkLatencyByDimensionQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive, dim, filters));
          Single<Map<String, Object>> percentilesSingle = executeQuery(projectId,
              ScreenRcaQueryBuilder.buildNetworkLatencyPercentilesQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive))
              .map(pctRows -> pctRows.isEmpty() ? Map.of() : pctRows.get(0));
          return findTopSegmentV2(
              projectId, screenName, window, (double) baselineAffectedUserCount, algo)
              .flatMap(optSeg -> {
                Map<String, String> filters = resolveSegmentFilters(optSeg);
                Single<Optional<ScreenRcaMetrics>> segmentMetricsSingle = filters != null
                    ? fetchPercentileSegmentMetrics(
                        projectId,
                        ScreenRcaQueryBuilder.buildNetworkLatencySegmentMetricsQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        ScreenRcaQueryBuilder.buildNetworkLatencySegmentPercentilesQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        totalSessions)
                    : emptySegmentMetricsSingle();
                return Single.zip(segmentMetricsSingle, percentilesSingle, (segMetricsOpt, pctRow) -> {
                  long segmentAffectedUsers = optSeg.map(TopSegmentV2::affectedUserCount)
                      .orElse(baselineAffectedUserCount);
                  Long p50 = pctRow.isEmpty() ? null
                      : (long) NumberCoercionUtils.toDouble(pctRow.get(ScreenRcaQueryBuilder.P50_MS));
                  Long p95 = pctRow.isEmpty() ? null
                      : (long) NumberCoercionUtils.toDouble(pctRow.get(ScreenRcaQueryBuilder.P95_MS));
                  return ScreenRcaProblemResult.builder()
                      .problemType("network_latency")
                      .topSegment(optSeg.map(TopSegmentV2::label).orElse("overall"))
                      .metricId(ScreenRcaQueryBuilder.BAD_NETWORK_LATENCY_RATE)
                      .metrics(ScreenRcaMetrics.builder()
                          .affectedVolume(Long.valueOf(baselineAffectedUserCount))
                          .rate(String.format("%.2f%%", badLatencyRate))
                          .p50Ms(p50)
                          .p95Ms(p95)
                          .build())
                      .segmentMetrics(segMetricsOpt
                          .map(sm -> sm.toBuilder().affectedVolume(Long.valueOf(segmentAffectedUsers)).build())
                          .orElse(null))
                      .affectedUserCount(Long.valueOf(segmentAffectedUsers))
                      .typePriorityOrdinal(getTypePriorityOrdinal("network_latency"))
                      .build();
                });
              });
        });
  }

  private Single<ScreenRcaProblemResult> computeScreenLoadProblem(
      String projectId, String screenName, RootCauseQueryBuilder.Window window, Long totalSessions) {
    return executeQuery(projectId, ScreenRcaQueryBuilder.buildScreenLoadBaselineQuery(
        projectId, screenName, window.startInclusive, window.endExclusive))
        .flatMap(rows -> {
          if (rows.isEmpty()) {
            return Single.just(SKIP);
          }
          long baselineAffectedUserCount =
              NumberCoercionUtils.toLong(rows.get(0).get("affected_user_count"));
          if (baselineAffectedUserCount == 0) {
            return Single.just(SKIP);
          }
          double badLoadRate =
              totalSessions > 0 ? (double) baselineAffectedUserCount / totalSessions * 100 : 0;
          ProblemAlgoConfig algo = new ProblemAlgoConfig(
              r -> NumberCoercionUtils.toDouble(r.get("affected_user_count")),
              (dim, filters) -> ScreenRcaQueryBuilder.buildScreenLoadByDimensionQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive, dim, filters));
          Single<Map<String, Object>> loadPctSingle = executeQuery(projectId,
              ScreenRcaQueryBuilder.buildScreenLoadPercentilesQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive))
              .map(pctRows -> pctRows.isEmpty() ? Map.of() : pctRows.get(0));
          return findTopSegmentV2(
              projectId, screenName, window, (double) baselineAffectedUserCount, algo)
              .flatMap(optSeg -> {
                Map<String, String> filters = resolveSegmentFilters(optSeg);
                Single<Optional<ScreenRcaMetrics>> segmentMetricsSingle = filters != null
                    ? fetchPercentileSegmentMetrics(
                        projectId,
                        ScreenRcaQueryBuilder.buildScreenLoadSegmentMetricsQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        ScreenRcaQueryBuilder.buildScreenLoadSegmentPercentilesQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        totalSessions)
                    : emptySegmentMetricsSingle();
                return Single.zip(segmentMetricsSingle, loadPctSingle, (segMetricsOpt, pctRow) -> {
                  long segmentAffectedUsers = optSeg.map(TopSegmentV2::affectedUserCount)
                      .orElse(baselineAffectedUserCount);
                  Long p50 = pctRow.isEmpty() ? null
                      : (long) NumberCoercionUtils.toDouble(pctRow.get(ScreenRcaQueryBuilder.P50_MS));
                  Long p95 = pctRow.isEmpty() ? null
                      : (long) NumberCoercionUtils.toDouble(pctRow.get(ScreenRcaQueryBuilder.P95_MS));
                  return ScreenRcaProblemResult.builder()
                      .problemType("screen_load_time")
                      .topSegment(optSeg.map(TopSegmentV2::label).orElse("overall"))
                      .metricId(ScreenRcaQueryBuilder.BAD_SCREEN_LOAD_RATE)
                      .metrics(ScreenRcaMetrics.builder()
                          .affectedVolume(Long.valueOf(baselineAffectedUserCount))
                          .rate(String.format("%.2f%%", badLoadRate))
                          .p50Ms(p50)
                          .p95Ms(p95)
                          .build())
                      .segmentMetrics(segMetricsOpt
                          .map(sm -> sm.toBuilder().affectedVolume(Long.valueOf(segmentAffectedUsers)).build())
                          .orElse(null))
                      .affectedUserCount(Long.valueOf(segmentAffectedUsers))
                      .typePriorityOrdinal(getTypePriorityOrdinal("screen_load_time"))
                      .build();
                });
              });
        });
  }

  private Single<ScreenRcaProblemResult> computeScreenInteractiveProblem(
      String projectId, String screenName, RootCauseQueryBuilder.Window window, Long totalSessions) {
    return executeQuery(projectId, ScreenRcaQueryBuilder.buildScreenInteractiveBaselineQuery(
        projectId, screenName, window.startInclusive, window.endExclusive))
        .flatMap(rows -> {
          if (rows.isEmpty()) {
            return Single.just(SKIP);
          }
          long baselineAffectedUserCount =
              NumberCoercionUtils.toLong(rows.get(0).get("affected_user_count"));
          if (baselineAffectedUserCount == 0) {
            return Single.just(SKIP);
          }
          double badInteractiveRate =
              totalSessions > 0 ? (double) baselineAffectedUserCount / totalSessions * 100 : 0;
          ProblemAlgoConfig algo = new ProblemAlgoConfig(
              r -> NumberCoercionUtils.toDouble(r.get("affected_user_count")),
              (dim, filters) -> ScreenRcaQueryBuilder.buildScreenInteractiveByDimensionQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive, dim, filters));
          Single<Map<String, Object>> interactivePctSingle = executeQuery(projectId,
              ScreenRcaQueryBuilder.buildScreenInteractivePercentilesQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive))
              .map(pctRows -> pctRows.isEmpty() ? Map.of() : pctRows.get(0));
          return findTopSegmentV2(
              projectId, screenName, window, (double) baselineAffectedUserCount, algo)
              .flatMap(optSeg -> {
                Map<String, String> filters = resolveSegmentFilters(optSeg);
                Single<Optional<ScreenRcaMetrics>> segmentMetricsSingle = filters != null
                    ? fetchPercentileSegmentMetrics(
                        projectId,
                        ScreenRcaQueryBuilder.buildScreenInteractiveSegmentMetricsQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        ScreenRcaQueryBuilder.buildScreenInteractiveSegmentPercentilesQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters),
                        totalSessions)
                    : emptySegmentMetricsSingle();
                return Single.zip(segmentMetricsSingle, interactivePctSingle, (segMetricsOpt, pctRow) -> {
                  long segmentAffectedUsers = optSeg.map(TopSegmentV2::affectedUserCount)
                      .orElse(baselineAffectedUserCount);
                  Long p50 = pctRow.isEmpty() ? null
                      : (long) NumberCoercionUtils.toDouble(pctRow.get(ScreenRcaQueryBuilder.P50_MS));
                  Long p95 = pctRow.isEmpty() ? null
                      : (long) NumberCoercionUtils.toDouble(pctRow.get(ScreenRcaQueryBuilder.P95_MS));
                  return ScreenRcaProblemResult.builder()
                      .problemType("screen_interactive")
                      .topSegment(optSeg.map(TopSegmentV2::label).orElse("overall"))
                      .metricId(ScreenRcaQueryBuilder.BAD_SCREEN_INTERACTIVE_RATE)
                      .metrics(ScreenRcaMetrics.builder()
                          .affectedVolume(Long.valueOf(baselineAffectedUserCount))
                          .rate(String.format("%.2f%%", badInteractiveRate))
                          .p50Ms(p50)
                          .p95Ms(p95)
                          .build())
                      .segmentMetrics(segMetricsOpt
                          .map(sm -> sm.toBuilder().affectedVolume(Long.valueOf(segmentAffectedUsers)).build())
                          .orElse(null))
                      .affectedUserCount(Long.valueOf(segmentAffectedUsers))
                      .typePriorityOrdinal(getTypePriorityOrdinal("screen_interactive"))
                      .build();
                });
              });
        });
  }

  private Single<ScreenRcaProblemResult> computeBadClickProblem(
      String projectId, String screenName, RootCauseQueryBuilder.Window window) {
    return executeQuery(projectId, ScreenRcaQueryBuilder.buildBaselineQuery(
        projectId, screenName, window.startInclusive, window.endExclusive))
        .flatMap(rows -> {
          if (rows.isEmpty()) {
            return Single.just(SKIP);
          }
          Map<String, Object> baselineRow = rows.get(0);
          long clickVolume = NumberCoercionUtils.toLong(baselineRow.get(ScreenRcaQueryBuilder.CLICK_VOLUME));
          long rageCount = NumberCoercionUtils.toLong(baselineRow.get(ScreenRcaQueryBuilder.RAGE_COUNT));
          long deadCount = NumberCoercionUtils.toLong(baselineRow.get(ScreenRcaQueryBuilder.DEAD_COUNT));
          if (clickVolume <= 0 || (rageCount + deadCount) == 0) {
            return Single.just(SKIP);
          }
          long baselineAffectedUserCount =
              NumberCoercionUtils.toLong(baselineRow.get("affected_user_count"));
          double badClickRate = clickVolume > 0 ? (double) (rageCount + deadCount) / clickVolume * 100 : 0;
          ProblemAlgoConfig algo = new ProblemAlgoConfig(
              r -> NumberCoercionUtils.toDouble(r.get("affected_user_count")),
              (dim, filters) -> ScreenRcaQueryBuilder.buildBadClickByDimensionQuery(
                  projectId, screenName, window.startInclusive, window.endExclusive, dim, filters));
          final long fClickVolume = clickVolume;
          final long fRageCount = rageCount;
          final long fDeadCount = deadCount;
          return findTopSegmentV2(
              projectId, screenName, window, (double) baselineAffectedUserCount, algo)
              .flatMap(optSeg -> {
                long segmentAffectedUsers = optSeg.map(TopSegmentV2::affectedUserCount)
                    .orElse(baselineAffectedUserCount);
                Map<String, String> filters = resolveSegmentFilters(optSeg);
                Single<Optional<ScreenRcaMetrics>> segmentMetricsSingle = filters != null
                    ? fetchBadClickSegmentMetrics(
                        projectId,
                        ScreenRcaQueryBuilder.buildBadClickSegmentMetricsQuery(
                            projectId, screenName, window.startInclusive, window.endExclusive, filters))
                    : emptySegmentMetricsSingle();
                return segmentMetricsSingle.map(segMetricsOpt -> ScreenRcaProblemResult.builder()
                    .problemType("bad_clicks")
                    .topSegment(optSeg.map(TopSegmentV2::label).orElse("overall"))
                    .metricId(ScreenRcaQueryBuilder.BAD_CLICKS_RATE)
                    .metrics(ScreenRcaMetrics.builder()
                        .affectedVolume(Long.valueOf(baselineAffectedUserCount))
                        .rate(String.format("%.2f%%", badClickRate))
                        .clickVolume(Long.valueOf(fClickVolume))
                        .rageCount(Long.valueOf(fRageCount))
                        .deadCount(Long.valueOf(fDeadCount))
                        .build())
                    .segmentMetrics(segMetricsOpt
                        .map(sm -> sm.toBuilder().affectedVolume(Long.valueOf(segmentAffectedUsers)).build())
                        .orElse(null))
                    .affectedUserCount(Long.valueOf(segmentAffectedUsers))
                    .typePriorityOrdinal(getTypePriorityOrdinal("bad_clicks"))
                    .build());
              });
        })
        .onErrorReturnItem(SKIP);
  }

  // ===== Screen RCA v2: Orchestration =====

  private Single<List<ScreenRcaProblemResult>> computeAllProblems(
      String projectId, String screenName, RootCauseQueryBuilder.Window window) {
    return fetchTotalScreenSessions(projectId, screenName, window)
        .flatMap(totalSessions -> Single.zip(
            computeCrashProblem(projectId, screenName, window, totalSessions),
            computeAnrProblem(projectId, screenName, window, totalSessions),
            computeFrozenFrameProblem(projectId, screenName, window, totalSessions),
            computeSlowRenderProblem(projectId, screenName, window, totalSessions),
            computeNetworkFailureProblem(projectId, screenName, window, totalSessions),
            computeNetworkLatencyProblem(projectId, screenName, window, totalSessions),
            computeScreenLoadProblem(projectId, screenName, window, totalSessions),
            computeScreenInteractiveProblem(projectId, screenName, window, totalSessions),
            computeBadClickProblem(projectId, screenName, window),
            (c, a, ff, sr, nf, nl, sl, si, bc) ->
                List.of(c, a, ff, sr, nf, nl, sl, si, bc)
                    .stream().filter(p -> p != null).toList()
        ));
  }

  private List<ScreenRcaProblemResult> rankProblems(List<ScreenRcaProblemResult> problems) {
    List<ScreenRcaProblemResult> sorted = problems.stream()
        .filter(p -> p.getProblemType() != null)
        .collect(Collectors.toCollection(ArrayList::new));
    if (sorted.isEmpty()) {
      return sorted;
    }
    sorted.sort(java.util.Comparator
        .comparingInt((ScreenRcaProblemResult p) -> -p.getAffectedUserCount().intValue())
        .thenComparingInt(ScreenRcaProblemResult::getTypePriorityOrdinal)
    );
    double equalWeight = 1.0 / sorted.size();
    for (int i = 0; i < sorted.size(); i++) {
      sorted.get(i).setRank(i + 1);
      sorted.get(i).setWeightage(equalWeight);
    }
    return sorted;
  }

  private int getTypePriorityOrdinal(String problemType) {
    return switch (problemType) {
        case "crashes" -> 0;
        case "anr" -> 1;
        case "frozen_frames" -> 2;
        case "screen_load_time" -> 3;
        case "screen_interactive" -> 4;
        case "network_failures" -> 5;
        case "network_latency" -> 6;
        case "slow_rendering" -> 7;
        case "bad_clicks" -> 8;
        default -> 9;
    };
  }

  // ===== Screen RCA v2: Generic hierarchical/flat algorithm =====

  /**
   * Runs the full hierarchical/flat segmentation algorithm against one problem metric and returns
   * the index-0 segment as top_segment. Algorithm is identical to interaction RCA; the only
   * difference is that we stop after the first segment rather than collecting up to maxSegments.
   *
   * @param baselineMetricValue overall driver metric value (gate = baseline × threshold%)
   */
  private Single<Optional<TopSegmentV2>> findTopSegmentV2(
      String projectId, String screenName, RootCauseQueryBuilder.Window window,
      double baselineMetricValue, ProblemAlgoConfig algo) {
    if (baselineMetricValue <= 0) {
      return Single.just(Optional.empty());
    }
    double threshold = baselineMetricValue * (config.getSimilarityThresholdPct() / 100.0);
    List<String> baseOrder = config.getDimensionOrder();
    if (baseOrder.isEmpty()) {
      return Single.just(Optional.empty());
    }
    Single<List<String>> dimOrderSingle = config.isHybridDimensionOrderingEnabled()
        ? computeHybridDimOrderV2(projectId, screenName, window, baseOrder, threshold, algo)
        : Single.just(baseOrder);
    return dimOrderSingle.flatMap(orderedDims ->
        pickFirstDimensionV2(projectId, screenName, window, orderedDims, threshold, baselineMetricValue, algo)
            .flatMap(optFirst -> {
              if (optFirst.isEmpty()) {
                return flatTopSegmentV2(projectId, screenName, window, orderedDims, algo);
              }
              DimPickV2 first = optFirst.get();
              LinkedHashMap<String, String> filters = new LinkedHashMap<>();
              filters.put(first.dimension(), first.value());
              return hierarchicalDrillV2(
                  projectId, screenName, window, orderedDims,
                  first.dimIndex() + 1, threshold, baselineMetricValue, filters,
                  first.affectedUserCount(), algo)
                  .map(Optional::of);
            }));
  }

  private Single<List<String>> computeHybridDimOrderV2(
      String projectId, String screenName, RootCauseQueryBuilder.Window window,
      List<String> baseOrder, double threshold, ProblemAlgoConfig algo) {
    if (baseOrder.isEmpty()) {
      return Single.just(List.of());
    }
    List<Single<Map.Entry<String, Double>>> maxQueries = baseOrder.stream()
        .map(dim -> {
          RootCauseQuerySpec q = algo.dimQueryBuilder().apply(dim, null);
          return executeQuery(projectId, q).map(rows -> {
            double maxVal = rows.stream()
                .mapToDouble(r -> algo.metricExtractor().apply(r))
                .max().orElse(0.0);
            return (Map.Entry<String, Double>) Map.entry(dim, maxVal);
          });
        })
        .toList();
    return Single.zip(maxQueries, results -> {
      Map<String, Long> dimMaxMap = new HashMap<>();
      for (Object r : results) {
        @SuppressWarnings("unchecked")
        Map.Entry<String, Double> e = (Map.Entry<String, Double>) r;
        dimMaxMap.put(e.getKey(), (long) (e.getValue() * 1000));
      }
      return RootCauseService.hybridDimensionOrderFromPrecomputedMaxes(
          baseOrder, dimMaxMap, threshold * 1000);
    });
  }

  private Single<Optional<DimPickV2>> pickFirstDimensionV2(
      String projectId, String screenName, RootCauseQueryBuilder.Window window,
      List<String> dimOrder, double threshold, double totalMetric, ProblemAlgoConfig algo) {
    return Observable.range(0, dimOrder.size())
        .concatMapMaybe(i -> {
          String dim = dimOrder.get(i);
          RootCauseQuerySpec q = algo.dimQueryBuilder().apply(dim, null);
          return executeQuery(projectId, q).flatMapMaybe(rows -> {
            Optional<DimPickV2> pick =
                pickClosestToTotalV2(rows, dim, i, totalMetric, threshold, algo.metricExtractor());
            return pick.map(Maybe::just).orElseGet(Maybe::empty);
          });
        })
        .firstElement()
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty());
  }

  private Single<TopSegmentV2> hierarchicalDrillV2(
      String projectId, String screenName, RootCauseQueryBuilder.Window window,
      List<String> dimOrder, int nextDimIndex, double threshold, double totalMetric,
      LinkedHashMap<String, String> accumulated, long affectedUserCount, ProblemAlgoConfig algo) {
    if (nextDimIndex >= dimOrder.size()) {
      return Single.just(buildTopSegmentLabel(accumulated, affectedUserCount));
    }
    String nextDim = dimOrder.get(nextDimIndex);
    RootCauseQuerySpec q = algo.dimQueryBuilder().apply(nextDim, Map.copyOf(accumulated));
    return executeQuery(projectId, q).flatMap(rows -> {
      Optional<DimPickV2> picked =
          pickClosestToTotalV2(rows, nextDim, nextDimIndex, totalMetric, threshold, algo.metricExtractor());
      if (picked.isEmpty()) {
        return Single.just(buildTopSegmentLabel(accumulated, affectedUserCount));
      }
      LinkedHashMap<String, String> next = new LinkedHashMap<>(accumulated);
      next.put(picked.get().dimension(), picked.get().value());
      return hierarchicalDrillV2(
          projectId, screenName, window, dimOrder, nextDimIndex + 1,
          threshold, totalMetric, next, picked.get().affectedUserCount(), algo);
    });
  }

  private Single<Optional<TopSegmentV2>> flatTopSegmentV2(
      String projectId, String screenName, RootCauseQueryBuilder.Window window,
      List<String> dimOrder, ProblemAlgoConfig algo) {
    return Observable.fromIterable(dimOrder)
        .concatMapMaybe(dim -> {
          RootCauseQuerySpec q = algo.dimQueryBuilder().apply(dim, null);
          return executeQuery(projectId, q).flatMapMaybe(rows -> {
            Optional<DimTopCandidate> best = rows.stream()
                .map(r -> new DimTopCandidate(
                    dim,
                    String.valueOf(r.get(dim)),
                    algo.metricExtractor().apply(r),
                    NumberCoercionUtils.toLong(r.get("affected_user_count"))))
                .filter(c -> c.metricValue() > 0)
                .max(Comparator.comparingDouble(DimTopCandidate::metricValue));
            return best.map(Maybe::just).orElseGet(Maybe::empty);
          });
        })
        .toList()
        .map(candidates -> {
          if (candidates.isEmpty()) {
            return Optional.<TopSegmentV2>empty();
          }
          return candidates.stream()
              .max(Comparator.comparingDouble(DimTopCandidate::metricValue))
              .map(best -> new TopSegmentV2(
                  best.dim() + ": " + best.value(),
                  Map.of(best.dim(), best.value()),
                  best.affectedUserCount()));
        });
  }

  private Optional<DimPickV2> pickClosestToTotalV2(
      List<Map<String, Object>> rows, String dimensionColumn, int dimIndex,
      double totalMetric, double threshold,
      Function<Map<String, Object>, Double> metricExtractor) {
    DimPickV2 best = null;
    double bestDiff = Double.MAX_VALUE;
    for (Map<String, Object> row : rows) {
      double metric = metricExtractor.apply(row);
      if (metric < threshold) {
        continue;
      }
      double diff = Math.abs(metric - totalMetric);
      if (diff < bestDiff) {
        bestDiff = diff;
        Object val = row.get(dimensionColumn);
        long aff = NumberCoercionUtils.toLong(row.get("affected_user_count"));
        best = new DimPickV2(dimIndex, dimensionColumn, val != null ? val.toString() : "", aff);
      }
    }
    return Optional.ofNullable(best);
  }

  private static TopSegmentV2 buildTopSegmentLabel(
      LinkedHashMap<String, String> filters, long affectedUserCount) {
    List<String> nonBlank = filters.values().stream()
        .filter(v -> v != null && !v.isBlank())
        .collect(Collectors.toList());
    String label = nonBlank.size() == 1
        ? filters.entrySet().stream()
            .filter(e -> e.getValue() != null && !e.getValue().isBlank())
            .findFirst()
            .map(e -> e.getKey() + ": " + e.getValue())
            .orElse("overall")
        : String.join(" + ", nonBlank);
    return new TopSegmentV2(label, Map.copyOf(filters), affectedUserCount);
  }

  // ===== Screen RCA v2: Specific issues =====

  private Single<List<ScreenRcaSpecificIssue>> fetchCrashSpecificIssues(
      String projectId, String screenName, RootCauseQueryBuilder.Window window,
      String dimensionColumn, String dimensionValue) {
    RootCauseQuerySpec spec = ScreenRcaQueryBuilder.buildCrashSpecificIssuesQuery(
        projectId, screenName, window.startInclusive, window.endExclusive,
        dimensionColumn, dimensionValue);
    return executeQuery(projectId, spec)
        .map(rows -> rows.stream()
            .map(row -> ScreenRcaSpecificIssue.builder()
                .groupId(String.valueOf(row.getOrDefault("group_id", "")))
                .issue(String.valueOf(row.getOrDefault("issue", "")))
                .count(NumberCoercionUtils.toLong(row.get("cnt")))
                .build())
            .toList());
  }

  private Single<List<ScreenRcaSpecificIssue>> fetchAnrSpecificIssues(
      String projectId, String screenName, RootCauseQueryBuilder.Window window,
      String dimensionColumn, String dimensionValue) {
    RootCauseQuerySpec spec = ScreenRcaQueryBuilder.buildAnrSpecificIssuesQuery(
        projectId, screenName, window.startInclusive, window.endExclusive,
        dimensionColumn, dimensionValue);
    return executeQuery(projectId, spec)
        .map(rows -> rows.stream()
            .map(row -> ScreenRcaSpecificIssue.builder()
                .groupId(String.valueOf(row.getOrDefault("group_id", "")))
                .issue(String.valueOf(row.getOrDefault("issue", "")))
                .threadName(String.valueOf(row.getOrDefault("thread", "")))
                .count(NumberCoercionUtils.toLong(row.get("cnt")))
                .build())
            .toList());
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
