package org.dreamhorizon.pulseserver.dao.rootcause;

import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.rootcause.models.ScreenRootCauseCacheRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

/**
 * Screen RCA cache in ClickHouse. Uses {@link org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService}'s
 * global read pool (server credentials) so per-project users do not require {@code INSERT} on {@code
 * otel.screen_root_cause_cache}; SQL builders must keep {@code ProjectId} literals aligned with the request.
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ScreenRootCauseCacheDao {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Reads the latest cache row by (projectId, screenName, window_start_date, window_end_date) in
   * UTC. If duplicates exist before merge, returns the row with max cached_at.
   */
  public Single<Optional<ScreenRootCauseCacheRow>> findByKey(
      String projectId, String screenName, LocalDate windowStartDate, LocalDate windowEndDate) {
    String startStr = windowStartDate.format(DATE_FMT);
    String endStr = windowEndDate.format(DATE_FMT);
    String query =
        ScreenRootCauseCacheQueries.buildSelectByKeyQuery(projectId, screenName, startStr, endStr);
    QueryConfiguration config =
        QueryConfiguration.newQuery(query).projectId(projectId).build();
    return clickhouseQueryService
        .executeGenericQueryWithGlobalPool(config, ScreenRootCauseCacheRow.class)
        .map(QueryResultResponse::getRows)
        .map(
            rows -> {
              if (rows.isEmpty()) {
                return Optional.empty();
              }
              return rows.stream()
                  .max(java.util.Comparator.comparing(ScreenRootCauseCacheRow::getCachedAt))
                  .or(() -> Optional.of(rows.get(0)));
            });
  }

  public Completable upsert(
      String projectId,
      String screenName,
      LocalDate windowStartDate,
      LocalDate windowEndDate,
      Instant windowStartInclusiveUtc,
      Instant windowEndExclusiveUtc,
      String resultJson,
      LocalDateTime cachedAt) {
    String startStr = windowStartDate.format(DATE_FMT);
    String endStr = windowEndDate.format(DATE_FMT);
    String query =
        ScreenRootCauseCacheQueries.buildInsertQuery(
            projectId,
            screenName,
            startStr,
            endStr,
            windowStartInclusiveUtc,
            windowEndExclusiveUtc,
            resultJson,
            cachedAt);
    QueryConfiguration config =
        QueryConfiguration.newQuery(query).projectId(projectId).build();
    return clickhouseQueryService
        .executeQueryWithGlobalPool(config)
        .ignoreElement()
        .onErrorResumeNext(
            e -> {
              log.error("Screen root cause cache upsert failed: {}", e.getMessage());
              return Completable.error(e);
            });
  }
}
