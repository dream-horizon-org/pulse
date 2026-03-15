package org.dreamhorizon.pulseserver.service.rootcause;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.RootCauseConfig;
import org.dreamhorizon.pulseserver.dao.rootcause.RootCauseCacheDao;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.service.rootcause.models.RootCauseResult;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RootCauseService {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final RootCauseConfig config;
  private final RootCauseCacheDao cacheDao;
  private final RootCauseAlgorithm algorithm;

  /**
   * Returns root-cause result for the interaction: from cache if valid, else computes and caches.
   */
  public Single<RootCauseResult> getRootCause(String tenantId, String projectId, String interactionName,
      String date) {
    Instant[] range = resolveLookbackRange(date);
    Instant start = range[0];
    Instant end = range[1];
    String cacheDate = date != null && !date.isBlank()
        ? date
        : end.atZone(ZoneOffset.UTC).toLocalDate().toString();

    return cacheDao.get(tenantId, projectId, interactionName, cacheDate)
        .<RootCauseResult>flatMap(row -> {
          if (isCacheValid(row)) {
            return Single.just(fromCacheRow(row)).toMaybe();
          }
          return computeAndCache(tenantId, projectId, interactionName, cacheDate, start, end).toMaybe();
        })
        .switchIfEmpty(Single.defer(() ->
            computeAndCache(tenantId, projectId, interactionName, cacheDate, start, end)));
  }

  private boolean isCacheValid(RootCauseCacheRow row) {
    if (row.getCachedAt() == null) return false;
    long hoursSince = ChronoUnit.HOURS.between(row.getCachedAt(), Instant.now());
    return hoursSince < config.getCacheTtlHours();
  }

  private RootCauseResult fromCacheRow(RootCauseCacheRow row) {
    try {
      @SuppressWarnings("unchecked")
      var baseline = MAPPER.readValue(row.getBaseline(), java.util.Map.class);
      @SuppressWarnings("unchecked")
      var segments = MAPPER.readValue(row.getSegments(), List.class);
      return RootCauseResult.builder()
          .mode(row.getMode())
          .baseline(toDoubleMap(baseline))
          .segments(parseSegments(segments))
          .cachedAt(row.getCachedAt() != null ? row.getCachedAt().toString() : null)
          .build();
    } catch (JsonProcessingException e) {
      log.warn("Failed to parse cached root cause: {}", e.getMessage());
      return RootCauseResult.builder().noDataAvailable(true).build();
    }
  }

  @SuppressWarnings("unchecked")
  private static List<RootCauseResult.RootCauseSegment> parseSegments(List<?> list) {
    if (list == null) return List.of();
    return list.stream()
        .map(o -> MAPPER.convertValue(o, RootCauseResult.RootCauseSegment.class))
        .toList();
  }

  @SuppressWarnings("unchecked")
  private static java.util.Map<String, Double> toDoubleMap(java.util.Map<String, ?> map) {
    if (map == null) return new java.util.LinkedHashMap<>();
    java.util.Map<String, Double> out = new java.util.LinkedHashMap<>();
    map.forEach((k, v) -> {
      if (v instanceof Number) out.put(k, ((Number) v).doubleValue());
    });
    return out;
  }

  private Single<RootCauseResult> computeAndCache(String tenantId, String projectId, String interactionName,
      String cacheDate, Instant start, Instant end) {
    return algorithm.run(tenantId, projectId, interactionName, start, end)
        .flatMap(result -> {
          if (Boolean.TRUE.equals(result.getNoDataAvailable())) {
            return Single.just(result);
          }
          String baselineJson;
          String segmentsJson;
          try {
            baselineJson = MAPPER.writeValueAsString(result.getBaseline() != null ? result.getBaseline() : "{}");
            segmentsJson = MAPPER.writeValueAsString(result.getSegments() != null ? result.getSegments() : List.of());
          } catch (JsonProcessingException e) {
            return Single.just(result);
          }
          return cacheDao.upsert(tenantId, projectId, interactionName, cacheDate,
                  result.getMode() != null ? result.getMode() : RootCauseResult.MODE_HIERARCHICAL,
                  baselineJson, segmentsJson)
              .andThen(Single.just(result));
        });
  }

  private Instant[] resolveLookbackRange(String date) {
    Instant end = date != null && !date.isBlank()
        ? java.time.LocalDate.parse(date).atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
        : Instant.now();
    Instant start = end.minus(config.getLookbackDays(), ChronoUnit.DAYS);
    return new Instant[] { start, end };
  }
}
