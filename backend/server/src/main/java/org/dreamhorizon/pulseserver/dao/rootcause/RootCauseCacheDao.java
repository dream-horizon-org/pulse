package org.dreamhorizon.pulseserver.dao.rootcause;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RootCauseCacheDao {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Reads the latest cache row by (projectId, interactionName, date).
   * If multiple rows exist (before ReplacingMergeTree merge), returns the one with max cached_at.
   */
  public Single<Optional<RootCauseCacheRow>> findByKey(
      String projectId, String interactionName, LocalDate date) {
    String dateStr = date.format(DATE_FMT);
    String query =
        RootCauseCacheQueries.buildSelectByKeyQuery(projectId, interactionName, dateStr);
    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .projectId(projectId)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config, RootCauseCacheRow.class)
        .map(QueryResultResponse::getRows)
        .map(rows -> {
          if (rows.isEmpty()) {
            return Optional.empty();
          }
          // If duplicates exist before merge, pick the latest by cached_at
          return rows.stream()
              .max(java.util.Comparator.comparing(RootCauseCacheRow::getCachedAt))
              .or(() -> Optional.of(rows.get(0)));
        });
  }

  /**
   * Inserts one cache row. ReplacingMergeTree(cached_at) keeps latest by cached_at.
   */
  public Completable upsert(
      String projectId,
      String interactionName,
      LocalDate date,
      Instant windowEndExclusiveUtc,
      String mode,
      String baselineJson,
      String segmentsJson,
      LocalDateTime cachedAt
  ) {
    String dateStr = date.format(DATE_FMT);
    String query = RootCauseCacheQueries.buildInsertQuery(
        projectId,
        interactionName,
        dateStr,
        windowEndExclusiveUtc,
        mode,
        baselineJson,
        segmentsJson,
        cachedAt);
    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .projectId(projectId)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config)
        .ignoreElement()
        .onErrorResumeNext(e -> {
          log.error("Root cause cache upsert failed: {}", e.getMessage());
          return Completable.error(e);
        });
  }
}
