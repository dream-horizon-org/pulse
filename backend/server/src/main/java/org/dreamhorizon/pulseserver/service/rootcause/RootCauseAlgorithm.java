package org.dreamhorizon.pulseserver.service.rootcause;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;

/**
 * Segment selection algorithm: total problematic -> first dimension threshold ->
 * add-dimension threshold -> flat fallback. Computes baseline + segment metrics + deltas.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RootCauseAlgorithm {

  private final RootCauseConfig config;
  private final RootCauseQueryBuilder queryBuilder;

  /**
   * Runs the full algorithm for the given interaction and time range.
   */
  public Single<RootCauseResult> run(String tenantId, String projectId, String interactionName,
      Instant startTime, Instant endTime) {
    return queryBuilder.getTotalProblematicCount(projectId, interactionName, startTime, endTime)
        .flatMap(totalProblematic -> {
          if (totalProblematic == 0) {
            return buildResultWithBaselineOnly(projectId, interactionName, startTime, endTime, 0L);
          }
          return selectSegmentsAndBuildResult(
              tenantId, projectId, interactionName, startTime, endTime, totalProblematic);
        })
        .onErrorResumeNext(e -> {
          log.warn("Root cause algorithm failed: {}", e.getMessage());
          return Single.just(RootCauseResult.builder()
              .noDataAvailable(true)
              .build());
        });
  }

  private Single<RootCauseResult> buildResultWithBaselineOnly(String projectId, String interactionName,
      Instant startTime, Instant endTime, long totalProblematic) {
    return runBaselineQuery(projectId, interactionName, startTime, endTime)
        .map(baseline -> RootCauseResult.builder()
            .mode(RootCauseResult.MODE_HIERARCHICAL)
            .baseline(baseline)
            .segments(List.of())
            .totalProblematicCount(totalProblematic)
            .everythingGood(true)
            .build());
  }

  private Single<RootCauseResult> selectSegmentsAndBuildResult(String tenantId, String projectId,
      String interactionName, Instant startTime, Instant endTime, long totalProblematic) {
    return findFirstDimensionSegment(projectId, interactionName, startTime, endTime, totalProblematic)
        .flatMap(first -> {
          if (first != null && first.problematicSharePct >= config.getFirstDimensionThresholdPct()) {
            return buildHierarchicalSegments(projectId, interactionName, startTime, endTime,
                totalProblematic, first.dimensionKey, first.dimensionValue);
          }
          return buildFlatSegments(projectId, interactionName, startTime, endTime, totalProblematic);
        })
        .map(result -> {
          result.setTotalProblematicCount(totalProblematic);
          return result;
        });
  }

  private static class DimensionCandidate {
    final String dimensionKey;
    final String dimensionValue;
    final long problematicCount;
    final double problematicSharePct;

    DimensionCandidate(String dimensionKey, String dimensionValue, long problematicCount, long total) {
      this.dimensionKey = dimensionKey;
      this.dimensionValue = dimensionValue;
      this.problematicCount = problematicCount;
      this.problematicSharePct = total > 0 ? (100.0 * problematicCount / total) : 0;
    }
  }

  private Single<DimensionCandidate> findFirstDimensionSegment(String projectId, String interactionName,
      Instant startTime, Instant endTime, long totalProblematic) {
    List<Single<List<RootCauseQueryBuilder.ProblematicSegmentCount>>> dimensionQueries = new ArrayList<>();
    for (String dim : RootCauseMetricsRegistry.DIMENSION_KEYS) {
      dimensionQueries.add(queryBuilder.getProblematicCountByDimension(
          projectId, interactionName, startTime, endTime, dim));
    }
    return Single.zip(dimensionQueries, results -> {
      DimensionCandidate best = null;
      for (Object r : results) {
        @SuppressWarnings("unchecked")
        List<RootCauseQueryBuilder.ProblematicSegmentCount> list =
            (List<RootCauseQueryBuilder.ProblematicSegmentCount>) r;
        if (list.isEmpty()) continue;
        RootCauseQueryBuilder.ProblematicSegmentCount top = list.get(0);
        if (top.dimensionValue == null || top.dimensionValue.isBlank()) continue;
        DimensionCandidate c = new DimensionCandidate(
            top.dimensionKey, top.dimensionValue, top.problematicCount, totalProblematic);
        if (best == null || c.problematicSharePct > best.problematicSharePct) {
          best = c;
        }
      }
      return best;
    });
  }

  private Single<RootCauseResult> buildHierarchicalSegments(String projectId, String interactionName,
      Instant startTime, Instant endTime, long totalProblematic,
      String firstDimKey, String firstDimValue) {
    Map<String, String> initialFilters = new LinkedHashMap<>();
    initialFilters.put(firstDimKey, firstDimValue);
    List<String> remainingDims = new ArrayList<>(RootCauseMetricsRegistry.DIMENSION_KEYS);
    remainingDims.remove(firstDimKey);
    return buildSegmentForFilters(projectId, interactionName, startTime, endTime, firstDimValue, initialFilters)
        .flatMap(firstSegment -> collectHierarchicalSegments(
            projectId, interactionName, startTime, endTime, totalProblematic,
            initialFilters, firstDimValue, remainingDims, List.of(firstSegment)));
  }

  private Single<RootCauseResult> collectHierarchicalSegments(String projectId, String interactionName,
      Instant startTime, Instant endTime, long totalProblematic,
      Map<String, String> currentFilters, String currentLabel, List<String> remainingDims,
      List<RootCauseResult.RootCauseSegment> segmentsSoFar) {
    if (remainingDims.isEmpty()) {
      return finishWithBaselineAndDeltas(projectId, interactionName, startTime, endTime,
          RootCauseResult.MODE_HIERARCHICAL, segmentsSoFar);
    }
    String nextDim = remainingDims.get(0);
    List<String> rest = remainingDims.subList(1, remainingDims.size());
    return queryBuilder.getProblematicCountByDimensionWithFilter(
            projectId, interactionName, startTime, endTime, currentFilters, nextDim)
        .flatMap(list -> {
          DimensionCandidate best = list.stream()
              .filter(p -> p.dimensionValue != null && !p.dimensionValue.isBlank())
              .map(p -> new DimensionCandidate(nextDim, p.dimensionValue, p.problematicCount, totalProblematic))
              .filter(c -> c.problematicSharePct >= config.getAddDimensionThresholdPct())
              .findFirst()
              .orElse(null);
          if (best == null) {
            return finishWithBaselineAndDeltas(projectId, interactionName, startTime, endTime,
                RootCauseResult.MODE_HIERARCHICAL, segmentsSoFar);
          }
          Map<String, String> newFilters = new LinkedHashMap<>(currentFilters);
          newFilters.put(best.dimensionKey, best.dimensionValue);
          String newLabel = currentLabel + " + " + best.dimensionValue;
          return buildSegmentForFilters(projectId, interactionName, startTime, endTime, newLabel, newFilters)
              .flatMap(newSegment -> {
                List<RootCauseResult.RootCauseSegment> extended = new ArrayList<>(segmentsSoFar);
                extended.add(newSegment);
                return collectHierarchicalSegments(
                    projectId, interactionName, startTime, endTime, totalProblematic,
                    newFilters, newLabel, rest, extended);
              });
        });
  }

  private Single<RootCauseResult> finishWithBaselineAndDeltas(String projectId, String interactionName,
      Instant startTime, Instant endTime, String mode, List<RootCauseResult.RootCauseSegment> segments) {
    return runBaselineQuery(projectId, interactionName, startTime, endTime)
        .map(baseline -> {
          List<RootCauseResult.RootCauseSegment> withDeltas = new ArrayList<>();
          for (RootCauseResult.RootCauseSegment seg : segments) {
            withDeltas.add(seg.toBuilder().deltas(computeDeltas(baseline, seg.getMetrics())).build());
          }
          return RootCauseResult.builder()
              .mode(mode)
              .baseline(baseline)
              .segments(withDeltas)
              .build();
        });
  }

  private Single<RootCauseResult.RootCauseSegment> buildSegmentForFilters(String projectId, String interactionName,
      Instant startTime, Instant endTime, String label, Map<String, String> filters) {
    RootCauseQueryRequest req = RootCauseQueryRequest.builder()
        .projectId(projectId)
        .interactionName(interactionName)
        .startTime(startTime)
        .endTime(endTime)
        .metricKeys(RootCauseMetricsRegistry.METRIC_KEYS)
        .dimensionFilters(filters)
        .limit(1)
        .build();
    return queryBuilder.execute(req)
        .map(rows -> {
          Map<String, Double> metrics = rows.isEmpty() ? Map.of() : toMetricsMap(rows.get(0));
          return RootCauseResult.RootCauseSegment.builder()
              .label(label)
              .dimensions(new LinkedHashMap<>(filters))
              .metrics(metrics)
              .build();
        });
  }

  private Single<RootCauseResult> buildFlatSegments(String projectId, String interactionName,
      Instant startTime, Instant endTime, long totalProblematic) {
    List<Single<RootCauseResult.RootCauseSegment>> segmentSingles = new ArrayList<>();
    for (String dim : RootCauseMetricsRegistry.DIMENSION_KEYS) {
      Single<List<RootCauseQueryBuilder.ProblematicSegmentCount>> byDim =
          queryBuilder.getProblematicCountByDimension(projectId, interactionName, startTime, endTime, dim);
      segmentSingles.add(byDim.flatMap(list -> {
        if (list.isEmpty()) return Single.just((RootCauseResult.RootCauseSegment) null);
        RootCauseQueryBuilder.ProblematicSegmentCount top = list.get(0);
        if (top.dimensionValue == null || top.dimensionValue.isBlank()) return Single.just((RootCauseResult.RootCauseSegment) null);
        String label = dim + " – " + top.dimensionValue;
        Map<String, String> filters = Map.of(dim, top.dimensionValue);
        return buildSegmentForFilters(projectId, interactionName, startTime, endTime, label, filters);
      }));
    }
    return Single.zip(segmentSingles, results -> {
      List<RootCauseResult.RootCauseSegment> list = new ArrayList<>();
      for (Object r : results) {
        if (r != null) list.add((RootCauseResult.RootCauseSegment) r);
      }
      return list;
    }).flatMap(segments -> finishWithBaselineAndDeltas(
        projectId, interactionName, startTime, endTime, RootCauseResult.MODE_FLAT, segments));
  }

  private Single<Map<String, Double>> runBaselineQuery(String projectId, String interactionName,
      Instant startTime, Instant endTime) {
    RootCauseQueryRequest req = RootCauseQueryRequest.builder()
        .projectId(projectId)
        .interactionName(interactionName)
        .startTime(startTime)
        .endTime(endTime)
        .metricKeys(RootCauseMetricsRegistry.METRIC_KEYS)
        .limit(1)
        .build();
    return queryBuilder.execute(req)
        .map(rows -> rows.isEmpty() ? new LinkedHashMap<String, Double>() : toMetricsMap(rows.get(0)));
  }

  private static Map<String, Double> toMetricsMap(Map<String, Object> row) {
    Map<String, Double> out = new LinkedHashMap<>();
    for (String key : RootCauseMetricsRegistry.METRIC_KEYS) {
      Object v = row.get(key);
      if (v != null) {
        if (v instanceof Number) {
          out.put(key, ((Number) v).doubleValue());
        } else {
          try {
            out.put(key, Double.parseDouble(v.toString()));
          } catch (NumberFormatException e) {
            // skip
          }
        }
      }
    }
    return out;
  }

  private static Map<String, Double> computeDeltas(Map<String, Double> baseline, Map<String, Double> metrics) {
    Map<String, Double> deltas = new LinkedHashMap<>();
    for (String key : RootCauseMetricsRegistry.METRIC_KEYS) {
      Double base = baseline.get(key);
      Double value = metrics.get(key);
      if (base == null || value == null) continue;
      if (key.equals("volume")) {
        deltas.put(key, base == 0 ? null : (value / base) * 100);
      } else {
        if (base == 0) continue;
        deltas.put(key, ((value - base) / base) * 100);
      }
    }
    return deltas;
  }
}
