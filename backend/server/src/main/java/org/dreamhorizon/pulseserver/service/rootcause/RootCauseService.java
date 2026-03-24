package org.dreamhorizon.pulseserver.service.rootcause;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
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
import jakarta.ws.rs.WebApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.dto.response.GetRawUserEventsResponseDto;
import org.dreamhorizon.pulseserver.dto.response.universalquerying.GetQueryDataResponseDto;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseSegment;
import org.dreamhorizon.pulseserver.util.ObjectMapperUtil;

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
          return Single.just(fromCacheRow(row));
        });
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

  private Single<Optional<Map<String, Object>>> runBaseline(
      String projectId,
      String interactionName,
      RootCauseQueryBuilder.Window window
  ) {
    String query = RootCauseQueryBuilder.buildBaselineQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive);
    return executeQuery(projectId, query)
        .map(rows -> rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0)));
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
    return Observable.fromIterable(dimOrder)
        .concatMapSingle(dim -> {
          String q = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
              projectId, interactionName, window.startInclusive, window.endExclusive, dim, null);
          return executeQuery(projectId, q)
              .map(rows -> pickClosestToTotal(rows, dim, totalProblematic, threshold));
        })
        .filter(Optional::isPresent)
        .firstElement()
        .switchIfEmpty(Maybe.just(Optional.<SegmentPath>empty()))
        .toSingle();
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
    String q = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive, dim, null);
    return executeQuery(projectId, q).flatMap(rows -> {
      Optional<Map.Entry<String, Long>> top = rows.stream()
          .map(r -> Map.entry(String.valueOf(r.get(dim)), toLong(r.get("problematic_count"))))
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
            return collectFlatExtrasFromDimensionIndex(
                projectId, interactionName, window, dimOrder, maxSegments, nextDimIndex, flatExtras)
                .flatMap(finalPath ->
                    materializeSegments(projectId, interactionName, window, baseline, finalPath));
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
      List<SegmentPath> flatExtras
  ) {
    if (flatExtras.size() >= maxSegments || index >= dimOrder.size()) {
      return Single.just(flatExtras);
    }
    String d = dimOrder.get(index);
    String q2 = RootCauseQueryBuilder.buildProblematicCountByDimensionQuery(
        projectId, interactionName, window.startInclusive, window.endExclusive, d, null);
    return executeQuery(projectId, q2).flatMap(r2 -> {
      Optional<Map.Entry<String, Long>> top = r2.stream()
          .map(row -> Map.entry(String.valueOf(row.get(d)), toLong(row.get("problematic_count"))))
          .filter(e -> e.getValue() > 0)
          .max(Map.Entry.comparingByValue());
      List<SegmentPath> next = new ArrayList<>(flatExtras);
      if (top.isPresent()) {
        next.add(new SegmentPath(d, top.get().getKey()));
      }
      if (next.size() >= maxSegments) {
        return Single.just(next);
      }
      return collectFlatExtrasFromDimensionIndex(
          projectId, interactionName, window, dimOrder, maxSegments, index + 1, next);
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
    LinkedHashMap<String, String> nextAcc = new LinkedHashMap<>(acc);
    nextAcc.put(p.dimension, p.value);
    String label = path.size() == 1
        ? p.dimension + ": " + p.value
        : String.join(" + ", nextAcc.values());
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
    String q = RootCauseQueryBuilder.buildSegmentQuery(
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

  private RootCauseResult fromCacheRow(RootCauseCacheRow row) {
    Map<String, Object> baseline = parseJsonMapOrThrow(row, row.getBaseline(), CACHE_FIELD_BASELINE);
    List<RootCauseSegment> segments =
        parseJsonSegmentsOrThrow(row, row.getSegments(), CACHE_FIELD_SEGMENTS);
    return RootCauseResult.builder()
        .baseline(baseline)
        .segments(segments)
        .mode(row.getMode())
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
    return ServiceError.ROOT_CAUSE_CACHE_INVALID.getException();
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
