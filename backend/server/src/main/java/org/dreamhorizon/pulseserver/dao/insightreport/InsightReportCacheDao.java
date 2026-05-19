package org.dreamhorizon.pulseserver.dao.insightreport;

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
import org.dreamhorizon.pulseserver.dao.insightjob.InsightExecutionMode;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;
import org.dreamhorizon.pulseserver.dao.insightreport.models.InsightReportCacheHit;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InsightReportCacheDao {

  private final MysqlClient mysqlClient;

  public Maybe<InsightReportCacheHit> get(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final InsightExecutionMode executionMode,
      final LocalDate startDate,
      final LocalDate endDate) {
    return getFromPool(
        mysqlClient.getReaderPool(), projectId, insightType, entityKey, executionMode,
        startDate, endDate);
  }

  public Maybe<InsightReportCacheHit> getFromWriterPool(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final InsightExecutionMode executionMode,
      final LocalDate startDate,
      final LocalDate endDate) {
    return getFromPool(
        mysqlClient.getWriterPool(), projectId, insightType, entityKey, executionMode,
        startDate, endDate);
  }

  private Maybe<InsightReportCacheHit> getFromPool(
      final io.vertx.rxjava3.sqlclient.Pool pool,
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final InsightExecutionMode executionMode,
      final LocalDate startDate,
      final LocalDate endDate) {
    return pool
        .preparedQuery(InsightReportCacheQueries.GET_BY_KEY)
        .rxExecute(
            Tuple.wrap(
                new Object[] {
                  projectId,
                  insightType.name(),
                  entityKey,
                  executionMode.name(),
                  startDate != null ? java.sql.Date.valueOf(startDate) : null,
                  endDate != null ? java.sql.Date.valueOf(endDate) : null
                }))
        .flatMapMaybe(
            rows -> {
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
              return Maybe.just(new InsightReportCacheHit(body, cachedAt));
            })
        .doOnError(e -> log.warn("Insight report cache get failed: {}", e.getMessage()));
  }

  public Completable put(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final InsightExecutionMode executionMode,
      final LocalDate startDate,
      final LocalDate endDate,
      final String reportBody) {
    if (reportBody == null || reportBody.isBlank()) {
      return Completable.complete();
    }
    return mysqlClient
        .getWriterPool()
        .preparedQuery(InsightReportCacheQueries.UPSERT)
        .rxExecute(
            Tuple.wrap(
                new Object[] {
                  projectId,
                  insightType.name(),
                  entityKey,
                  executionMode.name(),
                  startDate != null ? java.sql.Date.valueOf(startDate) : null,
                  endDate != null ? java.sql.Date.valueOf(endDate) : null,
                  reportBody
                }))
        .ignoreElement()
        .doOnComplete(
            () ->
                log.debug(
                    "Insight report cached: project={} type={} entity={} mode={} start={} end={}",
                    projectId, insightType, entityKey, executionMode, startDate, endDate))
        .doOnError(e -> log.warn("Insight report cache put failed: {}", e.getMessage()));
  }
}
