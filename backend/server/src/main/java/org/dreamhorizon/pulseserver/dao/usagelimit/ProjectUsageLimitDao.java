package org.dreamhorizon.pulseserver.dao.usagelimit;

import static org.dreamhorizon.pulseserver.constant.ProjectUsageLimitRowConstants.NOTIFICATION_CREATED_AT;
import static org.dreamhorizon.pulseserver.constant.ProjectUsageLimitRowConstants.PROJECT_ID;
import static org.dreamhorizon.pulseserver.constant.ProjectUsageLimitRowConstants.PROJECT_NAME;
import static org.dreamhorizon.pulseserver.constant.ProjectUsageLimitRowConstants.NOTIFICATION_ROW_ACTIVE;
import static org.dreamhorizon.pulseserver.constant.ProjectUsageLimitRowConstants.NOTIFICATION_PROJECT_USAGE_LIMIT_ID;
import static org.dreamhorizon.pulseserver.constant.ProjectUsageLimitRowConstants.THRESHOLDS_NOTIFIED;
import static org.dreamhorizon.pulseserver.constant.ProjectUsageLimitRowConstants.ID;
import static org.dreamhorizon.pulseserver.constant.ProjectUsageLimitRowConstants.CREATED_AT;
import static org.dreamhorizon.pulseserver.constant.ProjectUsageLimitRowConstants.UPDATED_AT;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.DEACTIVATE_ACTIVE_NOTIFICATIONS_FOR_PROJECT;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.CHECK_ACTIVE_LIMIT_EXISTS;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_ACTIVE_LIMIT_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_ALL_ACTIVE_LIMITS;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_ALL_LIMITS;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_ALL_LIMITS_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_LIMIT_BY_ID;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_LIMIT_HISTORY_BY_PROJECT_ID;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.GET_NOTIFICATION_FOR_CURRENT_MONTH;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.INSERT_NOTIFICATION;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.INSERT_USAGE_LIMIT;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.SOFT_DELETE_ACTIVE_LIMIT;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.SOFT_DELETE_ACTIVE_LIMITS_FOR_PROJECTS;
import static org.dreamhorizon.pulseserver.dao.usagelimit.ProjectUsageLimitQueries.UPDATE_NOTIFICATION;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
  private final ObjectMapper objectMapper;

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
   * Deactivates all active {@code usage_limit_notifications} rows for this project.
   */
  public Single<ProjectUsageLimit> updateUsageLimits(
      String projectId,
      String newUsageLimitsJson,
      String performedBy,
      String disabledReason) {
    return softDeleteActiveLimit(projectId, performedBy, disabledReason)
        .andThen(deactivateActiveNotificationsForProject(projectId))
        .andThen(createUsageLimit(projectId, newUsageLimitsJson, performedBy));
  }

  private Completable deactivateActiveNotificationsForProject(String projectId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    return pool.preparedQuery(DEACTIVATE_ACTIVE_NOTIFICATIONS_FOR_PROJECT)
        .rxExecute(Tuple.of(projectId))
        .ignoreElement()
        .doOnError(error ->
            log.error("Failed to deactivate usage_limit_notifications for project: {}", projectId, error));
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
    
    // Parse notification JSON
    JsonNode thresholdsNotified = null;
    Object thresholdsValue = row.getValue(THRESHOLDS_NOTIFIED);
    if (thresholdsValue != null) {
      try {
        if (thresholdsValue instanceof io.vertx.core.json.JsonObject) {
          thresholdsNotified = objectMapper.readTree(((io.vertx.core.json.JsonObject) thresholdsValue).encode());
        } else {
          thresholdsNotified = objectMapper.readTree(thresholdsValue.toString());
        }
      } catch (JsonProcessingException e) {
        log.error("Failed to parse {} JSON", THRESHOLDS_NOTIFIED, e);
        thresholdsNotified = objectMapper.createObjectNode();
      }
    }
    
    String projectName = row.getColumnIndex(PROJECT_NAME) >= 0
        ? row.getString(PROJECT_NAME) : null;

    Long notificationProjectUsageLimitId = null;
    if (row.getColumnIndex(NOTIFICATION_PROJECT_USAGE_LIMIT_ID) >= 0) {
      Object nuid = row.getValue(NOTIFICATION_PROJECT_USAGE_LIMIT_ID);
      if (nuid != null) {
        notificationProjectUsageLimitId = ((Number) nuid).longValue();
      }
    }

    Boolean notificationRowActive = null;
    if (row.getColumnIndex(NOTIFICATION_ROW_ACTIVE) >= 0) {
      notificationRowActive = row.getBoolean(NOTIFICATION_ROW_ACTIVE);
    }

    return ProjectUsageLimit.builder()
        .projectUsageLimitId(row.getLong("project_usage_limit_id"))
        .projectId(row.getString("project_id"))
        .projectName(projectName)
        .usageLimits(usageLimitsJson)
        .isActive(row.getBoolean("is_active"))
        .createdAt(row.getLocalDateTime("created_at") != null
            ? row.getLocalDateTime("created_at").toInstant(ZoneOffset.UTC) : null)
        .disabledAt(row.getLocalDateTime("disabled_at") != null
            ? row.getLocalDateTime("disabled_at").toInstant(ZoneOffset.UTC) : null)
        .disabledBy(row.getString("disabled_by"))
        .disabledReason(row.getString("disabled_reason"))
        .createdBy(row.getString("created_by"))
        .thresholdsNotified(thresholdsNotified)
        .notificationProjectUsageLimitId(notificationProjectUsageLimitId)
        .notificationRowActive(notificationRowActive)
        .notificationCreatedAt(row.getLocalDateTime(NOTIFICATION_CREATED_AT) != null
            ? row.getLocalDateTime(NOTIFICATION_CREATED_AT).toInstant(ZoneOffset.UTC)
            : null)
        .tenantId(row.getColumnIndex("tenant_id") >= 0 ? row.getString("tenant_id") : null)
        .build();
  }

  /**
   * Mark thresholds as notified for the current month.
   * Creates a new row if one doesn't exist for this month, otherwise updates existing row.
   *
   * @param projectUsageLimitId active {@code project_usage_limits} row to reference (frozen for the month on insert)
   */
  public Single<NotificationRecord> markThresholdsNotified(
      String projectId, List<Integer> thresholds, long projectUsageLimitId) {
    MySQLPool pool = mysqlClient.getWriterPool();
    Instant now = Instant.now();

    return pool.preparedQuery(GET_NOTIFICATION_FOR_CURRENT_MONTH)
        .rxExecute(Tuple.of(projectId))
        .flatMap(result -> {
          if (result.size() > 0) {
            return updateExistingNotification(pool, result.iterator().next(), thresholds, now);
          } else {
            return createNewNotification(pool, projectId, thresholds, projectUsageLimitId, now);
          }
        })
        .doOnError(error -> log.error("Failed to mark thresholds notified for project: {}", projectId, error));
  }

  private Single<NotificationRecord> updateExistingNotification(
      MySQLPool pool, Row existingRow, List<Integer> thresholds, Instant now) {
    try {
      Long id = existingRow.getLong(ID);
      String projectId = existingRow.getString(PROJECT_ID);
      
      // Get JSON column - MySQL returns it as JsonObject, convert to String
      Object thresholdsObj = existingRow.getValue(THRESHOLDS_NOTIFIED);
      String existingJson = thresholdsObj != null ? thresholdsObj.toString() : "{}";
      
      JsonNode existingNode = objectMapper.readTree(existingJson);
      ObjectNode updatedNode = existingNode.isObject() 
          ? (ObjectNode) existingNode 
          : objectMapper.createObjectNode();
      
          for (Integer threshold : thresholds) {
            String key = String.valueOf(threshold);
            if (!updatedNode.has(key)) {
              updatedNode.put(key, now.toString());
            }
            // Idempotent: skip if already notified (e.g. retry after partial success)
          }
      
      String updatedJson = objectMapper.writeValueAsString(updatedNode);
      
      Boolean rowActive = existingRow.getColumnIndex("is_active") >= 0
          ? existingRow.getBoolean("is_active") : null;

      Long projectUsageLimitId = existingRow.getColumnIndex("project_usage_limit_id") >= 0
          ? existingRow.getLong("project_usage_limit_id") : null;

      return pool.preparedQuery(UPDATE_NOTIFICATION)
          .rxExecute(Tuple.of(updatedJson, id))
          .map(result -> NotificationRecord.builder()
              .id(id)
              .projectId(projectId)
              .projectUsageLimitId(projectUsageLimitId)
              .thresholdsNotified(updatedNode)
              .notificationRowActive(rowActive)
              .createdAt(existingRow.getLocalDateTime(CREATED_AT) != null
                  ? existingRow.getLocalDateTime(CREATED_AT).toInstant(ZoneOffset.UTC) : null)
              .updatedAt(now)
              .build());
    } catch (JsonProcessingException e) {
      return Single.error(new RuntimeException("Failed to parse notification JSON", e));
    }
  }

  private Single<NotificationRecord> createNewNotification(
      MySQLPool pool,
      String projectId,
      List<Integer> thresholds,
      long projectUsageLimitId,
      Instant now) {
    try {
      ObjectNode notificationNode = objectMapper.createObjectNode();
      for (Integer threshold : thresholds) {
        notificationNode.put(String.valueOf(threshold), now.toString());
      }

      String notificationJson = objectMapper.writeValueAsString(notificationNode);

      return pool.preparedQuery(INSERT_NOTIFICATION)
          .rxExecute(Tuple.of(projectId, notificationJson, projectUsageLimitId))
          .flatMap(result -> pool.preparedQuery(GET_NOTIFICATION_FOR_CURRENT_MONTH)
              .rxExecute(Tuple.of(projectId))
              .map(rows -> {
                Row row = rows.iterator().next();
                Boolean active = row.getColumnIndex("is_active") >= 0 ? row.getBoolean("is_active") : true;
                return NotificationRecord.builder()
                    .id(row.getLong(ID))
                    .projectId(projectId)
                    .projectUsageLimitId(projectUsageLimitId)
                    .thresholdsNotified(notificationNode)
                    .notificationRowActive(active)
                    .createdAt(row.getLocalDateTime(CREATED_AT) != null
                        ? row.getLocalDateTime(CREATED_AT).toInstant(ZoneOffset.UTC) : null)
                    .updatedAt(row.getLocalDateTime(UPDATED_AT) != null
                        ? row.getLocalDateTime(UPDATED_AT).toInstant(ZoneOffset.UTC) : null)
                    .build();
              }));
    } catch (JsonProcessingException e) {
      return Single.error(new RuntimeException("Failed to create notification JSON", e));
    }
  }

  @lombok.Data
  @lombok.Builder
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  public static class NotificationRecord {
    private Long id;
    private String projectId;
    /** Referenced {@code project_usage_limits} row (notification month snapshot). */
    private Long projectUsageLimitId;
    private JsonNode thresholdsNotified;
    private Boolean notificationRowActive;
    private Instant createdAt;
    private Instant updatedAt;
  }
}

