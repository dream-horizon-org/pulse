package org.dreamhorizon.pulseserver.dao.productAnalysis.journey;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.productAnalysis.journey.models.JourneyRow;

@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class JourneyDao {

  private final MysqlClient mysqlClient;

  public Single<Long> insert(JourneyRow row) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
      .preparedQuery(JourneyQueries.INSERT)
      .rxExecute(
        Tuple.from(
          List.of(
            row.getProjectId(),
            row.getName(),
            row.getDescription(),
            row.getAnchorEvent(),
            row.getDirection(),
            row.getDepth(),
            row.getMode(),
            row.getFiltersJson(),
            localDateTimeOrNull(row.getStartTime()),
            localDateTimeOrNull(row.getEndTime()),
            row.getJourneyType(),
            localDateTimeOrNull(row.getExpiry()),
            row.getDateRangeDays(),
            row.getCreatedBy())))
      .map(r -> r.property(MySQLClient.LAST_INSERTED_ID));
  }

  public Single<Integer> update(long id, String projectId, JourneyRow row) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
      .preparedQuery(JourneyQueries.UPDATE)
      .rxExecute(
        Tuple.from(
          List.of(
            row.getName(),
            row.getDescription(),
            row.getAnchorEvent(),
            row.getDirection(),
            row.getDepth(),
            row.getMode(),
            row.getFiltersJson(),
            localDateTimeOrNull(row.getStartTime()),
            localDateTimeOrNull(row.getEndTime()),
            row.getJourneyType(),
            localDateTimeOrNull(row.getExpiry()),
            row.getDateRangeDays(),
            projectId,
            id)))
      .map(r -> (int) r.rowCount());
  }

  public Single<Integer> delete(String projectId, long id) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
      .preparedQuery(JourneyQueries.DELETE)
      .rxExecute(Tuple.of(projectId, id))
      .map(r -> (int) r.rowCount());
  }

  public Maybe<JourneyRow> findByProjectAndId(String projectId, long id) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
      .preparedQuery(JourneyQueries.SELECT_BY_PROJECT_AND_ID)
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

  public Single<List<JourneyRow>> listByProject(String projectId, JourneyListParams p) {
    StringBuilder sql =
      new StringBuilder(
        "SELECT journey.id, journey.project_id, journey.name, journey.description, journey.anchor_event, "
          + "journey.direction, journey.depth, journey.mode, journey.filters_json, journey.start_time, "
          + "journey.end_time, journey.journey_type, journey.expiry, journey.date_range, "
          + "journey.created_at, journey.updated_at, journey.created_by, ");
    sql.append(JourneyQueries.LATEST_JOURNEY_JOB_STATUS).append(" AS latest_job_status, ");
    sql.append("COUNT(*) OVER() AS total_count ");
    sql.append("FROM journey WHERE journey.project_id = ? ");
    List<Object> params = new ArrayList<>();
    params.add(projectId);

    if (p.getStatuses() != null && !p.getStatuses().isEmpty()) {
      sql.append(" AND (").append(JourneyQueries.JOURNEY_COMPUTED_STATUS_CASE).append(") IN (");
      sql.append(String.join(",", p.getStatuses().stream().map(s -> "?").toList()));
      sql.append(")");
      params.addAll(p.getStatuses());
    }
    if (p.getJourneyType() != null && !p.getJourneyType().isBlank()) {
      sql.append(" AND journey.journey_type = ?");
      params.add(p.getJourneyType());
    }
    if (p.getNameLikePrefix() != null) {
      sql.append(" AND journey.name LIKE ?");
      params.add(p.getNameLikePrefix());
    }
    if (p.isUseFullTextSearch() && p.getFtsBooleanQuery() != null && !p.getFtsBooleanQuery().isBlank()) {
      sql.append(" AND MATCH(journey.name) AGAINST (? IN BOOLEAN MODE)");
      params.add(p.getFtsBooleanQuery());
    }
    if (p.getUpdatedAfter() != null) {
      sql.append(" AND journey.updated_at >= ?");
      params.add(LocalDateTime.ofInstant(p.getUpdatedAfter(), ZoneOffset.UTC));
    }
    if (p.getUpdatedBefore() != null) {
      sql.append(" AND journey.updated_at <= ?");
      params.add(LocalDateTime.ofInstant(p.getUpdatedBefore(), ZoneOffset.UTC));
    }
    if (p.getCreatedBy() != null && !p.getCreatedBy().isBlank()) {
      sql.append(" AND journey.created_by = ?");
      params.add(p.getCreatedBy());
    }

    sql.append(" ORDER BY journey.updated_at DESC LIMIT ? OFFSET ?");
    params.add(p.getLimit());
    params.add(p.getOffset());

    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
      .preparedQuery(sql.toString())
      .rxExecute(Tuple.from(params))
      .map(
        rows -> {
          List<JourneyRow> out = new ArrayList<>();
          rows.forEach(row -> out.add(mapRow(row)));
          return out;
        });
  }

  private static JourneyRow mapRow(Row row) {
    return JourneyRow.builder()
      .id(row.getLong("id"))
      .projectId(row.getString("project_id"))
      .name(row.getString("name"))
      .description(row.getString("description"))
      .anchorEvent(row.getString("anchor_event"))
      .direction(row.getString("direction"))
      .depth(row.getInteger("depth"))
      .mode(row.getString("mode"))
      .filtersJson(row.getString("filters_json"))
      .startTime(toInstant(row, "start_time"))
      .endTime(toInstant(row, "end_time"))
      .journeyType(row.getString("journey_type"))
      .expiry(toInstant(row, "expiry"))
      .dateRangeDays(row.getInteger("date_range"))
      .createdAt(toInstant(row, "created_at"))
      .updatedAt(toInstant(row, "updated_at"))
      .createdBy(row.getString("created_by"))
      .latestJobStatus(row.getString("latest_job_status"))
      .totalCount(row.getColumnIndex("total_count") >= 0 ? row.getLong("total_count") : 0)
      .build();
  }

  private static Instant toInstant(Row row, String column) {
    LocalDateTime ldt = row.getLocalDateTime(column);
    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
  }

  private static LocalDateTime localDateTimeOrNull(Instant instant) {
    return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
  }
}
