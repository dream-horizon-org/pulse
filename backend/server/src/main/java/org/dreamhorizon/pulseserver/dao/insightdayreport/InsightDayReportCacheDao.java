package org.dreamhorizon.pulseserver.dao.insightdayreport;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.insightjob.InsightType;

/**
 * MySQL cache for per-day AI summaries produced by the {@code /insight/{type}/day} endpoint.
 *
 * <p>A cache hit for a date means the full ClickHouse data-fetch AND the AI day call can both be
 * skipped — only the final merge needs to run.
 */
@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class InsightDayReportCacheDao {

  private final MysqlClient mysqlClient;

  /**
   * Returns a map of date → day_body for whichever dates already have a cached AI summary.
   * Dates with no cache row are absent from the map.
   */
  public Single<Map<LocalDate, String>> getForDates(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final List<LocalDate> dates) {
    if (dates == null || dates.isEmpty()) {
      return Single.just(Map.of());
    }
    String placeholders = String.join(", ", Collections.nCopies(dates.size(), "?"));
    String sql = String.format(InsightDayReportCacheQueries.SELECT_FOR_DATES, placeholders);

    List<Object> params = new ArrayList<>();
    params.add(projectId);
    params.add(insightType.name());
    params.add(entityKey);
    for (LocalDate d : dates) {
      params.add(java.sql.Date.valueOf(d));
    }

    return mysqlClient
        .getReaderPool()
        .preparedQuery(sql)
        .rxExecute(Tuple.wrap(params.toArray()))
        .map(rows -> {
          Map<LocalDate, String> result = new HashMap<>();
          for (Row row : rows) {
            Object dateVal = row.getValue(0);
            String body = row.getString(1);
            if (dateVal == null || body == null || body.isBlank()) {
              continue;
            }
            try {
              LocalDate date = dateVal instanceof java.sql.Date sqlDate
                  ? sqlDate.toLocalDate()
                  : LocalDate.parse(dateVal.toString());
              result.put(date, body);
            } catch (Exception e) {
              log.warn("Failed to parse day cache row date={}: {}", dateVal, e.getMessage());
            }
          }
          return result;
        })
        .doOnError(e ->
            log.warn("InsightDayReportCacheDao.getForDates failed project={}: {}",
                projectId, e.getMessage()))
        .onErrorReturnItem(Map.of());
  }

  /** Upserts a single day's AI summary. Failures are logged but not propagated. */
  public Completable put(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final LocalDate date,
      final String dayBody) {
    if (dayBody == null || dayBody.isBlank() || "{}".equals(dayBody.trim())) {
      return Completable.complete();
    }
    return mysqlClient
        .getWriterPool()
        .preparedQuery(InsightDayReportCacheQueries.UPSERT)
        .rxExecute(Tuple.of(
            projectId,
            insightType.name(),
            entityKey,
            java.sql.Date.valueOf(date),
            dayBody))
        .ignoreElement()
        .doOnComplete(() ->
            log.debug("Day insight cached: project={} type={} entity={} date={}",
                projectId, insightType, entityKey, date))
        .doOnError(e ->
            log.warn("InsightDayReportCacheDao.put failed project={} date={}: {}",
                projectId, date, e.getMessage()));
  }

  /**
   * Bulk upsert of multiple day AI summaries in parallel (fire-and-forget — errors are logged,
   * not propagated, so a single write failure never breaks the job).
   */
  public Completable putAll(
      final String projectId,
      final InsightType insightType,
      final String entityKey,
      final Map<LocalDate, String> dayInsights) {
    if (dayInsights == null || dayInsights.isEmpty()) {
      return Completable.complete();
    }
    List<Completable> writes = dayInsights.entrySet().stream()
        .map(e -> put(projectId, insightType, entityKey, e.getKey(), e.getValue())
            .onErrorComplete())
        .toList();
    return Completable.merge(writes);
  }
}
