package org.dreamhorizon.pulseserver.dao.sessionrca;

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
import org.dreamhorizon.pulseserver.dao.sessionrca.models.SessionRcaCacheRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

/**
 * Session RCA snapshot store in ClickHouse. Keyed by {@code (ProjectId, date)} — project-wide, no
 * entity name dimension (unlike interaction/screen RCA stores).
 */
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class SessionRcaCacheDao {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Returns the latest snapshot row for the project + anchor date. If duplicates exist before
   * ReplacingMergeTree merge, returns the row with max {@code cached_at}.
   */
  public Single<Optional<SessionRcaCacheRow>> findByKey(String projectId, LocalDate anchorDateUtc) {
    String dateStr = anchorDateUtc.format(DATE_FMT);
    SessionRcaCacheQueries.BoundStatement bound = SessionRcaCacheQueries.buildSelectByKey(projectId, dateStr);
    QueryConfiguration config = QueryConfiguration.newQuery(bound.sql()).projectId(projectId).build();
    return clickhouseQueryService
        .executeGenericQueryWithGlobalPoolBinds(
            config, SessionRcaCacheRow.class, bound.bindNames(), bound.bindValues())
        .map(QueryResultResponse::getRows)
        .map(rows -> {
          if (rows.isEmpty()) {
            return Optional.empty();
          }
          return rows.stream()
              .max(java.util.Comparator.comparing(SessionRcaCacheRow::getCachedAt))
              .or(() -> Optional.of(rows.get(0)));
        });
  }

  public Completable upsert(
      String projectId,
      LocalDate anchorDateUtc,
      Instant windowEndExclusiveUtc,
      String mode,
      String baselineJson,
      String segmentsJson,
      LocalDateTime cachedAt) {
    String dateStr = anchorDateUtc.format(DATE_FMT);
    SessionRcaCacheQueries.BoundStatement bound =
        SessionRcaCacheQueries.buildInsert(
            projectId, dateStr, windowEndExclusiveUtc, mode, baselineJson, segmentsJson, cachedAt);
    QueryConfiguration config = QueryConfiguration.newQuery(bound.sql()).projectId(projectId).build();
    return clickhouseQueryService
        .executeQueryWithGlobalPoolBinds(config, bound.bindNames(), bound.bindValues())
        .ignoreElement()
        .onErrorResumeNext(e -> {
          log.error("Session RCA snapshot upsert failed for project={}: {}", projectId, e.getMessage());
          return Completable.error(e);
        });
  }
}
