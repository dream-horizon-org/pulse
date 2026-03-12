package org.dreamhorizon.pulseserver.dao.usagelimit;

import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.CHECK_ACTIVE_LIMIT_EXISTS;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_ACTIVE_LIMIT_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_ALL_ACTIVE_LIMITS;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_ALL_LIMITS;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_ALL_LIMITS_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_LIMIT_BY_ID;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_LIMIT_HISTORY_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.INSERT_USAGE_LIMIT;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.SOFT_DELETE_ACTIVE_LIMIT;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.SOFT_DELETE_ACTIVE_LIMITS_FOR_PROJECTS;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.usagelimit.models.ProjectUsageLimit;

@Slf4j
@Singleton
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ProjectUsageLimitDao {
  private final MysqlClient mysqlClient;

  public Single<ProjectUsageLimit> createUsageLimit(String projectId, String usageLimitsJson, String createdBy) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(INSERT_USAGE_LIMIT)
        .rxExecute(Tuple.of(projectId, usageLimitsJson, createdBy))
        .map(result -> mapToCreatedUsageLimit(result, projectId, usageLimitsJson, createdBy))
        .doOnError(error -> log.error("Failed to create usage limit for project: {}", projectId, error));
  }

  public Single<ProjectUsageLimit> createUsageLimit(SqlConnection conn, String projectId, String usageLimitsJson, String createdBy) {
    return conn.preparedQuery(INSERT_USAGE_LIMIT)
        .rxExecute(Tuple.of(projectId, usageLimitsJson, createdBy))
        .map(result -> mapToCreatedUsageLimit(result, projectId, usageLimitsJson, createdBy))
        .doOnError(error -> log.error("Failed to create usage limit for project: {}", projectId, error));
  }

  private ProjectUsageLimit mapToCreatedUsageLimit(
      io.vertx.rxjava3.sqlclient.RowSet<Row> result,
      String projectId,
      String usageLimitsJson,
      String createdBy) {
    long generatedId = Long.parseLong(result.property(io.vertx.rxjava3.mysqlclient.MySQLClient.LAST_INSERTED_ID).toString());
    log.info("Created usage limit {} for project: {}", generatedId, projectId);
    return ProjectUsageLimit.builder()
        .projectUsageLimitId(generatedId)
        .projectId(projectId)
        .usageLimits(usageLimitsJson)
        .isActive(true)
        .createdBy(createdBy)
        .createdAt(Instant.now())
        .build();
  }

  public Maybe<ProjectUsageLimit> getActiveLimitByProjectId(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_ACTIVE_LIMIT_BY_PROJECT_ID)
        .rxExecute(Tuple.of(projectId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToUsageLimit(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch active limit for project: {}", projectId, error));
  }

  public Maybe<ProjectUsageLimit> getLimitById(long limitId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_LIMIT_BY_ID)
        .rxExecute(Tuple.of(limitId))
        .flatMapMaybe(rowSet -> {
          if (rowSet.size() == 0) {
            return Maybe.empty();
          }
          return Maybe.just(mapRowToUsageLimit(rowSet.iterator().next()));
        })
        .doOnError(error -> log.error("Failed to fetch limit by id: {}", limitId, error));
  }

  public Flowable<ProjectUsageLimit> getAllLimitsByProjectId(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_ALL_LIMITS_BY_PROJECT_ID)
        .rxExecute(Tuple.of(projectId))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToUsageLimit((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all limits for project: {}", projectId, error));
  }

  public Flowable<ProjectUsageLimit> getLimitHistoryByProjectId(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(GET_LIMIT_HISTORY_BY_PROJECT_ID)
        .rxExecute(Tuple.of(projectId))
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToUsageLimit((Row) row)))
        .doOnError(error -> log.error("Failed to fetch limit history for project: {}", projectId, error));
  }

  public Completable softDeleteActiveLimit(String projectId, String disabledBy, String disabledReason) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(SOFT_DELETE_ACTIVE_LIMIT)
        .rxExecute(Tuple.of(disabledBy, disabledReason, projectId))
        .flatMapCompletable(result -> {
          if (result.rowCount() == 0) {
            log.warn("No active limit found to soft-delete for project: {}", projectId);
          } else {
            log.info("Soft-deleted active limit for project: {} reason: {}", projectId, disabledReason);
          }
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to soft-delete active limit for project: {}", projectId, error));
  }

  public Completable softDeleteActiveLimitsForProjects(List<String> projectIds, String disabledBy, String disabledReason) {
    if (projectIds == null || projectIds.isEmpty()) {
      return Completable.complete();
    }

    MySQLPool pool = mysqlClient.getWriterPool();
    String placeholders = projectIds.stream().map(id -> "?").collect(Collectors.joining(", "));
    String query = String.format(SOFT_DELETE_ACTIVE_LIMITS_FOR_PROJECTS, placeholders);

    // Build tuple with disabledBy, disabledReason, then all projectIds
    Object[] params = new Object[2 + projectIds.size()];
    params[0] = disabledBy;
    params[1] = disabledReason;
    for (int i = 0; i < projectIds.size(); i++) {
      params[2 + i] = projectIds.get(i);
    }

    return pool.preparedQuery(query)
        .rxExecute(Tuple.from(params))
        .flatMapCompletable(result -> {
          log.info("Soft-deleted {} active limits for {} projects, reason: {}",
              result.rowCount(), projectIds.size(), disabledReason);
          return Completable.complete();
        })
        .doOnError(error -> log.error("Failed to soft-delete active limits for projects: {}", projectIds, error));
  }

  public Single<Boolean> hasActiveLimit(String projectId) {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.preparedQuery(CHECK_ACTIVE_LIMIT_EXISTS)
        .rxExecute(Tuple.of(projectId))
        .map(rowSet -> {
          Row row = rowSet.iterator().next();
          return row.getLong("count") > 0;
        })
        .doOnError(error -> log.error("Failed to check active limit existence for project: {}", projectId, error));
  }

  public Flowable<ProjectUsageLimit> getAllActiveLimits() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.query(GET_ALL_ACTIVE_LIMITS)
        .rxExecute()
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToUsageLimit((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all active limits", error));
  }

  public Flowable<ProjectUsageLimit> getAllLimits() {
    MySQLPool pool = mysqlClient.getReaderPool();
    return pool.query(GET_ALL_LIMITS)
        .rxExecute()
        .toFlowable()
        .flatMap(rowSet -> Flowable.fromIterable(rowSet).map(row -> mapRowToUsageLimit((Row) row)))
        .doOnError(error -> log.error("Failed to fetch all limits", error));
  }

  /**
   * Updates usage limits for a project by soft-deleting the current active record
   * and creating a new one with the updated limits.
   */
  public Single<ProjectUsageLimit> updateUsageLimits(
      String projectId,
      String newUsageLimitsJson,
      String performedBy,
      String disabledReason) {
    return softDeleteActiveLimit(projectId, performedBy, disabledReason)
        .andThen(createUsageLimit(projectId, newUsageLimitsJson, performedBy));
  }

  private ProjectUsageLimit mapRowToUsageLimit(Row row) {
    // MySQL JSON column returns JsonObject, convert to String for the model
    String usageLimitsJson = null;
    Object usageLimitsValue = row.getValue("usage_limits");
    if (usageLimitsValue != null) {
      if (usageLimitsValue instanceof io.vertx.core.json.JsonObject) {
        usageLimitsJson = ((io.vertx.core.json.JsonObject) usageLimitsValue).encode();
      } else {
        usageLimitsJson = usageLimitsValue.toString();
      }
    }
    
    return ProjectUsageLimit.builder()
        .projectUsageLimitId(row.getLong("project_usage_limit_id"))
        .projectId(row.getString("project_id"))
        .usageLimits(usageLimitsJson)
        .isActive(row.getBoolean("is_active"))
        .createdAt(row.getLocalDateTime("created_at") != null
            ? row.getLocalDateTime("created_at").toInstant(ZoneOffset.UTC) : null)
        .disabledAt(row.getLocalDateTime("disabled_at") != null
            ? row.getLocalDateTime("disabled_at").toInstant(ZoneOffset.UTC) : null)
        .disabledBy(row.getString("disabled_by"))
        .disabledReason(row.getString("disabled_reason"))
        .createdBy(row.getString("created_by"))
        .build();
  }
}

