package org.dreamhorizon.pulseserver.service.rootcause;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.ws.rs.WebApplicationException;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;
import org.dreamhorizon.pulseserver.util.ClickhouseQueryRowUtils;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperUtil;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RootCauseService {

  private static final String CACHE_FIELD_BASELINE = "baseline";
  private static final String CACHE_FIELD_SEGMENTS = "segments";
  private static final String CACHE_PARSE_FAILED_DETAIL =
      "ClickHouse root_cause_cache row has invalid JSON in %s";

  private final RootCauseConfig config;
  private final ClickhouseQueryService clickhouseQueryService;
  private final RootCauseCacheDao cacheDao;
  private final ObjectMapperUtil objectMapper;

  /**
   * Returns root cause analysis for the interaction. Read-through persistence in ClickHouse;
   * computes on miss. {@code windowEndExclusiveUtc} is the exclusive upper bound on span timestamps
   * (typically request-time {@code Instant.now()}). Use {@link #getRootCause(String, String, LocalDate,
   * Instant, boolean)} with {@code forceRefresh true} to recompute and replace the stored row.
   */
  public Single<RootCauseResult> getRootCause(
      String projectId, String interactionName, LocalDate anchorDateUtc, Instant windowEndExclusiveUtc) {
    return getRootCause(projectId, interactionName, anchorDateUtc, windowEndExclusiveUtc, false);
  }

  /**
   * @param forceRefresh when true, skips reading {@code root_cause_cache} and recomputes
   */
  public Single<RootCauseResult> getRootCause(
      String projectId,
      String interactionName,
      LocalDate anchorDateUtc,
      Instant windowEndExclusiveUtc,
      boolean forceRefresh
  ) {
    final RootCauseQueryBuilder.Window window;
    try {
      window =
          new RootCauseQueryBuilder.Window(anchorDateUtc, config.getLookbackDays(), windowEndExclusiveUtc);
    } catch (IllegalArgumentException e) {
      return Single.error(ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(e.getMessage()));
    }
    if (forceRefresh) {
      return computeAndCache(projectId, interactionName, anchorDateUtc, window);
    }
    return cacheDao.findByKey(projectId, interactionName, anchorDateUtc)
        .flatMap(opt -> {
          if (opt.isEmpty()) {
            return computeAndCache(projectId, interactionName, anchorDateUtc, window);
          }
          return Single.just(fromCacheRow(opt.get()));
        });
  }

  /**
   * Non-empty {@code screen.name} values for spans with {@code pulse.interaction.name} matching
   * {@code interactionName} in the RCA window (aligned with session listing), ordered by descending
   * span count per screen (then name for ties). On ClickHouse error returns an empty list.
   */
  public Single<List<String>> fetchDistinctScreensForInteraction(
      String projectId, String interactionName, RootCauseQueryBuilder.Window window) {
    RootCauseQuerySpec spec =
        RootCauseQueryBuilder.buildDistinctScreensForInteractionQuery(
            projectId, interactionName, window);
    return executeQuery(projectId, spec)
        .map(RootCauseService::extractDistinctScreensFromRows)
        .onErrorResumeNext(
            e -> {
              log.warn(
                  "Distinct screens query failed for project={}, interaction={}: {}",
                  projectId,
                  interactionName,
                  e.getMessage());
              return Single.just(List.of());
            });
  }

  private static List<String> extractDistinctScreensFromRows(List<Map<String, Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return List.of();
    }
    return normalizeScreensValue(rows.get(0).get("screens"));
  }

  private static List<String> normalizeScreensValue(Object raw) {
    if (raw == null) {
      return List.of();
    }
    if (raw instanceof List) {
      return ((List<?>) raw)
          .stream()
          .filter(Objects::nonNull)
          .map(Object::toString)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .toList();
    }
    if (raw instanceof String[] arr) {
      return Arrays.stream(arr)
          .filter(Objects::nonNull)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .toList();
    }
    if (raw instanceof Object[] arr) {
      return Arrays.stream(arr)
          .filter(Objects::nonNull)
          .map(Object::toString)
          .map(String::trim)
          .filter(s -> !s.isEmpty())
          .toList();
    }
    return List.of();
  }

  private Single<RootCauseResult> computeAndCache(
      String projectId,
      String interactionName,
      LocalDate date,
      RootCauseQueryBuilder.Window window
  ) {
    return runBaseline(projectId, interactionName, window)
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
          Object vol = baselineRow.get(RootCauseMetricsRegistry.VOLUME);
          long volume = NumberCoercionUtils.toLong(vol);
          if (volume == 0) {
            return Single.just(RootCauseResult.builder()
                .noDataAvailable(true)
                .message("No data available")
                .baseline(toBaselineMap(baselineRow))
                .segments(List.of())
                .build());
          }
          long totalProblematic = NumberCoercionUtils.toLong(baselineRow.get("problematic_count"));
          if (totalProblematic == 0) {
            return Single.just(RootCauseResult.builder()
                .everythingGood(true)
                .message("Everything is good")
                .baseline(toBaselineMap(baselineRow))
                .segments(List.of())
                .mode(RootCauseAnalysisMode.FLAT)
                .build());
          }
          return runAlgorithm(projectId, interactionName, window, baselineRow, totalProblematic)
              .map(outcome -> RootCauseResult.builder()
                  .baseline(toBaselineMap(baselineRow))
                  .segments(outcome.segments())
                  .mode(outcome.mode())
                  .build());
        })
        .flatMap(result -> {
          if (result.getNoDataAvailable() != null && result.getNoDataAvailable()) {
            return Single.just(result);
          }
          List<RootCauseSegment> gated =
              applySignalGate(result.getSegments(), interactionName);
          if (gated != result.getSegments()) {
            result = result.toBuilder().segments(gated).build();
          }
          String baselineJson = objectMapper.writeValueAsString(result.getBaseline());
          String segmentsJson = objectMapper.writeValueAsString(result.getSegments());
          RootCauseAnalysisMode modeForCache =
              result.getMode() != null ? result.getMode() : RootCauseAnalysisMode.FLAT;
          return cacheDao.upsert( // add screen names for interaction name here
              projectId,
              interactionName,
              date,
              window.endExclusive,
              modeForCache.getWireValue(),
              baselineJson,
              segmentsJson,
              java.time.LocalDateTime.now(ZoneOffset.UTC)
          ).andThen(Single.just(result.toBuilder().cachedAt(Instant.now()).build()));
        });
  }

  /**
   * Segmentation outcome: mode follows the code path (flat vs hierarchy), not display labels — values
   * may contain ":" (e.g. geo names), and single-step hierarchy uses the same "Dim: value" label as flat.
   */
  private record SegmentsWithMode(List<RootCauseSegment> segments, RootCauseAnalysisMode mode) {}

  /**
   * Drops pre-LLM segments whose combined absolute delta {@code S = |Δerror_rate| +
   * |Δpoor_user_pct|} is below {@link RootCauseConfig#getMinCombinedDeltaSignal()}. Returns the
   * input list unchanged when the gate is disabled ({@code threshold <= 0}) or no segments are
   * dropped, so callers can detect a no-op via reference equality. Order of kept segments is
   * preserved.
   */
  private List<RootCauseSegment> applySignalGate(
      List<RootCauseSegment> segments, String interactionName) {
    if (segments == null || segments.isEmpty()) {
      return segments;
    }
    double threshold = config.getMinCombinedDeltaSignal();
    if (threshold <= 0) {
      return segments;
    }
    List<RootCauseSegment> kept = SegmentSignalGate.filter(segments, threshold);
    if (kept.size() == segments.size()) {
      return segments;
    }
    if (log.isDebugEnabled()) {
      for (RootCauseSegment s : segments) {
        if (!kept.contains(s)) {
          log.debug(
              "[RCA-SEGMENT] Drop segment below combined signal: interaction={}, label={}, S={}, threshold={}",
              interactionName,
              s.getLabel(),
              SegmentSignalGate.computeSignal(s),
              threshold);
        }
      }
    }
    log.info(
        "[RCA-SEGMENT] Signal gate filtered segments: interaction={}, kept={}/{}, threshold={}",
        interactionName,
        kept.size(),
        segments.size(),
        threshold);
    return kept;
  }

  private Single<Optional<Map<String, Object>>> runBaseline(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window
  ) {
    RootCauseQuerySpec query = RootCauseQueryBuilder.buildBaselineQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive);
    return executeQuery(projectId, query)
        .map(rows -> rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0)));
  }

  private Single<SegmentsWithMode> runAlgorithm(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      long totalProblematic
  ) {
    double threshold = totalProblematic * (config.getSimilarityThresholdPct() / 100.0);
    int maxSegments = config.getMaxSegments();
    boolean hybridEnabled = config.isHybridDimensionOrderingEnabled();

    log.debug("[RCA-SEGMENT] Algorithm start: interaction={}, totalProblematic={}, threshold={} ({}%), maxSegments={}, hybridEnabled={}",
        interactionName, totalProblematic, threshold, config.getSimilarityThresholdPct(), maxSegments, hybridEnabled);

    Single<List<String>> dimOrderSingle =
        hybridEnabled
            ? computeHybridDimensionOrder(
                projectId, interactionName, window, config.getDimensionOrder(), threshold)
            : Single.just(config.getDimensionOrder());

    return dimOrderSingle.flatMap(
        dimOrder -> {
          log.info("[RCA-SEGMENT] Dimension order: {} (hybridEnabled={})", dimOrder, hybridEnabled);
          return pickFirstDimension(projectId, interactionName, window, dimOrder, threshold, totalProblematic)
              .flatMap(
                  optFirst -> {
                    if (optFirst.isEmpty()) {
                      log.debug("[RCA-SEGMENT] No first dimension picked, falling to flat mode");
                      return buildFlatSegments(
                              projectId, interactionName, window, baseline, dimOrder, maxSegments)
                          .map(segments -> new SegmentsWithMode(segments, RootCauseAnalysisMode.FLAT));
                    }
                    FirstDimensionPick first = optFirst.get();
                    log.debug("[RCA-SEGMENT] First dimension picked: index={}, dim={}, value={}",
                        first.dimOrderIndex(), first.path().dimension(), first.path().value());
                    return buildHierarchyThenFlat(
                            projectId,
                            interactionName,
                            window,
                            baseline,
                            dimOrder,
                            maxSegments,
                            totalProblematic,
                            threshold,
                            first.dimOrderIndex(),
                            List.of(first.path()))
                        .map(
                            segments ->
                                new SegmentsWithMode(segments, RootCauseAnalysisMode.HIERARCHICAL));
                  });
        });
  }

  /**
   * Hybrid order: dimensions whose max bucket count is at or above {@code strongSignalThreshold}
   * first (descending max, then {@code baseOrder} for ties), then the rest in {@code baseOrder}.
   *
   * <p>Package-private for unit tests.
   */
  static List<String> hybridDimensionOrderFromPrecomputedMaxes(
      List<String> baseOrder,
      Map<String, Long> dimMaxProblematicByDimension,
      double strongSignalThreshold) {
    List<String> strongSignals =
        dimMaxProblematicByDimension.entrySet().stream()
            .filter(e -> e.getValue() >= strongSignalThreshold)
            .sorted(
                Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
                    .thenComparing(e -> baseOrderRank(baseOrder, e.getKey())))
            .map(Map.Entry::getKey)
            .toList();
    List<String> reordered = new ArrayList<>(strongSignals);
    for (String dim : baseOrder) {
      if (!reordered.contains(dim)) {
        reordered.add(dim);
      }
    }
    return reordered;
  }

  private static int baseOrderRank(List<String> baseOrder, String dimension) {
    int idx = baseOrder.indexOf(dimension);
    return idx >= 0 ? idx : Integer.MAX_VALUE;
  }

  private Single<List<String>> computeHybridDimensionOrder(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      List<String> baseOrder,
      double strongSignalThreshold) {
    if (baseOrder.isEmpty()) {
      return Single.just(List.of());
    }
    log.debug("[RCA-SEGMENT] Hybrid order computation start: interaction={}, threshold={}", interactionName, strongSignalThreshold);
    List<Single<Map.Entry<String, Long>>> maxQueries =
        baseOrder.stream()
            .map(
                dim ->
                    getMaxProblematicForDimension(projectId, interactionName, window, dim)
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
              hybridDimensionOrderFromPrecomputedMaxes(baseOrder, dimMaxMap, strongSignalThreshold);
          log.debug("[RCA-SEGMENT] Hybrid order computed: baseOrder={}, dimMaxMap={}, threshold={}, finalOrder={}",
              baseOrder, dimMaxMap, strongSignalThreshold, order);
          return order;
        });
  }

  private Single<Long> getMaxProblematicForDimension(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      String dimension) {
    RootCauseQuerySpec spec =
        RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
            projectId,
            interactionName,
            window.startInclusive,
            window.endExclusive,
            dimension,
            null);
    return executeQuery(projectId, spec)
        .map(
            rows ->
                rows.stream()
                    .mapToLong(r -> NumberCoercionUtils.toLong(r.get("problematic_count")))
                    .max()
                    .orElse(0L));
  }

  private Single<Optional<FirstDimensionPick>> pickFirstDimension(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      List<String> dimOrder,
      double threshold,
      long totalProblematic
  ) {
    log.debug("[RCA-SEGMENT] pickFirstDimension start: interaction={}, dimOrder={}, threshold={}, totalProblematic={}",
        interactionName, dimOrder, threshold, totalProblematic);
    return Observable.range(0, dimOrder.size())
        .concatMapMaybe(i -> {
          String dim = dimOrder.get(i);
          RootCauseQuerySpec q = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
              projectId, interactionName, window.startInclusive, window.endExclusive, dim, null);
          return executeQuery(projectId, q)
              .flatMapMaybe(rows -> {
                log.debug("[RCA-SEGMENT] pickFirstDimension dim={}: rowsCount={}, evaluating threshold={}", dim, rows.size(), threshold);
                Optional<SegmentPath> path = pickClosestToTotal(rows, dim, totalProblematic, threshold);
                if (path.isPresent()) {
                  log.debug("[RCA-SEGMENT] pickFirstDimension dim={}: PICKED value={}, dimension={}", dim, path.get().value(), path.get().dimension());
                } else {
                  log.debug("[RCA-SEGMENT] pickFirstDimension dim={}: NO PICK (no value met threshold)", dim);
                }
                return path.map(p -> Maybe.just(new FirstDimensionPick(i, p))).orElseGet(Maybe::empty);
              });
        })
        .firstElement()
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty())
        .doOnSuccess(result -> log.debug("[RCA-SEGMENT] pickFirstDimension result: {}", result));
  }

  private Single<List<RootCauseSegment>> buildFlatSegments(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<String> dimOrder,
      int maxSegments
  ) {
    return buildFlatSegmentsFromIndex(
        projectId, interactionName, window, baseline, dimOrder, maxSegments, 0, new ArrayList<>());
  }

  private Single<List<RootCauseSegment>> buildFlatSegmentsFromIndex(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<String> dimOrder,
      int maxSegments,
      int index,
      List<RootCauseSegment> accumulated
  ) {
    if (accumulated.size() >= maxSegments || index >= dimOrder.size()) {
      return Single.just(accumulated);
    }
    String dim = dimOrder.get(index);
    RootCauseQuerySpec q = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive, dim, null);
    return executeQuery(projectId, q).flatMap(rows -> {
      Optional<Map.Entry<String, Long>> top = rows.stream()
          .map(r -> Map.entry(String.valueOf(r.get(dim)), NumberCoercionUtils.toLong(r.get("problematic_count"))))
          .filter(e -> e.getValue() > 0)
          .max(Map.Entry.comparingByValue());
      if (top.isEmpty()) {
        return buildFlatSegmentsFromIndex(
            projectId, interactionName, window, baseline, dimOrder, maxSegments, index + 1, accumulated);
      }
      String value = top.get().getKey();
      Map<String, String> filters = Map.of(dim, value);
      String label = dim + ": " + value;
      return fetchSegmentMetrics(projectId, interactionName, window, baseline, label, filters)
          .flatMap(optSeg -> {
            List<RootCauseSegment> next = new ArrayList<>(accumulated);
            optSeg.ifPresent(next::add);
            if (next.size() >= maxSegments) {
              return Single.just(next);
            }
            return buildFlatSegmentsFromIndex(
                projectId, interactionName, window, baseline, dimOrder, maxSegments, index + 1, next);
          });
    });
  }

  private Single<List<RootCauseSegment>> buildHierarchyThenFlat(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<String> dimOrder,
      int maxSegments,
      long totalProblematic,
      double threshold,
      int hierarchyStartDimIndex,
      List<SegmentPath> path
  ) {
    if (path.size() >= maxSegments) {
      return materializeSegments(projectId, interactionName, window, baseline, path);
    }
    Map<String, String> currentFilters = path.stream()
        .collect(Collectors.toMap(s -> s.dimension, s -> s.value, (a, b) -> b));
    int nextDimIndex = hierarchyStartDimIndex + path.size();
    if (nextDimIndex >= dimOrder.size()) {
      return materializeSegments(projectId, interactionName, window, baseline, path);
    }
    String nextDim = dimOrder.get(nextDimIndex);
    RootCauseQuerySpec q = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive, nextDim, currentFilters);
    return executeQuery(projectId, q)
        .flatMap(rows -> {
          Optional<SegmentPath> picked = pickClosestToTotal(rows, nextDim, totalProblematic, threshold);
          if (picked.isEmpty()) {
            // Collect flat extras from ALL dimensions not yet in the hierarchy path
            // Start from index 0 to include dimensions before the hierarchy start
            java.util.Set<String> dimsInPath = path.stream()
                .map(s -> s.dimension)
                .collect(Collectors.toSet());
            List<SegmentPath> flatExtras = new ArrayList<>(path);
            return collectFlatExtrasFromDimensionIndex(
                projectId, interactionName, window, dimOrder, maxSegments, 0, flatExtras, dimsInPath)
                .flatMap(finalPath ->
                    materializeSegments(projectId, interactionName, window, baseline, finalPath));
          }
          List<SegmentPath> newPath = new ArrayList<>(path);
          newPath.add(picked.get());
          return buildHierarchyThenFlat(
              projectId, interactionName, window, baseline, dimOrder, maxSegments,
              totalProblematic, threshold, hierarchyStartDimIndex, newPath);
        });
  }

  private Single<List<RootCauseSegment>> materializeSegments(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<SegmentPath> path
  ) {
    return materializeSegmentsFromIndex(
        projectId, interactionName, window, baseline, path, 0, new LinkedHashMap<>(), new ArrayList<>());
  }

  private Single<List<SegmentPath>> collectFlatExtrasFromDimensionIndex(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      List<String> dimOrder,
      int maxSegments,
      int index,
      List<SegmentPath> flatExtras,
      java.util.Set<String> dimsInHierarchy
  ) {
    if (flatExtras.size() >= maxSegments || index >= dimOrder.size()) {
      return Single.just(flatExtras);
    }
    String d = dimOrder.get(index);
    // Skip dimensions already in the hierarchy path
    if (dimsInHierarchy.contains(d)) {
      return collectFlatExtrasFromDimensionIndex(
          projectId, interactionName, window, dimOrder, maxSegments, index + 1, flatExtras, dimsInHierarchy);
    }
    RootCauseQuerySpec q2 = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive, d, null);
    return executeQuery(projectId, q2).flatMap(r2 -> {
      Optional<Map.Entry<String, Long>> top = r2.stream()
          .map(row -> Map.entry(String.valueOf(row.get(d)), NumberCoercionUtils.toLong(row.get("problematic_count"))))
          .filter(e -> e.getValue() > 0)
          .max(Map.Entry.comparingByValue());
      List<SegmentPath> next = new ArrayList<>(flatExtras);
      if (top.isPresent()) {
        // Flat extras are standalone single-dimension filters
        next.add(new SegmentPath(d, top.get().getKey(), true));
      }
      if (next.size() >= maxSegments) {
        return Single.just(next);
      }
      return collectFlatExtrasFromDimensionIndex(
          projectId, interactionName, window, dimOrder, maxSegments, index + 1, next, dimsInHierarchy);
    });
  }

  private Single<List<RootCauseSegment>> materializeSegmentsFromIndex(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<SegmentPath> path,
      int index,
      LinkedHashMap<String, String> acc,
      List<RootCauseSegment> segments
  ) {
    if (index >= path.size()) {
      return Single.just(segments);
    }
    SegmentPath p = path.get(index);
    // Flat extras are standalone single-dimension filters, not cumulative with hierarchy
    LinkedHashMap<String, String> nextAcc;
    if (p.isFlatExtra) {
      nextAcc = new LinkedHashMap<>();
    } else {
      nextAcc = new LinkedHashMap<>(acc);
    }
    nextAcc.put(p.dimension, p.value);
    // Flat extras always use "Dimension: Value" label format
    String label;
    if (p.isFlatExtra) {
      label = p.dimension + ": " + p.value;
    } else {
      label = path.size() == 1
          ? p.dimension + ": " + p.value
          : String.join(" + ", nextAcc.values());
    }
    return fetchSegmentMetrics(
            projectId, interactionName, window, baseline, label, Map.copyOf(nextAcc))
        .flatMap(opt -> {
          List<RootCauseSegment> nextSegs = new ArrayList<>(segments);
          opt.ifPresent(nextSegs::add);
          return materializeSegmentsFromIndex(
              projectId, interactionName, window, baseline, path, index + 1, nextAcc, nextSegs);
        });
  }

  private Single<Optional<RootCauseSegment>> fetchSegmentMetrics(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      String label,
      Map<String, String> dimensionFilters
  ) {
    List<String> dims = new ArrayList<>(dimensionFilters.keySet());
    RootCauseQuerySpec q = RootCauseQueryBuilder.buildSegmentQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive, dims, dimensionFilters);
    return executeQuery(projectId, q)
        .map(rows -> {
          if (rows.isEmpty()) {
            return Optional.<RootCauseSegment>empty();
          }
          Map<String, Object> row = rows.get(0);
          Map<String, Double> deltas = computeDeltas(baseline, row);
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
      long totalProblematic,
      double threshold
  ) {
    log.debug("[RCA-SEGMENT] pickClosestToTotal: dimension={}, rows={}, totalProblematic={}, threshold={}",
        dimensionColumn, rows.size(), totalProblematic, threshold);
    SegmentPath best = null;
    long bestDiff = Long.MAX_VALUE;
    int considered = 0;
    int skipped = 0;
    for (Map<String, Object> row : rows) {
      long count = NumberCoercionUtils.toLong(row.get("problematic_count"));
      Object val = row.get(dimensionColumn);
      if (count < threshold) {
        skipped++;
        log.debug("[RCA-SEGMENT] pickClosestToTotal: SKIP value={}, count={} < threshold={}", val, count, threshold);
        continue;
      }
      considered++;
      long diff = Math.abs(count - totalProblematic);
      log.debug("[RCA-SEGMENT] pickClosestToTotal: CONSIDER value={}, count={}, diff={}, bestDiff={}", val, count, diff, bestDiff);
      if (diff < bestDiff) {
        bestDiff = diff;
        best = new SegmentPath(dimensionColumn, val != null ? val.toString() : "", false);
        log.debug("[RCA-SEGMENT] pickClosestToTotal: NEW BEST value={}, count={}, diff={}", val, count, diff);
      }
    }
    log.debug("[RCA-SEGMENT] pickClosestToTotal result: dimension={}, considered={}, skipped={}, picked={}",
        dimensionColumn, considered, skipped, best != null ? best.value() : "NONE");
    return Optional.ofNullable(best);
  }

  private Map<String, Double> computeDeltas(Map<String, Object> baseline, Map<String, Object> segment) {
    Map<String, Double> deltas = new LinkedHashMap<>();
    for (String metric : RootCauseMetricsRegistry.getMetricExpressions().keySet()) {
      Object b = baseline.get(metric);
      Object s = segment.get(metric);
      if (b == null || s == null) {
        continue;
      }
      double bv = NumberCoercionUtils.toDouble(b);
      double sv = NumberCoercionUtils.toDouble(s);
      if (metric.equals(RootCauseMetricsRegistry.VOLUME)) {
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

  private Single<List<Map<String, Object>>> executeQuery(String projectId, RootCauseQuerySpec spec) {
    return clickhouseQueryService
        .executeRootCauseQuery(
            projectId, spec.sql(), spec.bindNames(), spec.bindValues())
        .map(ClickhouseQueryRowUtils::rowsToMaps);
  }

  private static Map<String, Object> toBaselineMap(Map<String, Object> row) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (String key : RootCauseMetricsRegistry.getMetricExpressions().keySet()) {
      if (row.containsKey(key)) {
        m.put(key, row.get(key));
      }
    }
    if (row.containsKey("problematic_count")) {
      m.put("problematic_count", row.get("problematic_count"));
    }
    return m;
  }

  private RootCauseResult fromCacheRow(RootCauseCacheRow row) {
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
      RootCauseCacheRow cacheRow, String json, String fieldName) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
      if (parsed == null) {
        throw new IllegalStateException("parsed map is null");
      }
      return parsed;
    } catch (Exception e) {
      throw rootCauseCacheJsonInvalid(cacheRow, fieldName, e);
    }
  }

  private List<RootCauseSegment> parseJsonSegmentsOrThrow(
      RootCauseCacheRow cacheRow, String json, String fieldName) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<?> list = objectMapper.readValue(json, List.class);
      if (list == null) {
        throw new IllegalStateException("parsed list is null");
      }
      return list.stream()
          .map(m -> objectMapper.convertValue(m, RootCauseSegment.class))
          .toList();
    } catch (Exception e) {
      throw rootCauseCacheJsonInvalid(cacheRow, fieldName, e);
    }
  }

  private WebApplicationException rootCauseCacheJsonInvalid(
      RootCauseCacheRow cacheRow, String fieldName, Exception cause) {
    log.error(
        "Invalid root_cause_cache JSON ({}): projectId={} interactionName={} date={} — {}",
        String.format(CACHE_PARSE_FAILED_DETAIL, fieldName),
        cacheRow.getProjectId(),
        cacheRow.getInteractionName(),
        cacheRow.getDate(),
        cause.getMessage(),
        cause);
    return ServiceError.INTERNAL_SERVER_ERROR.getException();
  }

  private record FirstDimensionPick(int dimOrderIndex, SegmentPath path) {}

  private record SegmentPath(String dimension, String value, boolean isFlatExtra) {}
}
