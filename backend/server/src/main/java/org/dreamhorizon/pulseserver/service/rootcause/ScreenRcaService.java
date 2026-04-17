package org.dreamhorizon.pulseserver.service.rootcause;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseAnalysisMode;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.util.NumberCoercionUtils;

/**
 * Screen-scoped RCA over {@code app.click} logs (same segmentation algorithm as {@link RootCauseService},
 * driver metric {@link ScreenRcaQueryBuilder#BAD_FRUSTRATION}). No ClickHouse cache table.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ScreenRcaService {

  private final RootCauseConfig config;
  private final ClickhouseQueryService clickhouseQueryService;

  public Single<RootCauseResult> getScreenRootCause(
      String projectId, String screenName, LocalDate anchorDateUtc, Instant windowEndExclusiveUtc) {
    final RootCauseQueryBuilder.Window window;
    try {
      window =
          new RootCauseQueryBuilder.Window(anchorDateUtc, config.getLookbackDays(), windowEndExclusiveUtc);
    } catch (IllegalArgumentException e) {
      return Single.error(ServiceError.INCORRECT_OR_MISSING_QUERY_PARAMETERS.getCustomException(e.getMessage()));
    }
    return compute(projectId, screenName, window);
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
    List<String> dimOrder = config.getDimensionOrder();
    int maxSegments = config.getMaxSegments();

    return pickFirstDimension(projectId, screenName, window, dimOrder, threshold, totalBad)
        .flatMap(optFirst -> {
          if (optFirst.isEmpty()) {
            return buildFlatSegments(projectId, screenName, window, baseline, dimOrder, maxSegments)
                .map(segments -> new SegmentsWithMode(segments, RootCauseAnalysisMode.FLAT));
          }
          FirstDimensionPick first = optFirst.get();
          return buildHierarchyThenFlat(
                  projectId, screenName, window, baseline, dimOrder, maxSegments,
                  totalBad, threshold, first.dimOrderIndex(), List.of(first.path()))
              .map(segments -> new SegmentsWithMode(segments, RootCauseAnalysisMode.HIERARCHICAL));
        });
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
          .map(r -> Map.entry(String.valueOf(r.get(dim)), NumberCoercionUtils.toLong(r.get(ScreenRcaQueryBuilder.BAD_FRUSTRATION))))
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
        ScreenRcaQueryBuilder.buildBadFrustrationByDimensionQuery(
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
        ScreenRcaQueryBuilder.buildBadFrustrationByDimensionQuery(
            projectId, screenName, window.startInclusive, window.endExclusive, d, null);
    return executeQuery(projectId, q2).flatMap(r2 -> {
      Optional<Map.Entry<String, Long>> top = r2.stream()
          .map(row -> Map.entry(String.valueOf(row.get(d)), NumberCoercionUtils.toLong(row.get(ScreenRcaQueryBuilder.BAD_FRUSTRATION))))
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
      long count = NumberCoercionUtils.toLong(row.get(ScreenRcaQueryBuilder.BAD_FRUSTRATION));
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
        ScreenRcaQueryBuilder.TAP_COUNT,
        ScreenRcaQueryBuilder.RAGE_COUNT,
        ScreenRcaQueryBuilder.DEAD_COUNT,
        ScreenRcaQueryBuilder.BAD_FRUSTRATION);
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
