package org.dreamhorizon.pulseserver.service.sessionrca;

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
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.ws.rs.WebApplicationException;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.sessionrca.SessionRcaCacheDao;
import org.dreamhorizon.pulseserver.dao.sessionrca.models.SessionRcaCacheRow;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseQueryBuilder;
import org.dreamhorizon.pulseserver.service.rootcause.RootCauseService;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.util.ClickhouseQueryRowUtils;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;
import org.dreamhorizon.pulseserver.util.serialization.ObjectMapperUtil;

/**
 * Session-scoped RCA over {@code otel.session_summary}. Segmentation algorithm ports
 * {@link RootCauseService} and {@link org.dreamhorizon.pulseserver.service.rootcause.ScreenRcaService}
 * exactly. Selection signal: {@code low_quality_count} (sessions where quality_score < µ − 2σ).
 * Impact flag: z_score < −2.0 → critical. Reuses {@link RootCauseService#hybridDimensionOrderFromPrecomputedMaxes}.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionRcaService {

  /**
   * Base dimension order for session RCA. Overridden by hybridDimensionOrdering when enabled.
   */
  static final List<String> SESSION_DIMENSION_ORDER = List.of(
      "platform", "osVersion", "appVersion", "startType",
      "SessionLength", "deviceModel", "networkProvider", "geoRegion");

  private static final String CACHE_FIELD_BASELINE = "baseline";
  private static final String CACHE_FIELD_SEGMENTS = "segments";

  private static final int EVIDENCE_SESSION_LIMIT = 2;

  private final RootCauseConfig config;
  private final ClickhouseQueryService clickhouseQueryService;
  private final SessionRcaCacheDao cacheDao;
  private final ObjectMapperUtil objectMapper;

  public Single<RootCauseResult> getSessionRca(
      String projectId, LocalDate anchorDateUtc, Instant windowEndExclusiveUtc) {
    return getSessionRca(projectId, anchorDateUtc, windowEndExclusiveUtc, false);
  }

  public Single<RootCauseResult> getSessionRca(
      String projectId,
      LocalDate anchorDateUtc,
      Instant windowEndExclusiveUtc,
      boolean forceRefresh) {
    final RootCauseQueryBuilder.Window window;
    try {
      window = new RootCauseQueryBuilder.Window(
          anchorDateUtc, config.getLookbackDays(), windowEndExclusiveUtc);
    } catch (IllegalArgumentException e) {
      return Single.error(
          ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(e.getMessage()));
    }
    if (forceRefresh) {
      return computeAndCache(projectId, anchorDateUtc, window);
    }
    return cacheDao.findByKey(projectId, anchorDateUtc)
        .flatMap(opt -> {
          if (opt.isEmpty()) {
            return computeAndCache(projectId, anchorDateUtc, window);
          }
          try {
            return Single.just(fromCacheRow(opt.get()));
          } catch (WebApplicationException e) {
            log.warn("session_rca_cache invalid row for project={}, date={}: {}",
                projectId, anchorDateUtc, e.getMessage());
            return computeAndCache(projectId, anchorDateUtc, window);
          }
        });
  }

  private Single<RootCauseResult> computeAndCache(
      String projectId, LocalDate date, RootCauseQueryBuilder.Window window) {
    return compute(projectId, window)
        .flatMap(result -> {
          if (result.getNoDataAvailable() != null && result.getNoDataAvailable()) {
            return Single.just(result);
          }
          String baselineJson = objectMapper.writeValueAsString(result.getBaseline());
          String segmentsJson = objectMapper.writeValueAsString(result.getSegments());
          RootCauseAnalysisMode modeForCache =
              result.getMode() != null ? result.getMode() : RootCauseAnalysisMode.FLAT;
          LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
          return cacheDao.upsert(
                  projectId, date, window.endExclusive,
                  modeForCache.getWireValue(), baselineJson, segmentsJson, now)
              .andThen(Single.just(result.toBuilder()
                  .cachedAt(now.atZone(ZoneOffset.UTC).toInstant())
                  .build()));
        });
  }

  private Single<RootCauseResult> compute(
      String projectId, RootCauseQueryBuilder.Window window) {
    return runBaseline(projectId, window)
        .flatMap(baselineRowOpt -> {
          if (baselineRowOpt.isEmpty()) {
            return Single.just(RootCauseResult.builder()
                .noDataAvailable(true).message("No data available")
                .baseline(Map.of()).segments(List.of()).build());
          }
          Map<String, Object> baselineRow = baselineRowOpt.get();
          long volume = NumberCoercionUtils.toLong(
              baselineRow.get(SessionRcaMetricsRegistry.VOLUME));
          if (volume == 0) {
            return Single.just(RootCauseResult.builder()
                .noDataAvailable(true).message("No data available")
                .baseline(toBaselineMap(baselineRow)).segments(List.of()).build());
          }
          double qualityMean = NumberCoercionUtils.toDouble(
              baselineRow.get(SessionRcaMetricsRegistry.QUALITY_SCORE_MEAN));
          double qualityStd = NumberCoercionUtils.toDouble(
              baselineRow.get(SessionRcaMetricsRegistry.QUALITY_SCORE_STD));
          double criticalThreshold = qualityStd < 0.01
              ? qualityMean * 0.80  // σ≈0 fallback: flag if > 20% below mean
              : qualityMean - 2.0 * qualityStd;

          return countLowQuality(projectId, window, criticalThreshold)
              .flatMap(totalLowQuality -> {
                if (totalLowQuality == 0) {
                  return Single.just(RootCauseResult.builder()
                      .everythingGood(true).message("Everything is good")
                      .baseline(toBaselineMap(baselineRow)).segments(List.of())
                      .mode(RootCauseAnalysisMode.FLAT).build());
                }
                return fetchPercentiles(projectId, window)
                    .flatMap(percentiles -> {
                      long p20Ms = NumberCoercionUtils.toLong(percentiles.get("p20"));
                      long p80Ms = NumberCoercionUtils.toLong(percentiles.get("p80"));
                      return runAlgorithm(
                          projectId, window, baselineRow,
                          qualityMean, qualityStd, criticalThreshold,
                          totalLowQuality, p20Ms, p80Ms)
                          .map(outcome -> RootCauseResult.builder()
                              .baseline(toBaselineMap(baselineRow))
                              .segments(outcome.segments())
                              .mode(outcome.mode())
                              .build());
                    });
              });
        });
  }

  private record SegmentsWithMode(List<RootCauseSegment> segments, RootCauseAnalysisMode mode) {
  }

  private Single<Optional<Map<String, Object>>> runBaseline(
      String projectId, RootCauseQueryBuilder.Window window) {
    var spec = SessionRcaQueryBuilder.buildBaselineQuery(
        projectId, window.startInclusive, window.endExclusive);
    return executeQuery(projectId, spec)
        .map(rows -> rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0)));
  }

  private Single<Long> countLowQuality(
      String projectId, RootCauseQueryBuilder.Window window, double criticalThreshold) {
    SessionRcaQueryBuilder.BindAccumulator acc = new SessionRcaQueryBuilder.BindAccumulator();
    String thresholdParam = acc.nextName();
    acc.add(thresholdParam, criticalThreshold);
    String where = SessionRcaQueryBuilder.baseWhereSql(
        acc, projectId, window.startInclusive, window.endExclusive);
    String sql = "SELECT " + SessionRcaMetricsRegistry.lowQualityCountExpr(thresholdParam)
        + " AS " + SessionRcaMetricsRegistry.LOW_QUALITY_COUNT
        + " FROM " + SessionRcaQueryBuilder.SESSION_SUMMARY_TABLE
        + " WHERE " + where;
    var spec = acc.toSpec(sql);
    return executeQuery(projectId, spec)
        .map(rows -> rows.isEmpty() ? 0L
            : NumberCoercionUtils.toLong(
            rows.get(0).get(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT)));
  }

  private Single<Map<String, Object>> fetchPercentiles(
      String projectId, RootCauseQueryBuilder.Window window) {
    var spec = SessionRcaQueryBuilder.buildSessionLengthPercentilesQuery(
        projectId, window.startInclusive, window.endExclusive);
    return executeQuery(projectId, spec)
        .map(rows -> rows.isEmpty() ? Map.of("p20", 0L, "p80", Long.MAX_VALUE) : rows.get(0));
  }

  private Single<SegmentsWithMode> runAlgorithm(
      String projectId,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      double qualityMean,
      double qualityStd,
      double criticalThreshold,
      long totalLowQuality,
      long p20Ms,
      long p80Ms) {
    double threshold = totalLowQuality * (config.getSimilarityThresholdPct() / 100.0);
    int maxSegments = config.getMaxSegments();
    boolean hybridEnabled = config.isHybridDimensionOrderingEnabled();

    log.debug("[SESSION-RCA] start: project={}, totalLowQuality={}, threshold={}, maxSegments={}, hybridEnabled={}",
        projectId, totalLowQuality, threshold, maxSegments, hybridEnabled);

    Single<List<String>> dimOrderSingle = hybridEnabled
        ? computeHybridDimensionOrder(
        projectId, window, SESSION_DIMENSION_ORDER, criticalThreshold, threshold, p20Ms, p80Ms)
        : Single.just(SESSION_DIMENSION_ORDER);

    return dimOrderSingle.flatMap(dimOrder -> {
      log.info("[SESSION-RCA] dimension order: {}", dimOrder);
      return pickFirstDimension(
          projectId, window, dimOrder, criticalThreshold, threshold, totalLowQuality, p20Ms, p80Ms)
          .flatMap(optFirst -> {
            if (optFirst.isEmpty()) {
              log.debug("[SESSION-RCA] no first dim picked, FLAT mode");
              return buildFlatSegments(
                  projectId, window, baseline, dimOrder, maxSegments,
                  qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms)
                  .map(segs -> new SegmentsWithMode(segs, RootCauseAnalysisMode.FLAT));
            }
            FirstDimensionPick first = optFirst.get();
            log.debug("[SESSION-RCA] hierarchical: dim={}, value={}", first.path().dimension(), first.path().value());
            return buildHierarchyThenFlat(
                projectId, window, baseline, dimOrder, maxSegments,
                totalLowQuality, threshold, first.dimOrderIndex(), List.of(first.path()),
                qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms)
                .map(segs -> new SegmentsWithMode(segs, RootCauseAnalysisMode.HIERARCHICAL));
          });
    });
  }

  private Single<List<String>> computeHybridDimensionOrder(
      String projectId,
      RootCauseQueryBuilder.Window window,
      List<String> baseOrder,
      double criticalThreshold,
      double strongSignalThreshold,
      long p20Ms,
      long p80Ms) {
    if (baseOrder.isEmpty()) {
      return Single.just(List.of());
    }
    List<Single<Map.Entry<String, Long>>> maxQueries = baseOrder.stream()
        .map(dim -> getMaxLowQualityForDimension(
            projectId, window, dim, criticalThreshold, p20Ms, p80Ms)
            .map(max -> Map.entry(dim, max)))
        .toList();
    return Single.zip(maxQueries, results -> {
      Map<String, Long> dimMaxMap = new HashMap<>();
      for (Object r : results) {
        @SuppressWarnings("unchecked")
        Map.Entry<String, Long> e = (Map.Entry<String, Long>) r;
        dimMaxMap.put(e.getKey(), e.getValue());
      }
      List<String> order = RootCauseService.hybridDimensionOrderFromPrecomputedMaxes(
          baseOrder, dimMaxMap, strongSignalThreshold);
      log.debug("[SESSION-RCA] hybrid order: {}", order);
      return order;
    });
  }

  private Single<Long> getMaxLowQualityForDimension(
      String projectId,
      RootCauseQueryBuilder.Window window,
      String dimension,
      double criticalThreshold,
      long p20Ms,
      long p80Ms) {
    var spec = SessionRcaQueryBuilder.buildLowQualityCountByDimensionQuery(
        projectId, window.startInclusive, window.endExclusive,
        dimension, null, criticalThreshold, p20Ms, p80Ms);
    return executeQuery(projectId, spec)
        .map(rows -> rows.stream()
            .mapToLong(r -> NumberCoercionUtils.toLong(
                r.get(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT)))
            .max().orElse(0L));
  }

  private Single<Optional<FirstDimensionPick>> pickFirstDimension(
      String projectId,
      RootCauseQueryBuilder.Window window,
      List<String> dimOrder,
      double criticalThreshold,
      double threshold,
      long totalLowQuality,
      long p20Ms,
      long p80Ms) {
    return Observable.range(0, dimOrder.size())
        .concatMapMaybe(i -> {
          String dim = dimOrder.get(i);
          var spec = SessionRcaQueryBuilder.buildLowQualityCountByDimensionQuery(
              projectId, window.startInclusive, window.endExclusive,
              dim, null, criticalThreshold, p20Ms, p80Ms);
          return executeQuery(projectId, spec)
              .flatMapMaybe(rows -> {
                Optional<SegmentPath> path = pickClosestToTotal(rows, dim, totalLowQuality, threshold);
                return path.map(p -> Maybe.just(new FirstDimensionPick(i, p)))
                    .orElseGet(Maybe::empty);
              });
        })
        .firstElement()
        .map(Optional::of)
        .defaultIfEmpty(Optional.empty());
  }

  private Single<List<RootCauseSegment>> buildFlatSegments(
      String projectId,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<String> dimOrder,
      int maxSegments,
      double qualityMean,
      double qualityStd,
      double criticalThreshold,
      long p20Ms,
      long p80Ms) {
    return buildFlatSegmentsFromIndex(
        projectId, window, baseline, dimOrder, maxSegments, 0, new ArrayList<>(),
        qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms);
  }

  private Single<List<RootCauseSegment>> buildFlatSegmentsFromIndex(
      String projectId,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<String> dimOrder,
      int maxSegments,
      int index,
      List<RootCauseSegment> accumulated,
      double qualityMean,
      double qualityStd,
      double criticalThreshold,
      long p20Ms,
      long p80Ms) {
    if (accumulated.size() >= maxSegments || index >= dimOrder.size()) {
      return Single.just(accumulated);
    }
    String dim = dimOrder.get(index);
    var spec = SessionRcaQueryBuilder.buildLowQualityCountByDimensionQuery(
        projectId, window.startInclusive, window.endExclusive,
        dim, null, criticalThreshold, p20Ms, p80Ms);
    return executeQuery(projectId, spec).flatMap(rows -> {
      Optional<Map.Entry<String, Long>> top = rows.stream()
          .map(r -> Map.entry(
              String.valueOf(r.get(dim)),
              NumberCoercionUtils.toLong(r.get(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT))))
          .filter(e -> e.getValue() > 0)
          .max(Map.Entry.comparingByValue());
      if (top.isEmpty()) {
        return buildFlatSegmentsFromIndex(
            projectId, window, baseline, dimOrder, maxSegments, index + 1, accumulated,
            qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms);
      }
      String value = top.get().getKey();
      Map<String, String> filters = Map.of(dim, value);
      String label = dim + ": " + value;
      return fetchAndMaterializeSegment(
          projectId, window, baseline, label, List.of(dim), filters,
          qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms)
          .flatMap(optSeg -> {
            List<RootCauseSegment> next = new ArrayList<>(accumulated);
            optSeg.ifPresent(next::add);
            return buildFlatSegmentsFromIndex(
                projectId, window, baseline, dimOrder, maxSegments, index + 1, next,
                qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms);
          });
    });
  }

  private Single<List<RootCauseSegment>> buildHierarchyThenFlat(
      String projectId,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<String> dimOrder,
      int maxSegments,
      long totalLowQuality,
      double threshold,
      int hierarchyStartDimIndex,
      List<SegmentPath> path,
      double qualityMean,
      double qualityStd,
      double criticalThreshold,
      long p20Ms,
      long p80Ms) {
    if (path.size() >= maxSegments) {
      return materializeSegments(projectId, window, baseline, path, qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms);
    }
    Map<String, String> currentFilters = path.stream()
        .collect(Collectors.toMap(s -> s.dimension, s -> s.value, (a, b) -> b));
    int nextDimIndex = hierarchyStartDimIndex + path.size();
    if (nextDimIndex >= dimOrder.size()) {
      return materializeSegments(projectId, window, baseline, path, qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms);
    }
    String nextDim = dimOrder.get(nextDimIndex);
    var spec = SessionRcaQueryBuilder.buildLowQualityCountByDimensionQuery(
        projectId, window.startInclusive, window.endExclusive,
        nextDim, currentFilters, criticalThreshold, p20Ms, p80Ms);
    return executeQuery(projectId, spec).flatMap(rows -> {
      Optional<SegmentPath> picked = pickClosestToTotal(rows, nextDim, totalLowQuality, threshold);
      if (picked.isEmpty()) {
        Set<String> dimsInPath = path.stream().map(s -> s.dimension).collect(Collectors.toSet());
        List<SegmentPath> flatExtras = new ArrayList<>(path);
        return collectFlatExtrasFromDimensionIndex(
            projectId, window, dimOrder, maxSegments, 0, flatExtras, dimsInPath,
            criticalThreshold, p20Ms, p80Ms)
            .flatMap(finalPath ->
                materializeSegments(projectId, window, baseline, finalPath, qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms));
      }
      List<SegmentPath> newPath = new ArrayList<>(path);
      newPath.add(picked.get());
      return buildHierarchyThenFlat(
          projectId, window, baseline, dimOrder, maxSegments,
          totalLowQuality, threshold, hierarchyStartDimIndex, newPath,
          qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms);
    });
  }

  private Single<List<SegmentPath>> collectFlatExtrasFromDimensionIndex(
      String projectId,
      RootCauseQueryBuilder.Window window,
      List<String> dimOrder,
      int maxSegments,
      int index,
      List<SegmentPath> flatExtras,
      Set<String> dimsInHierarchy,
      double criticalThreshold,
      long p20Ms,
      long p80Ms) {
    if (flatExtras.size() >= maxSegments || index >= dimOrder.size()) {
      return Single.just(flatExtras);
    }
    String d = dimOrder.get(index);
    if (dimsInHierarchy.contains(d)) {
      return collectFlatExtrasFromDimensionIndex(
          projectId, window, dimOrder, maxSegments, index + 1,
          flatExtras, dimsInHierarchy, criticalThreshold, p20Ms, p80Ms);
    }
    var spec = SessionRcaQueryBuilder.buildLowQualityCountByDimensionQuery(
        projectId, window.startInclusive, window.endExclusive,
        d, null, criticalThreshold, p20Ms, p80Ms);
    return executeQuery(projectId, spec).flatMap(rows -> {
      Optional<Map.Entry<String, Long>> top = rows.stream()
          .map(r -> Map.entry(
              String.valueOf(r.get(d)),
              NumberCoercionUtils.toLong(r.get(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT))))
          .filter(e -> e.getValue() > 0)
          .max(Map.Entry.comparingByValue());
      List<SegmentPath> next = new ArrayList<>(flatExtras);
      top.ifPresent(e -> next.add(new SegmentPath(d, e.getKey(), true)));
      if (next.size() >= maxSegments) {
        return Single.just(next);
      }
      return collectFlatExtrasFromDimensionIndex(
          projectId, window, dimOrder, maxSegments, index + 1,
          next, dimsInHierarchy, criticalThreshold, p20Ms, p80Ms);
    });
  }

  private Single<List<RootCauseSegment>> materializeSegments(
      String projectId,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<SegmentPath> path,
      double qualityMean,
      double qualityStd,
      double criticalThreshold,
      long p20Ms,
      long p80Ms) {
    return materializeSegmentsFromIndex(
        projectId, window, baseline, path, 0, new LinkedHashMap<>(), new ArrayList<>(),
        qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms);
  }

  private Single<List<RootCauseSegment>> materializeSegmentsFromIndex(
      String projectId,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<SegmentPath> path,
      int index,
      LinkedHashMap<String, String> acc,
      List<RootCauseSegment> segments,
      double qualityMean,
      double qualityStd,
      double criticalThreshold,
      long p20Ms,
      long p80Ms) {
    if (index >= path.size()) {
      return Single.just(segments);
    }
    SegmentPath p = path.get(index);
    LinkedHashMap<String, String> nextAcc = p.isFlatExtra ? new LinkedHashMap<>() : new LinkedHashMap<>(acc);
    nextAcc.put(p.dimension, p.value);

    // Skip intermediate hierarchy nodes — only materialize the deepest node in the chain.
    // e.g. [appVersion:3.1.0, platform:Android] → skip appVersion alone, only emit the combined.
    boolean isIntermediateHierarchy = !p.isFlatExtra
        && index + 1 < path.size()
        && !path.get(index + 1).isFlatExtra;
    if (isIntermediateHierarchy) {
      return materializeSegmentsFromIndex(
          projectId, window, baseline, path, index + 1, nextAcc, segments,
          qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms);
    }

    String label = p.isFlatExtra || path.size() == 1
        ? p.dimension + ": " + p.value
        : String.join(" + ", nextAcc.values());
    return fetchAndMaterializeSegment(
        projectId, window, baseline, label,
        new ArrayList<>(nextAcc.keySet()), Map.copyOf(nextAcc),
        qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms)
        .flatMap(optSeg -> {
          List<RootCauseSegment> nextSegs = new ArrayList<>(segments);
          optSeg.ifPresent(nextSegs::add);
          return materializeSegmentsFromIndex(
              projectId, window, baseline, path, index + 1, nextAcc, nextSegs,
              qualityMean, qualityStd, criticalThreshold, p20Ms, p80Ms);
        });
  }

  private Single<Optional<RootCauseSegment>> fetchAndMaterializeSegment(
      String projectId,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      String label,
      List<String> dimColumns,
      Map<String, String> dimensionFilters,
      double qualityMean,
      double qualityStd,
      double criticalThreshold,
      long p20Ms,
      long p80Ms) {
    double baselineVolume = NumberCoercionUtils.toDouble(
        baseline.get(SessionRcaMetricsRegistry.VOLUME));
    double minVolumeAbs = baselineVolume * (config.getMinSegmentVolumePct() / 100.0);

    var spec = SessionRcaQueryBuilder.buildSegmentQuery(
        projectId, window.startInclusive, window.endExclusive,
        dimColumns, dimensionFilters, p20Ms, p80Ms);
    return executeQuery(projectId, spec).flatMap(rows -> {
      if (rows.isEmpty()) {
        return Single.just(Optional.<RootCauseSegment>empty());
      }
      Map<String, Object> row = rows.get(0);
      long segVolume = NumberCoercionUtils.toLong(row.get(SessionRcaMetricsRegistry.VOLUME));
      if (segVolume < minVolumeAbs) {
        log.debug("[SESSION-RCA] volume guard: segment='{}' volume={} < minVolumeAbs={}, excluded",
            label, segVolume, minVolumeAbs);
        return Single.just(Optional.<RootCauseSegment>empty());
      }
      double segQuality = NumberCoercionUtils.toDouble(
          row.get(SessionRcaMetricsRegistry.QUALITY_SCORE));
      double zScore = qualityStd < 0.01 ? 0.0 : (segQuality - qualityMean) / qualityStd;
      String impact;
      if (qualityStd < 0.01) {
        // σ≈0: no spread to compute z_score against — use same delta rule as criticalThreshold
        impact = segQuality < qualityMean * 0.80
            ? SessionRcaMetricsRegistry.IMPACT_CRITICAL
            : SessionRcaMetricsRegistry.IMPACT_NORMAL;
      } else {
        impact = zScore < -2.0
            ? SessionRcaMetricsRegistry.IMPACT_CRITICAL
            : SessionRcaMetricsRegistry.IMPACT_NORMAL;
      }

      Map<String, Object> metrics = new LinkedHashMap<>(row);
      metrics.put(SessionRcaMetricsRegistry.Z_SCORE, zScore);
      metrics.put(SessionRcaMetricsRegistry.IMPACT, impact);
      Map<String, Double> deltas = computeDeltas(baseline, row);

      var evidenceSpec = SessionRcaQueryBuilder.buildExampleSessionsQuery(
          projectId, window.startInclusive, window.endExclusive,
          dimensionFilters, criticalThreshold, EVIDENCE_SESSION_LIMIT);
      return executeQuery(projectId, evidenceSpec)
          .map(evidenceRows -> {
            List<String> exampleSessionIds = evidenceRows.stream()
                .map(r -> {
                  Object sid = r.get("sessionId");
                  return sid != null ? sid.toString() : null;
                })
                .filter(sid -> sid != null && !sid.isBlank())
                .toList();
            RootCauseSegment segment = RootCauseSegment.builder()
                .label(label)
                .dimensions(new LinkedHashMap<>(dimensionFilters))
                .metrics(metrics)
                .deltas(deltas)
                .exampleSessionIds(exampleSessionIds.isEmpty() ? null : exampleSessionIds)
                .build();
            return Optional.of(segment);
          })
          .onErrorReturnItem(Optional.of(RootCauseSegment.builder()
              .label(label)
              .dimensions(new LinkedHashMap<>(dimensionFilters))
              .metrics(metrics)
              .deltas(deltas)
              .build()));
    });
  }

  private Optional<SegmentPath> pickClosestToTotal(
      List<Map<String, Object>> rows,
      String dimensionColumn,
      long totalLowQuality,
      double threshold) {
    SegmentPath best = null;
    long bestDiff = Long.MAX_VALUE;
    for (Map<String, Object> row : rows) {
      long count = NumberCoercionUtils.toLong(row.get(SessionRcaMetricsRegistry.LOW_QUALITY_COUNT));
      if (count < threshold) {
        continue;
      }
      long diff = Math.abs(count - totalLowQuality);
      if (diff < bestDiff) {
        bestDiff = diff;
        Object val = row.get(dimensionColumn);
        best = new SegmentPath(dimensionColumn, val != null ? val.toString() : "", false);
      }
    }
    return Optional.ofNullable(best);
  }

  private Map<String, Double> computeDeltas(
      Map<String, Object> baseline, Map<String, Object> segment) {
    Map<String, Double> deltas = new LinkedHashMap<>();
    for (String metric : List.of(
        SessionRcaMetricsRegistry.VOLUME, SessionRcaMetricsRegistry.QUALITY_SCORE)) {
      Object b = baseline.get(metric);
      Object s = segment.get(metric);
      if (b == null || s == null) {
        continue;
      }
      double bv = NumberCoercionUtils.toDouble(b);
      double sv = NumberCoercionUtils.toDouble(s);
      if (bv == 0) {
        continue;
      }
      if (metric.equals(SessionRcaMetricsRegistry.VOLUME)) {
        deltas.put(metric, (sv / bv) * 100 - 100);
      } else {
        deltas.put(metric, ((sv - bv) / bv) * 100);
      }
    }
    return deltas;
  }

  private Single<List<Map<String, Object>>> executeQuery(
      String projectId, org.dreamhorizon.pulseserver.service.rootcause.RootCauseQuerySpec spec) {
    return clickhouseQueryService
        .executeRootCauseQuery(projectId, spec.sql(), spec.bindNames(), spec.bindValues())
        .map(ClickhouseQueryRowUtils::rowsToMaps);
  }

  private static Map<String, Object> toBaselineMap(Map<String, Object> row) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (String key : List.of(
        SessionRcaMetricsRegistry.VOLUME,
        SessionRcaMetricsRegistry.QUALITY_SCORE,
        SessionRcaMetricsRegistry.QUALITY_SCORE_MEAN,
        SessionRcaMetricsRegistry.QUALITY_SCORE_STD)) {
      if (row.containsKey(key)) {
        m.put(key, row.get(key));
      }
    }
    return m;
  }

  private RootCauseResult fromCacheRow(SessionRcaCacheRow row) {
    Map<String, Object> baseline = parseJsonMap(row.getBaseline());
    List<RootCauseSegment> segments = parseJsonSegments(row.getSegments());
    return RootCauseResult.builder()
        .baseline(baseline)
        .segments(segments)
        .mode(RootCauseAnalysisMode.fromWireValue(row.getMode()))
        .cachedAt(row.getCachedAt().atZone(ZoneOffset.UTC).toInstant())
        .build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseJsonMap(String json) {
    if (json == null || json.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Object> parsed = objectMapper.readValue(json, Map.class);
      return parsed != null ? parsed : Map.of();
    } catch (Exception e) {
      log.error("session_rca_cache: failed to parse baseline JSON: {}", e.getMessage());
      throw ServiceError.INTERNAL_SERVER_ERROR.getException();
    }
  }

  private List<RootCauseSegment> parseJsonSegments(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<?> list = objectMapper.readValue(json, List.class);
      if (list == null) {
        return List.of();
      }
      return list.stream()
          .map(m -> objectMapper.convertValue(m, RootCauseSegment.class))
          .toList();
    } catch (Exception e) {
      log.error("session_rca_cache: failed to parse segments JSON: {}", e.getMessage());
      throw ServiceError.INTERNAL_SERVER_ERROR.getException();
    }
  }

  private record FirstDimensionPick(int dimOrderIndex, SegmentPath path) {
  }

  private record SegmentPath(String dimension, String value, boolean isFlatExtra) {
  }
}
