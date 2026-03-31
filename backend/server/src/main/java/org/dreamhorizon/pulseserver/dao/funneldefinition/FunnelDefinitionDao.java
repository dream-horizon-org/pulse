package org.dreamhorizon.pulseserver.dao.funneldefinition;

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
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.funneldefinition.models.FunnelDefinitionRow;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class FunnelDefinitionDao {

  private final MysqlClient mysqlClient;

  public Single<Long> insert(FunnelDefinitionRow row) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
        .preparedQuery(FunnelDefinitionQueries.INSERT)
        .rxExecute(
            Tuple.from(
                List.of(
                    row.getProjectId(),
                    row.getName(),
                    row.getDescription(),
                    row.getFunnelType(),
                    row.getStepOrderType(),
                    row.getStepsJson(),
                    row.getWindowSeconds(),
                    row.getMode(),
                    row.getFiltersJson(),
                    row.getDateRangeDays(),
                    localDateTimeOrNull(row.getStartTime()),
                    localDateTimeOrNull(row.getEndTime()),
                    localDateTimeOrNull(row.getExpiry()),
                    row.getCreatedBy())))
        .map(r -> r.property(MySQLClient.LAST_INSERTED_ID));
  }

  public Single<Integer> update(long id, String projectId, FunnelDefinitionRow row) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
        .preparedQuery(FunnelDefinitionQueries.UPDATE)
        .rxExecute(
            Tuple.from(
                List.of(
                    row.getName(),
                    row.getDescription(),
                    row.getFunnelType(),
                    row.getStepOrderType(),
                    row.getStepsJson(),
                    row.getWindowSeconds(),
                    row.getMode(),
                    row.getFiltersJson(),
                    row.getDateRangeDays(),
                    localDateTimeOrNull(row.getStartTime()),
                    localDateTimeOrNull(row.getEndTime()),
                    localDateTimeOrNull(row.getExpiry()),
                    projectId,
                    id)))
        .map(r -> (int) r.rowCount());
  }

  public Single<Integer> delete(String projectId, long id) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
        .preparedQuery(FunnelDefinitionQueries.DELETE)
        .rxExecute(Tuple.of(projectId, id))
        .map(r -> (int) r.rowCount());
  }

  public Maybe<FunnelDefinitionRow> findByProjectAndId(String projectId, long id) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
        .preparedQuery(FunnelDefinitionQueries.SELECT_BY_PROJECT_AND_ID)
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

  public Single<List<FunnelDefinitionRow>> listByProject(String projectId, FunnelDefinitionListParams p) {
    StringBuilder sql =
        new StringBuilder(
            "SELECT funnel.id, funnel.project_id, funnel.name, funnel.description, funnel.funnel_type, "
                + "funnel.step_order_type, funnel.steps_json, funnel.window_seconds, funnel.mode, funnel.filters_json, "
                + "funnel.date_range, funnel.start_time, funnel.end_time, funnel.expiry, "
                + "funnel.created_at, funnel.updated_at, funnel.created_by, ");
    sql.append(FunnelDefinitionQueries.LATEST_FUNNEL_JOB_STATUS).append(" AS latest_job_status ");
    sql.append("FROM funnel WHERE funnel.project_id = ? ");
    List<Object> params = new ArrayList<>();
    params.add(projectId);

    if (p.getStatuses() != null && !p.getStatuses().isEmpty()) {
      sql.append(" AND (").append(FunnelDefinitionQueries.FUNNEL_COMPUTED_STATUS_CASE).append(") IN (");
      sql.append(String.join(",", p.getStatuses().stream().map(s -> "?").toList()));
      sql.append(")");
      params.addAll(p.getStatuses());
    }
    if (p.getFunnelType() != null && !p.getFunnelType().isBlank()) {
      sql.append(" AND funnel.funnel_type = ?");
      params.add(p.getFunnelType());
    }
    if (p.getNameLikePrefix() != null) {
      sql.append(" AND funnel.name LIKE ?");
      params.add(p.getNameLikePrefix());
    }
    if (p.isUseFullTextSearch() && p.getFtsBooleanQuery() != null && !p.getFtsBooleanQuery().isBlank()) {
      sql.append(" AND MATCH(funnel.name) AGAINST (? IN BOOLEAN MODE)");
      params.add(p.getFtsBooleanQuery());
    }
    if (p.getUpdatedAfter() != null) {
      sql.append(" AND funnel.updated_at >= ?");
      params.add(LocalDateTime.ofInstant(p.getUpdatedAfter(), ZoneOffset.UTC));
    }
    if (p.getUpdatedBefore() != null) {
      sql.append(" AND funnel.updated_at <= ?");
      params.add(LocalDateTime.ofInstant(p.getUpdatedBefore(), ZoneOffset.UTC));
    }
    if (p.getCreatedBy() != null && !p.getCreatedBy().isBlank()) {
      sql.append(" AND funnel.created_by = ?");
      params.add(p.getCreatedBy());
    }

    sql.append(" ORDER BY funnel.updated_at DESC LIMIT ? OFFSET ?");
    params.add(p.getLimit());
    params.add(p.getOffset());

    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
        .preparedQuery(sql.toString())
        .rxExecute(Tuple.from(params))
        .map(
            rows -> {
              List<FunnelDefinitionRow> out = new ArrayList<>();
              rows.forEach(row -> out.add(mapRow(row)));
              return out;
            });
  }

  private static FunnelDefinitionRow mapRow(Row row) {
    return FunnelDefinitionRow.builder()
        .id(row.getLong("id"))
        .projectId(row.getString("project_id"))
        .name(row.getString("name"))
        .description(row.getString("description"))
        .funnelType(row.getString("funnel_type"))
        .stepOrderType(row.getString("step_order_type"))
        .stepsJson(row.getString("steps_json"))
        .windowSeconds(row.getLong("window_seconds"))
        .mode(row.getString("mode"))
        .filtersJson(row.getString("filters_json"))
        .dateRangeDays(row.getInteger("date_range"))
        .startTime(toInstant(row, "start_time"))
        .endTime(toInstant(row, "end_time"))
        .expiry(toInstant(row, "expiry"))
        .createdAt(toInstant(row, "created_at"))
        .updatedAt(toInstant(row, "updated_at"))
        .createdBy(row.getString("created_by"))
        .latestJobStatus(row.getString("latest_job_status"))
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
