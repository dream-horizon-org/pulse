package org.dreamhorizon.pulseserver.dao.rcareport;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RcaReportCacheDao {

  private final MysqlClient mysqlClient;

  /**
   * Returns the cached report body if present and not expired (TTL 24h).
   */
  public Maybe<String> get(String projectId, String interactionName, LocalDate date) {
    return mysqlClient.getReaderPool()
        .preparedQuery(RcaReportCacheQueries.GET_VALID)
        .rxExecute(Tuple.of(projectId, interactionName, date))
        .flatMapMaybe(rows -> {
          if (rows.size() == 0) {
            return Maybe.empty();
          }
          String body = rows.iterator().next().getString(0);
          return body == null || body.isBlank() ? Maybe.empty() : Maybe.just(body);
        })
        .doOnError(e -> log.warn("RCA report cache get failed: {}", e.getMessage()));
  }

  /**
   * Stores or updates the report for the given key.
   */
  public Completable put(String projectId, String interactionName, LocalDate date, String reportBody) {
    if (reportBody == null || reportBody.isBlank()) {
      return Completable.complete();
    }
    return mysqlClient.getWriterPool()
        .preparedQuery(RcaReportCacheQueries.UPSERT)
        .rxExecute(Tuple.of(projectId, interactionName, date, reportBody))
        .ignoreElement()
        .doOnComplete(() -> log.debug("RCA report cached: project={}, interaction={}, date={}", projectId, interactionName, date))
        .doOnError(e -> log.warn("RCA report cache put failed: {}", e.getMessage()));
  }
}
