package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.productAnalysis.funneldefinition.models.FunnelDefinitionRow;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
          Arrays.asList(
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
          Arrays.asList(
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

  /**
   * Stops auto-refresh for an AUTO funnel by flipping it to ONCE and freezing the analysis
   * window. Returns the number of rows updated — {@code 0} when the funnel is already
   * stopped (ONCE), missing, or owned by another project. Combined with the existing
   * {@code FUNNEL_COMPUTED_STATUS_CASE}, the listing's status reads as COMPLETED.
   */
  public Single<Integer> stopAuto(String projectId, long id) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool
      .preparedQuery(FunnelDefinitionQueries.STOP_AUTO)
      .rxExecute(Tuple.of(projectId, id))
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

  public Maybe<FunnelDefinitionRow> findById(long id) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
      .preparedQuery(FunnelDefinitionQueries.SELECT_BY_ID)
      .rxExecute(Tuple.of(id))
      .flatMapMaybe(
        rows -> {
          var it = rows.iterator();
          if (!it.hasNext()) {
            return Maybe.empty();
          }
          return Maybe.just(mapRow(it.next()));
        });
  }

  public Single<List<FunnelDefinitionRow>> listAllAuto() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
      .preparedQuery(FunnelDefinitionQueries.SELECT_ALL_AUTO)
      .rxExecute()
      .map(rows -> {
        List<FunnelDefinitionRow> out = new ArrayList<>();
        rows.forEach(row -> out.add(mapRow(row)));
        return out;
      });
  }

  public Single<List<FunnelDefinitionRow>> listByProject(String projectId, FunnelDefinitionListParams funnelDefinitionListParams) {
    StringBuilder sql =
      new StringBuilder(
        "SELECT funnel.id, funnel.project_id, funnel.name, funnel.description, funnel.funnel_type, "
          + "funnel.step_order_type, funnel.steps_json, funnel.window_seconds, funnel.mode, funnel.filters_json, "
          + "funnel.date_range, funnel.start_time, funnel.end_time, funnel.expiry, "
          + "funnel.created_at, funnel.updated_at, funnel.created_by, ");
    sql.append(FunnelDefinitionQueries.LATEST_FUNNEL_JOB_STATUS).append(" AS latest_job_status, ");
    sql.append("COUNT(*) OVER() AS total_count ");
    sql.append("FROM funnel WHERE funnel.project_id = ? ");
    List<Object> params = new ArrayList<>();
    params.add(projectId);

    if (funnelDefinitionListParams.getStatuses() != null && !funnelDefinitionListParams.getStatuses().isEmpty()) {
      sql.append(" AND (").append(FunnelDefinitionQueries.FUNNEL_COMPUTED_STATUS_CASE).append(") IN (");
      sql.append(String.join(",", funnelDefinitionListParams.getStatuses().stream().map(s -> "?").toList()));
      sql.append(")");
      params.addAll(funnelDefinitionListParams.getStatuses());
    }
    if (funnelDefinitionListParams.getFunnelType() != null && !funnelDefinitionListParams.getFunnelType().isBlank()) {
      sql.append(" AND funnel.funnel_type = ?");
      params.add(funnelDefinitionListParams.getFunnelType());
    }
    if (funnelDefinitionListParams.getNameLikePrefix() != null) {
      sql.append(" AND funnel.name LIKE ?");
      params.add(funnelDefinitionListParams.getNameLikePrefix());
    }
    if (funnelDefinitionListParams.isUseFullTextSearch() && funnelDefinitionListParams.getFtsBooleanQuery() != null && !funnelDefinitionListParams.getFtsBooleanQuery().isBlank()) {
      sql.append(" AND MATCH(funnel.name) AGAINST (? IN BOOLEAN MODE)");
      params.add(funnelDefinitionListParams.getFtsBooleanQuery());
    }
    if (funnelDefinitionListParams.getUpdatedAfter() != null) {
      sql.append(" AND funnel.updated_at >= ?");
      params.add(LocalDateTime.ofInstant(funnelDefinitionListParams.getUpdatedAfter(), ZoneOffset.UTC));
    }
    if (funnelDefinitionListParams.getUpdatedBefore() != null) {
      sql.append(" AND funnel.updated_at <= ?");
      params.add(LocalDateTime.ofInstant(funnelDefinitionListParams.getUpdatedBefore(), ZoneOffset.UTC));
    }
    if (funnelDefinitionListParams.getCreatedBy() != null && !funnelDefinitionListParams.getCreatedBy().isBlank()) {
      sql.append(" AND funnel.created_by = ?");
      params.add(funnelDefinitionListParams.getCreatedBy());
    }

    sql.append(" ORDER BY funnel.updated_at DESC LIMIT ? OFFSET ?");
    params.add(funnelDefinitionListParams.getLimit());
    params.add(funnelDefinitionListParams.getOffset());

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

  /** Distinct non-null created_by values for all funnels in the project. */
  public Single<List<String>> listDistinctCreatedBy(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool
      .preparedQuery(FunnelDefinitionQueries.SELECT_DISTINCT_CREATED_BY)
      .rxExecute(Tuple.of(projectId))
      .map(rows -> {
        List<String> out = new ArrayList<>();
        rows.forEach(row -> {
          String v = row.getString("created_by");
          if (v != null) out.add(v);
        });
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
      .stepsJson(row.getValue("steps_json") != null ? row.getValue("steps_json").toString() : null)
      .windowSeconds(row.getLong("window_seconds"))
      .mode(row.getString("mode"))
      .filtersJson(row.getValue("filters_json") != null ? row.getValue("filters_json").toString() : null)
      .dateRangeDays(row.getInteger("date_range"))
      .startTime(toInstant(row, "start_time"))
      .endTime(toInstant(row, "end_time"))
      .expiry(toInstant(row, "expiry"))
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
