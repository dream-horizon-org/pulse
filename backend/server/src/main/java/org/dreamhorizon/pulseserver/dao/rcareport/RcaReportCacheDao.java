package org.dreamhorizon.pulseserver.dao.rcareport;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.rcajob.RcaType;
import org.dreamhorizon.pulseserver.dao.rcareport.models.RcaReportCacheHit;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RcaReportCacheDao {

  private final MysqlClient mysqlClient;

  /** Returns the cached report body if present for the key (no time-based expiry). */
  public Maybe<RcaReportCacheHit> get(
      String projectId, RcaType type, String entityKey, LocalDate date) {
    return getFromPool(mysqlClient.getReaderPool(), projectId, type, entityKey, date);
  }

  /**
   * Like {@link #get} but reads from the writer (primary) pool. Use when the report was just
   * written and replica replication lag could cause a false cache-miss on the reader pool.
   */
  public Maybe<RcaReportCacheHit> getFromWriterPool(
      String projectId, RcaType type, String entityKey, LocalDate date) {
    return getFromPool(mysqlClient.getWriterPool(), projectId, type, entityKey, date);
  }

  private Maybe<RcaReportCacheHit> getFromPool(
      io.vertx.rxjava3.sqlclient.Pool pool,
      String projectId,
      RcaType type,
      String entityKey,
      LocalDate date) {
    return pool
        .preparedQuery(RcaReportCacheQueries.GET_BY_KEY)
        .rxExecute(Tuple.of(projectId, type.name(), entityKey, java.sql.Date.valueOf(date)))
        .flatMapMaybe(rows -> {
          if (rows.size() == 0) {
            return Maybe.empty();
          }
          Row row = rows.iterator().next();
          String body = row.getString(0);
          if (body == null || body.isBlank()) {
            return Maybe.empty();
          }
          LocalDateTime cachedAtLdt = row.getLocalDateTime(1);
          Instant cachedAt =
              cachedAtLdt != null ? cachedAtLdt.toInstant(ZoneOffset.UTC) : null;
          return Maybe.just(new RcaReportCacheHit(body, cachedAt));
        })
        .doOnError(e -> log.warn("RCA report cache get failed: {}", e.getMessage()));
  }

  /**
   * Stores or updates the report for the given key.
   */
  public Completable put(
      String projectId, RcaType type, String entityKey, LocalDate date, String reportBody) {
    if (reportBody == null || reportBody.isBlank()) {
      return Completable.complete();
    }
    return mysqlClient.getWriterPool()
        .preparedQuery(RcaReportCacheQueries.UPSERT)
        .rxExecute(Tuple.of(projectId, type.name(), entityKey, java.sql.Date.valueOf(date), reportBody))
        .ignoreElement()
        .doOnComplete(
            () ->
                log.debug(
                    "RCA report cached: project={}, type={}, entity={}, date={}",
                    projectId,
                    type,
                    entityKey,
                    date))
        .doOnError(e -> log.warn("RCA report cache put failed: {}", e.getMessage()));
  }
}
