package org.dreamhorizon.pulseserver.dao.productAnalysis.revenueevent;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.productAnalysis.revenueevent.models.RevenueEventRow;

@Singleton
@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class RevenueEventDao {

  private final MysqlClient mysqlClient;

  public Completable insert(RevenueEventRow row) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
      .preparedQuery(RevenueEventQueries.INSERT)
      .rxExecute(
        Tuple.from(
          Arrays.asList(
            row.getId(),
            row.getProjectId(),
            row.getEventName(),
            row.getValueAttribute(),
            row.getCurrency(),
            row.getCurrencyAttribute(),
            row.getConversionWindowHours(),
            row.getConfiguredBy())))
      .ignoreElement();
  }

  public Single<Integer> update(String projectId, String id, RevenueEventRow row) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
      .preparedQuery(RevenueEventQueries.UPDATE)
      .rxExecute(
        Tuple.from(
          Arrays.asList(
            row.getEventName(),
            row.getValueAttribute(),
            row.getCurrency(),
            row.getCurrencyAttribute(),
            row.getConversionWindowHours(),
            row.getConfiguredBy(),
            projectId,
            id)))
      .map(r -> (int) r.rowCount());
  }

  public Single<Integer> delete(String projectId, String id) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
      .preparedQuery(RevenueEventQueries.DELETE)
      .rxExecute(Tuple.of(projectId, id))
      .map(r -> (int) r.rowCount());
  }

  public Single<List<RevenueEventRow>> listByProject(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
      .preparedQuery(RevenueEventQueries.SELECT_BY_PROJECT)
      .rxExecute(Tuple.of(projectId))
      .map(
        rows -> {
          List<RevenueEventRow> out = new ArrayList<>();
          rows.forEach(row -> out.add(mapRow(row)));
          return out;
        });
  }

  public Maybe<RevenueEventRow> findByProjectAndId(String projectId, String id) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
      .preparedQuery(RevenueEventQueries.SELECT_BY_PROJECT_AND_ID)
      .rxExecute(Tuple.of(projectId, id))
      .flatMapMaybe(
        rows -> {
          var it = rows.iterator();
          if (!it.hasNext()) {
            return Maybe.empty();
          }
          return Maybe.just(mapRow(it.next()));
        });
  }

  public Maybe<RevenueEventRow> findByProjectAndEventName(String projectId, String eventName) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
      .preparedQuery(RevenueEventQueries.SELECT_BY_PROJECT_AND_EVENT_NAME)
      .rxExecute(Tuple.of(projectId, eventName))
      .flatMapMaybe(
        rows -> {
          var it = rows.iterator();
          if (!it.hasNext()) {
            return Maybe.empty();
          }
          return Maybe.just(mapRow(it.next()));
        });
  }

  private static RevenueEventRow mapRow(Row row) {
    return RevenueEventRow.builder()
      .id(row.getString("id"))
      .projectId(row.getString("project_id"))
      .eventName(row.getString("event_name"))
      .valueAttribute(row.getString("value_attribute"))
      .currency(row.getString("currency"))
      .currencyAttribute(row.getString("currency_attribute"))
      .conversionWindowHours(row.getInteger("conversion_window_hours"))
      .configuredBy(row.getString("configured_by"))
      .configuredAt(toInstant(row, "configured_at"))
      .updatedAt(toInstant(row, "updated_at"))
      .build();
  }

  private static Instant toInstant(Row row, String column) {
    LocalDateTime ldt = row.getLocalDateTime(column);
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }
}
