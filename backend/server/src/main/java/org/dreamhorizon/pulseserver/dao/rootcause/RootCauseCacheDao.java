package org.dreamhorizon.pulseserver.dao.rootcause;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.chclient.ClickhouseQueryService;
import org.dreamhorizon.pulseserver.dao.rootcause.models.RootCauseCacheRow;
import org.dreamhorizon.pulseserver.model.QueryConfiguration;
import org.dreamhorizon.pulseserver.model.QueryResultResponse;

@Slf4j
@RequiredArgsConstructor
public class RootCauseCacheDao {

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
  private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final ClickhouseQueryService clickhouseQueryService;

  /**
   * Reads the latest cache row by (projectId, interactionName, date) using FINAL.
   */
  public Single<Optional<RootCauseCacheRow>> findByKey(String projectId, String interactionName, LocalDate date) {
    String dateStr = date.format(DATE_FMT);
    String query = "SELECT project_id, interaction_name, date, mode, baseline, segments, cached_at"
        + " FROM otel.root_cause_cache FINAL"
        + " WHERE project_id = '" + escape(projectId) + "'"
        + " AND interaction_name = '" + escape(interactionName) + "'"
        + " AND date = '" + dateStr + "'";
    QueryConfiguration config = QueryConfiguration.newQuery(query)
        .projectId(projectId)
        .build();
    return clickhouseQueryService.executeQueryOrCreateJob(config, RootCauseCacheRow.class)
        .map(QueryResultResponse::getRows)
        .map(rows -> rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0)));
  }

  /**
   * Inserts one cache row. ReplacingMergeTree(cached_at) keeps latest by cached_at.
   */
  public Completable upsert(
      String projectId,
      String interactionName,
      LocalDate date,
      String mode,
      String baselineJson,
      String segmentsJson,
      LocalDateTime cachedAt
  ) {
    String dateStr = date.format(DATE_FMT);
    String cachedAtStr = cachedAt.format(DATETIME_FMT);
    String query = "INSERT INTO otel.root_cause_cache (project_id, interaction_name, date, mode, baseline, segments, cached_at)"
        + " VALUES ("
        + "'" + escape(projectId) + "',"
        + "'" + escape(interactionName) + "',"
        + "'" + dateStr + "',"
        + "'" + escape(mode) + "',"
        + "'" + escapeJson(baselineJson) + "',"
        + "'" + escapeJson(segmentsJson) + "',"
        + "toDateTime64('" + cachedAtStr + "', 3, 'UTC')"
        + ")";
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

  private static String escape(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\").replace("'", "\\'");
  }

  private static String escapeJson(String s) {
    if (s == null) return "{}";
    return s.replace("\\", "\\\\").replace("'", "\\'");
  }
}
