package org.dreamhorizon.pulseserver.service.rootcause;

import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RootCauseService {

  private final RootCauseConfig config;
  private final ClickhouseQueryService clickhouseQueryService;
  private final RootCauseCacheDao cacheDao;
  private final ObjectMapperUtil objectMapper;

  /**
   * Returns root cause analysis for the interaction. Read-through cache; computes on miss/expiry.
   */
  public Single<RootCauseResult> getRootCause(String projectId, String interactionName, LocalDate date) {
    RootCauseQueryBuilder.Window window = new RootCauseQueryBuilder.Window(date, config.getLookbackDays());
    long ttlHours = config.getCacheTtlHours();

    return cacheDao.findByKey(projectId, interactionName, date)
        .flatMap(opt -> {
          if (opt.isEmpty()) {
            return computeAndCache(projectId, interactionName, date, window);
          }
          RootCauseCacheRow row = opt.get();
          Instant cachedAt = row.getCachedAt().atZone(ZoneOffset.UTC).toInstant();
          if (ChronoUnit.HOURS.between(cachedAt, Instant.now()) >= ttlHours) {
            return computeAndCache(projectId, interactionName, date, window);
          }
          return Single.just(fromCacheRow(row, objectMapper));
        });
  }

  private Single<RootCauseResult> computeAndCache(
      String projectId,
      String interactionName,
      LocalDate date,
      RootCauseQueryBuilder.Window window
  ) {
    return runBaseline(projectId, interactionName, window)
        .flatMap(baselineRow -> {
          if (baselineRow == null) {
            return Single.just(RootCauseResult.builder()
                .noDataAvailable(true)
                .message("No data available")
                .baseline(Map.of())
                .segments(List.of())
                .build());
          }
          Object vol = baselineRow.get(RootCauseMetricsRegistry.VOLUME);
          long volume = toLong(vol);
          if (volume == 0) {
            return Single.just(RootCauseResult.builder()
                .noDataAvailable(true)
                .message("No data available")
                .baseline(toBaselineMap(baselineRow))
                .segments(List.of())
                .build());
          }
          long totalProblematic = toLong(baselineRow.get("problematic_count"));
          if (totalProblematic == 0) {
            return Single.just(RootCauseResult.builder()
                .everythingGood(true)
                .message("Everything is good")
                .baseline(toBaselineMap(baselineRow))
                .segments(List.of())
                .mode("flat")
                .build());
          }
          return runAlgorithm(projectId, interactionName, window, baselineRow, totalProblematic)
              .map(segments -> RootCauseResult.builder()
                  .baseline(toBaselineMap(baselineRow))
                  .segments(segments)
                  .mode(segments.isEmpty() ? "flat" : segments.get(0).getLabel().contains(":") ? "flat" : "hierarchical")
                  .build());
        })
        .flatMap(result -> {
          if (result.getNoDataAvailable() != null && result.getNoDataAvailable()) {
            return Single.just(result);
          }
          String baselineJson = objectMapper.writeValueAsString(result.getBaseline());
          String segmentsJson = objectMapper.writeValueAsString(result.getSegments());
          String mode = result.getMode() != null ? result.getMode() : "flat";
          return cacheDao.upsert(
              projectId,
              interactionName,
              date,
              mode,
              baselineJson,
              segmentsJson,
              java.time.LocalDateTime.now()
          ).andThen(Single.just(result.toBuilder().cachedAt(Instant.now()).build()));
        });
  }

  private Single<Map<String, Object>> runBaseline(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window
  ) {
    String query = RootCauseQueryBuilder.buildBaselineQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive);
    return executeQuery(projectId, query)
        .map(rows -> rows.isEmpty() ? null : rows.get(0));
  }

  private Single<List<RootCauseSegment>> runAlgorithm(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      long totalProblematic
  ) {
    double threshold = totalProblematic * (config.getSimilarityThresholdPct() / 100.0);
    List<String> dimOrder = config.getDimensionOrder();
    int maxSegments = config.getMaxSegments();

    return pickFirstDimension(projectId, interactionName, window, dimOrder, threshold, totalProblematic)
        .flatMap(optFirst -> {
          if (optFirst.isEmpty()) {
            return buildFlatSegments(projectId, interactionName, window, baseline, dimOrder, maxSegments);
          }
          SegmentPath first = optFirst.get();
          return buildHierarchyThenFlat(
              projectId, interactionName, window, baseline, dimOrder, maxSegments,
              totalProblematic, threshold, List.of(first));
        });
  }

  private Single<Optional<SegmentPath>> pickFirstDimension(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      List<String> dimOrder,
      double threshold,
      long totalProblematic
  ) {
    for (String dim : dimOrder) {
      String q = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
          projectId, interactionName, window.startInclusive, window.endExclusive, dim, null);
      Single<List<Map<String, Object>>> rows = executeQuery(projectId, q);
      List<Map<String, Object>> list = rows.blockingGet();
      Optional<SegmentPath> picked = pickClosestToTotal(list, dim, totalProblematic, threshold);
      if (picked.isPresent()) {
        return Single.just(picked);
      }
    }
    return Single.just(Optional.empty());
  }

  private Single<List<RootCauseSegment>> buildFlatSegments(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<String> dimOrder,
      int maxSegments
  ) {
    List<RootCauseSegment> out = new ArrayList<>();
    for (String dim : dimOrder) {
      if (out.size() >= maxSegments) break;
      String q = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
          projectId, interactionName, window.startInclusive, window.endExclusive, dim, null);
      List<Map<String, Object>> rows = executeQuery(projectId, q).blockingGet();
      Optional<Map.Entry<String, Long>> top = rows.stream()
          .map(r -> Map.entry(String.valueOf(r.get(dim)), toLong(r.get("problematic_count"))))
          .filter(e -> e.getValue() > 0)
          .max(Map.Entry.comparingByValue());
      if (top.isEmpty()) continue;
      String value = top.get().getKey();
      Map<String, String> filters = Map.of(dim, value);
      String label = dim + ": " + value;
      RootCauseSegment seg = fetchSegmentMetrics(projectId, interactionName, window, baseline, label, filters).blockingGet();
      if (seg != null) out.add(seg);
    }
    return Single.just(out);
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
      List<SegmentPath> path
  ) {
    if (path.size() >= maxSegments) {
      return materializeSegments(projectId, interactionName, window, baseline, path);
    }
    Map<String, String> currentFilters = path.stream()
        .collect(Collectors.toMap(s -> s.dimension, s -> s.value, (a, b) -> b));
    int nextDimIndex = path.size();
    if (nextDimIndex >= dimOrder.size()) {
      return materializeSegments(projectId, interactionName, window, baseline, path);
    }
    String nextDim = dimOrder.get(nextDimIndex);
    String q = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive, nextDim, currentFilters);
    return executeQuery(projectId, q)
        .flatMap(rows -> {
          Optional<SegmentPath> picked = pickClosestToTotal(rows, nextDim, totalProblematic, threshold);
          if (picked.isEmpty()) {
            List<SegmentPath> flatExtras = new ArrayList<>(path);
            for (int i = nextDimIndex; i < dimOrder.size() && flatExtras.size() < maxSegments; i++) {
              String d = dimOrder.get(i);
              String q2 = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
                  projectId, interactionName, window.startInclusive, window.endExclusive, d, null);
              List<Map<String, Object>> r2 = executeQuery(projectId, q2).blockingGet();
              Optional<Map.Entry<String, Long>> top = r2.stream()
                  .map(row -> Map.entry(String.valueOf(row.get(d)), toLong(row.get("problematic_count"))))
                  .filter(e -> e.getValue() > 0)
                  .max(Map.Entry.comparingByValue());
              if (top.isPresent()) {
                flatExtras.add(new SegmentPath(d, top.get().getKey()));
              }
            }
            return materializeSegments(projectId, interactionName, window, baseline, flatExtras);
          }
          List<SegmentPath> newPath = new ArrayList<>(path);
          newPath.add(picked.get());
          return buildHierarchyThenFlat(
              projectId, interactionName, window, baseline, dimOrder, maxSegments,
              totalProblematic, threshold, newPath);
        });
  }

  private Single<List<RootCauseSegment>> materializeSegments(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      List<SegmentPath> path
  ) {
    List<RootCauseSegment> segments = new ArrayList<>();
    Map<String, String> acc = new LinkedHashMap<>();
    for (SegmentPath p : path) {
      acc.put(p.dimension, p.value);
      String label = path.size() == 1
          ? p.dimension + ": " + p.value
          : String.join(" + ", acc.values());
      RootCauseSegment seg = fetchSegmentMetrics(
          projectId, interactionName, window, baseline, label, Map.copyOf(acc)).blockingGet();
      if (seg != null) segments.add(seg);
    }
    return Single.just(segments);
  }

  private Single<RootCauseSegment> fetchSegmentMetrics(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window,
      Map<String, Object> baseline,
      String label,
      Map<String, String> dimensionFilters
  ) {
    List<String> dims = new ArrayList<>(dimensionFilters.keySet());
    String q = RootCauseQueryBuilder.buildSegmentQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive, dims, dimensionFilters);
    return executeQuery(projectId, q)
        .map(rows -> {
          if (rows.isEmpty()) return null;
          Map<String, Object> row = rows.get(0);
          Map<String, Double> deltas = computeDeltas(baseline, row);
          return RootCauseSegment.builder()
              .label(label)
              .dimensions(new LinkedHashMap<>(dimensionFilters))
              .metrics(new LinkedHashMap<>(row))
              .deltas(deltas)
              .build();
        });
  }

  private Optional<SegmentPath> pickClosestToTotal(
      List<Map<String, Object>> rows,
      String dimensionColumn,
      long totalProblematic,
      double threshold
  ) {
    SegmentPath best = null;
    long bestDiff = Long.MAX_VALUE;
    for (Map<String, Object> row : rows) {
      long count = toLong(row.get("problematic_count"));
      if (count < threshold) continue;
      long diff = Math.abs(count - totalProblematic);
      if (diff < bestDiff) {
        bestDiff = diff;
        Object val = row.get(dimensionColumn);
        best = new SegmentPath(dimensionColumn, val != null ? val.toString() : "");
      }
    }
    return Optional.ofNullable(best);
  }

  private Map<String, Double> computeDeltas(Map<String, Object> baseline, Map<String, Object> segment) {
    Map<String, Double> deltas = new LinkedHashMap<>();
    for (String metric : RootCauseMetricsRegistry.getMetricExpressions().keySet()) {
      Object b = baseline.get(metric);
      Object s = segment.get(metric);
      if (b == null || s == null) continue;
      double bv = toDouble(b);
      double sv = toDouble(s);
      if (metric.equals(RootCauseMetricsRegistry.VOLUME)) {
        if (bv != 0) deltas.put(metric, (sv / bv) * 100 - 100);
      } else {
        if (bv != 0) deltas.put(metric, ((sv - bv) / bv) * 100);
      }
    }
    return deltas;
  }

  private Single<List<Map<String, Object>>> executeQuery(String projectId, String query) {
    QueryConfiguration config = QueryConfiguration.newQuery(query).projectId(projectId).build();
    return clickhouseQueryService.executeQueryOrCreateJob(config)
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

  private static Map<String, Object> toBaselineMap(Map<String, Object> row) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (String key : RootCauseMetricsRegistry.getMetricExpressions().keySet()) {
      if (row.containsKey(key)) m.put(key, row.get(key));
    }
    if (row.containsKey("problematic_count")) m.put("problematic_count", row.get("problematic_count"));
    return m;
  }

  private static RootCauseResult fromCacheRow(RootCauseCacheRow row, ObjectMapperUtil objectMapper) {
    Map<String, Object> baseline = parseJsonMap(row.getBaseline(), objectMapper);
    List<RootCauseSegment> segments = parseJsonSegments(row.getSegments(), objectMapper);
    return RootCauseResult.builder()
        .baseline(baseline)
        .segments(segments)
        .mode(row.getMode())
        .cachedAt(row.getCachedAt().atZone(ZoneOffset.UTC).toInstant())
        .build();
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJsonMap(String json, ObjectMapperUtil objectMapper) {
    if (json == null || json.isBlank()) return Map.of();
    try {
      return objectMapper.readValue(json, Map.class);
    } catch (Exception e) {
      return Map.of();
    }
  }

  @SuppressWarnings("unchecked")
  private static List<RootCauseSegment> parseJsonSegments(String json, ObjectMapperUtil objectMapper) {
    if (json == null || json.isBlank()) return List.of();
    try {
      List<?> list = objectMapper.readValue(json, List.class);
      return list.stream()
          .map(m -> objectMapper.convertValue(m, RootCauseSegment.class))
          .toList();
    } catch (Exception e) {
      return List.of();
    }
  }

  private static long toLong(Object o) {
    if (o == null) return 0;
    if (o instanceof Number n) return n.longValue();
    try {
      return Long.parseLong(o.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static double toDouble(Object o) {
    if (o == null) return 0;
    if (o instanceof Number n) return n.doubleValue();
    try {
      return Double.parseDouble(o.toString());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private record SegmentPath(String dimension, String value) {}
}
