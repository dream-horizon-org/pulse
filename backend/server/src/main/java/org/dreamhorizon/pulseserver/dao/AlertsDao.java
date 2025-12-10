package org.dreamhorizon.pulseserver.dao;

import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.query.AlertsQuery;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertEvaluationHistoryResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertFiltersResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertNotificationChannelResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertSeverityResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertTagsResponseDto;
import org.dreamhorizon.pulseserver.dto.v2.response.EmptyResponse;
import org.dreamhorizon.pulseserver.enums.AlertState;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertConditionDto;
import org.dreamhorizon.pulseserver.service.alert.core.models.*;
import org.dreamhorizon.pulseserver.util.AlertMapper;
import org.dreamhorizon.pulseserver.util.DateTimeUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.mysqlclient.MySQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Pool;
import io.vertx.rxjava3.sqlclient.Row;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AlertsDao {
  private final MysqlClient d11MysqlClient;
  private final DateTimeUtil dateTimeUtil;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static void logAlertNotFound(Integer alert_id) {
    log.error("Alert not found for alert id: {}", alert_id);
  }

  private static @NotNull List<Object> getParameters(String name, String scope, Integer limit, Integer offset,
                                                     String createdBy, String updatedBy) {
    // Create a list to hold query parameters
    List<Object> parameters = new ArrayList<>();

    // Add parameters dynamically
    parameters.add(name == null ? "" : name);
    parameters.add(name == null ? "" : name);
    parameters.add(scope == null ? "" : scope);
    parameters.add(scope == null ? "" : scope);
    parameters.add(createdBy == null ? "" : createdBy);
    parameters.add(createdBy == null ? "" : createdBy);
    parameters.add(updatedBy == null ? "" : updatedBy);
    parameters.add(updatedBy == null ? "" : updatedBy);
    parameters.add(limit == null ? 10 : limit);
    parameters.add(offset == null ? 0 : offset);
    return parameters;
  }

  private static void logError(@NotNull Integer alertId, Throwable error) {
    log.error("Error while updating last evaluated at in db for alert id {}: {}", alertId, error.getMessage());
  }

  private Map<String, Object> mapRowToScopeMap(Row row) {
    Map<String, Object> scope = new HashMap<>();
    scope.put("id", row.getInteger("id"));
    scope.put("name", row.getString("name"));
    scope.put("conditions", ((JsonArray) row.getValue("conditions")).encode());
    scope.put("state", row.getString("state"));
    scope.put("created_at", row.getLocalDateTime("created_at"));
    scope.put("updated_at", row.getLocalDateTime("updated_at"));
    return scope;
  }

  private Single<List<Map<String, Object>>> fetchAlertScopes(Pool pool, Integer alertId) {
    return pool.preparedQuery(AlertsQuery.GET_ALERT_SCOPES)
        .rxExecute(Tuple.of(alertId))
        .map(rowSet -> {
          List<Map<String, Object>> scopes = new ArrayList<>();
          for (Row row : rowSet) {
            scopes.add(mapRowToScopeMap(row));
          }
          return scopes;
        })
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alert scopes from db for alert id {}: {}", alertId, error.getMessage());
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(error.getMessage()));
        });
  }


  private Alert enrichAlertWithConditions(Alert alert, List<Map<String, Object>> scopes) {
    try {
      List<AlertConditionDto> alertConditions = parseAlertConditionsFromScopes(scopes);
      return alert.toBuilder()
          .alerts(alertConditions)
          .build();
    } catch (Exception e) {
      log.error("Error parsing alert conditions for alert {}: {}", alert.getAlertId(), e.getMessage());
      return alert;
    }
  }

  public Single<Integer> createAlert(@NotNull @Valid CreateAlertRequest req) {
    // Extract scope names (identifiers) from threshold map keys
    Set<String> scopeNames = new HashSet<>();
    for (AlertCondition alert : req.getAlerts()) {
      if (alert.getThreshold() != null) {
        scopeNames.addAll(alert.getThreshold().keySet());
      }
    }

    if (scopeNames.isEmpty()) {
      return Single.error(new RuntimeException("No scope names found in threshold maps"));
    }

    List<Object> params = Arrays.asList(
        req.getName(),
        req.getDescription(),
        req.getScope().name(),
        req.getDimensionFilters(),
        req.getConditionExpression(),
        req.getSeverity(),
        req.getNotificationChannelId(),
        req.getEvaluationPeriod(),
        req.getEvaluationInterval(),
        req.getCreatedBy()
    );

    return d11MysqlClient.getWriterPool()
        .rxGetConnection()
        .flatMap((SqlConnection conn) -> conn.rxBegin()
            .flatMap(tx -> conn.preparedQuery(AlertsQuery.CREATE_ALERT)
                .rxExecute(Tuple.tuple(params))
                .flatMap(rowSet -> {
                  if (rowSet.rowCount() == 0) {
                    return Single.error(ServiceError.DATABASE_ERROR.getCustomException("Error while inserting alert in db"));
                  }

                  Integer alertId = Integer.parseInt(
                      Objects.requireNonNull(rowSet.property(MySQLClient.LAST_INSERTED_ID)).toString());

                  return createAlertScopes(conn, alertId, new ArrayList<>(scopeNames), req.getAlerts())
                      .map(success -> alertId);
                })
                .flatMap(alertId -> tx.rxCommit()
                    .toSingleDefault(alertId))
                .onErrorResumeNext(error -> tx.rxRollback()
                    .onErrorComplete()
                    .andThen(Single.error(error))))
            .doFinally(conn::close))
        .onErrorResumeNext(error -> {
          log.error("Error while creating alert (transaction rolled back): {}", error.getMessage());
          if (error instanceof MySQLException) {
            return Single.error(ServiceError.DATABASE_ERROR.getCustomException(error.getMessage()));
          }
          return Single.error(error);
        });
  }

  private Single<Boolean> createAlertScopes(SqlConnection conn, Integer alertId,
                                            List<String> scopeNames, List<AlertCondition> alerts) {
    List<Single<Boolean>> scopeCreationOps = scopeNames.stream()
        .map(scopeName -> {
          try {
            String scopeConditionsJson = buildScopeSpecificConditions(scopeName, alerts);
            return createSingleAlertScopeInTransaction(conn, alertId, scopeName, scopeConditionsJson);
          } catch (JsonProcessingException e) {
            log.error("Error serializing conditions for scope {}: {}", scopeName, e.getMessage());
            return Single.<Boolean>error(new RuntimeException("Failed to serialize conditions: " + e.getMessage()));
          }
        })
        .collect(Collectors.toList());

    return Single.zip(scopeCreationOps, results -> true);
  }

  private String buildScopeSpecificConditions(String identifier, List<AlertCondition> alertConditions)
      throws JsonProcessingException {
    List<Map<String, Object>> scopeSpecificConditions = new ArrayList<>();

    for (AlertCondition condition : alertConditions) {
      Map<String, Object> conditionMap = new HashMap<>();
      conditionMap.put("alias", condition.getAlias());
      conditionMap.put("metric", condition.getMetric().name());
      conditionMap.put("metricOperator", condition.getMetricOperator().name());
      conditionMap.put("threshold", condition.getThreshold().get(identifier));
      scopeSpecificConditions.add(conditionMap);
    }

    return objectMapper.writeValueAsString(scopeSpecificConditions);
  }

  private Single<Boolean> createSingleAlertScopeInTransaction(SqlConnection conn,
                                                              Integer alertId,
                                                              String scopeName,
                                                              String conditionsJson) {
    List<Object> scopeParams = Arrays.asList(
        alertId,
        scopeName,
        conditionsJson,
        AlertState.NORMAL.name()
    );

    return conn.preparedQuery(AlertsQuery.CREATE_ALERT_SCOPE)
        .rxExecute(Tuple.tuple(scopeParams))
        .map(rs -> {
          if (rs.rowCount() == 0) {
            throw ServiceError.DATABASE_ERROR.getCustomException(
                "Failed to insert alert scope: " + scopeName);
          }
          log.info("Alert scope created: alert_id={}, scope={}", alertId, scopeName);
          return true;
        })
        .onErrorResumeNext(error -> {
          log.error("Error inserting alert scope (alert_id={}, scope={}): {}",
              alertId, scopeName, error.getMessage());
          return Single.error(error);
        });
  }


  public Single<EmptyResponse> snoozeAlert(@NotNull @Valid SnoozeAlertRequest snoozeAlertRequest) {
    Tuple tuple = Tuple.tuple();
    tuple.addLocalDateTime(dateTimeUtil.getLocalDateTime(ZoneOffset.UTC));
    tuple.addLocalDateTime(snoozeAlertRequest.getSnoozeFrom());
    tuple.addLocalDateTime(snoozeAlertRequest.getSnoozeUntil());
    tuple.addString(snoozeAlertRequest.getUpdatedBy());
    tuple.addInteger(snoozeAlertRequest.getAlertId());

    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.SNOOZE_ALERT).rxExecute(tuple)
        .onErrorResumeNext(error -> {
          log.error("Error while snoozing alert in db with id {} ", snoozeAlertRequest.getAlertId(), error);
          MySQLException mySqlException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        }).map(rowSet -> {
          if (rowSet.rowCount() == 0) {
            log.error("Error while snoozing alert in db with id: {}", snoozeAlertRequest.getAlertId());
            throw ServiceError.DATABASE_ERROR.getCustomException("Error while updating alert in db");
          }

          return EmptyResponse.emptyResponse;
        });
  }

  public Single<EmptyResponse> deleteSnooze(@NotNull @Valid DeleteSnoozeRequest deleteSnoozeRequest) {
    Tuple tuple = Tuple.tuple();
    tuple.addString(deleteSnoozeRequest.getUpdatedBy());
    tuple.addInteger(deleteSnoozeRequest.getAlertId());

    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.DELETE_SNOOZE).rxExecute(tuple)
        .onErrorResumeNext(error -> {
          log.error("Error while deleting snooze from alert in db with id {} ", deleteSnoozeRequest.getAlertId(), error);
          MySQLException mySqlException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        }).map(rowSet -> {
          if (rowSet.rowCount() == 0) {
            log.error("Error while deleting snooze from alert in db with id: {}", deleteSnoozeRequest.getAlertId());
            throw ServiceError.DATABASE_ERROR.getCustomException("Error while deleting snooze from alert in db");
          }

          return EmptyResponse.emptyResponse;
        });
  }

  public Single<Integer> updateAlert(@NotNull @Valid UpdateAlertRequest req) {
    // Extract scope names (identifiers) from threshold map keys
    Set<String> scopeNames = new HashSet<>();
    for (AlertCondition alert : req.getAlerts()) {
      if (alert.getThreshold() != null) {
        scopeNames.addAll(alert.getThreshold().keySet());
      }
    }

    if (scopeNames.isEmpty()) {
      return Single.error(new RuntimeException("No scope names found in threshold maps"));
    }

    List<Object> params = Arrays.asList(
        req.getName(),
        req.getDescription(),
        req.getScope().name(),
        req.getDimensionFilters(),
        req.getConditionExpression(),
        req.getSeverity(),
        req.getNotificationChannelId(),
        req.getEvaluationPeriod(),
        req.getEvaluationInterval(),
        req.getUpdatedBy(),
        req.getAlertId()
    );

    // Use transaction to ensure atomicity
    return d11MysqlClient.getWriterPool()
        .rxGetConnection()
        .flatMap(conn -> conn.rxBegin()
            .flatMap(tx -> {
              // 1. Update alert
              return conn.preparedQuery(AlertsQuery.UPDATE_ALERT)
                  .rxExecute(Tuple.tuple(params))
                  .flatMap(rowSet -> {
                    if (rowSet.rowCount() == 0) {
                      return Single.error(ServiceError.DATABASE_ERROR.getCustomException("Alert not found or not updated"));
                    }

                    log.info("Alert updated in database with id: {}", req.getAlertId());

                    // 2. Delete existing scopes
                    return conn.preparedQuery(AlertsQuery.DELETE_ALERT_SCOPES)
                        .rxExecute(Tuple.of(req.getAlertId()))
                        .flatMap(deleteResult -> {
                          log.info("Deleted {} existing scopes for alert_id: {}", deleteResult.rowCount(), req.getAlertId());

                          // 3. Create new scopes
                          return createAlertScopes(conn, req.getAlertId(), new ArrayList<>(scopeNames), req.getAlerts())
                              .map(success -> req.getAlertId());
                        });
                  })
                  .flatMap(alertId -> {
                    // 4. Commit transaction
                    return tx.rxCommit()
                        .toSingleDefault(alertId);
                  })
                  .onErrorResumeNext(error -> {
                    // Rollback on any error
                    return tx.rxRollback()
                        .onErrorComplete()
                        .andThen(Single.error(error));
                  });
            })
            .doFinally(conn::close))
        .onErrorResumeNext(error -> {
          log.error("Error while updating alert (transaction rolled back): {}", error.getMessage());
          if (error instanceof MySQLException) {
            return Single.error(ServiceError.DATABASE_ERROR.getCustomException(error.getMessage()));
          }
          return Single.error(error);
        });
  }

  public Single<Boolean> deleteAlert(@NotNull Integer alertId) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.DELETE_ALERT).rxExecute(Tuple.of(alertId))
        .onErrorResumeNext(error -> {
          log.error("Error while deleting alert from db for alert id {}: {}", alertId, error.getMessage());
          MySQLException mySqlException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        }).map(rowSet -> {
          log.info("Alert deleted from database with id: {}", alertId);
          return rowSet.rowCount() > 0;
        });
  }

  public Single<Boolean> updateAlertState(@NotNull Integer alert_id, @NotNull AlertState state) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.UPDATE_ALERT_STATE)
        .rxExecute(Tuple.of(state.toString(), alert_id)).onErrorResumeNext(error -> {
          log.error("Error while updating alert state in db for alert id {}: {}", alert_id, error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> {
          log.info("Alert state updated in database with id: {}", alert_id);
          return rowSet.rowCount() > 0;
        });
  }

  public Single<Alert> getAlertDetails(@NotNull Integer alert_id) {
    Pool pool = d11MysqlClient.getWriterPool();

    Single<Alert> alertSingle = pool.preparedQuery(AlertsQuery.GET_ALERT_DETAILS)
        .rxExecute(Tuple.of(alert_id))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alert details from db for alert id {}: {}", alert_id, error.getMessage());
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(error.getMessage()));
        })
        .map(rowSet -> {
          if (rowSet.size() > 0) {
            return mapRowToAlert(rowSet.iterator().next());
          } else {
            logAlertNotFound(alert_id);
            throw ServiceError.NOT_FOUND.getCustomException("Alert not found");
          }
        });

    Single<List<Map<String, Object>>> scopesSingle = fetchAlertScopes(pool, alert_id);

    return Single.zip(alertSingle, scopesSingle, this::enrichAlertWithConditions);
  }

  private List<AlertConditionDto> parseAlertConditionsFromScopes(List<Map<String, Object>> scopes)
      throws JsonProcessingException {
    if (scopes == null || scopes.isEmpty()) {
      return new ArrayList<>();
    }

    // Map to store conditions by alias
    Map<String, AlertConditionDto> conditionsByAlias = new LinkedHashMap<>();

    for (Map<String, Object> scope : scopes) {
      String scopeName = (String) scope.get("name");
      String conditionsJson = (String) scope.get("conditions");

      if (conditionsJson == null || conditionsJson.isEmpty()) {
        continue;
      }

      List<Map<String, Object>> scopeConditions = objectMapper.readValue(conditionsJson,
          objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));

      for (Map<String, Object> conditionMap : scopeConditions) {
        String alias = (String) conditionMap.get("alias");
        String metricStr = (String) conditionMap.get("metric");
        String metricOperatorStr = (String) conditionMap.get("metricOperator");
        Object thresholdValue = conditionMap.get("threshold");

        AlertConditionDto alertCondition = conditionsByAlias.get(alias);
        if (alertCondition == null) {
          alertCondition = new AlertConditionDto();
          alertCondition.setAlias(alias);
          alertCondition.setMetric(Metric.valueOf(metricStr));
          alertCondition.setMetricOperator(MetricOperator.valueOf(metricOperatorStr));
          alertCondition.setThreshold(new HashMap<>());
          conditionsByAlias.put(alias, alertCondition);
        }

        if (thresholdValue != null) {
          Float threshold = thresholdValue instanceof Number
              ? ((Number) thresholdValue).floatValue()
              : Float.parseFloat(thresholdValue.toString());
          alertCondition.getThreshold().put(scopeName, threshold);
        }
      }
    }

    return new ArrayList<>(conditionsByAlias.values());
  }

  private Alert mapRowToAlert(Row row) {

    return Alert.builder()
        .alertId(row.getInteger("alert_id"))
        .name(row.getString("name"))
        .description(row.getString("description"))
        .scope(row.getString("scope"))
        .dimensionFilter(row.getString("dimension_filter"))
        .conditionExpression(row.getString("condition_expression"))
        .evaluationPeriod(row.getInteger("evaluation_period"))
        .evaluationInterval(row.getInteger("evaluation_interval"))
        .severityId(row.getInteger("severity_id"))
        .notificationChannelId(row.getInteger("notification_channel_id"))
        .notificationWebhookUrl(row.getString("notification_webhook_url"))
        .createdBy(row.getString("created_by"))
        .updatedBy(row.getString("updated_by"))
        .createdAt(Timestamp.valueOf(row.getLocalDateTime("alert_created_at")))
        .updatedAt(Timestamp.valueOf(row.getLocalDateTime("alert_updated_at")))
        .isActive(row.getBoolean("is_active"))
        .lastSnoozedAt(row.getLocalDateTime("last_snoozed_at"))
        .snoozedFrom(row.getLocalDateTime("snoozed_from"))
        .snoozedUntil(row.getLocalDateTime("snoozed_until"))
        .build();
  }

  public Single<GetAlertsResponse> getAlerts(String name, String scope, @NotNull Integer limit, @NotNull Integer offset,
                                             String createdBy, String updatedBy) {
    final var parameters = getParameters(name, scope, limit, offset, createdBy, updatedBy);
    Pool pool = d11MysqlClient.getWriterPool();

    return pool.preparedQuery(AlertsQuery.GET_ALERTS)
        .rxExecute(Tuple.wrap(parameters.toArray()))
        .flatMap(rows -> {
          if (rows.size() == 0) {
            log.info("No alerts found with filters - name: {}, scope: {}, limit: {}, offset: {}", name, scope, limit, offset);
            return Single.just(new GetAlertsResponse(0, new ArrayList<>(), offset, limit));
          }

          List<Alert> alerts = new ArrayList<>();
          List<Integer> alertIds = new ArrayList<>();
          Integer totalCount = null;

          for (Row row : rows) {
            Alert alert = mapRowToAlert(row);
            alerts.add(alert);
            alertIds.add(alert.getAlertId());
            if (totalCount == null) {
              totalCount = row.getInteger("total_count");
            }
          }

          final Integer finalTotalCount = totalCount;

          String placeholders = alertIds.stream().map(id -> "?").collect(Collectors.joining(","));
          String scopesQuery = String.format(AlertsQuery.GET_ALERT_SCOPES_FOR_IDS, placeholders);

          return pool.preparedQuery(scopesQuery)
              .rxExecute(Tuple.wrap(alertIds.toArray()))
              .map(scopeRows -> {
                Map<Integer, List<Map<String, Object>>> scopesByAlertId = new HashMap<>();
                for (Row row : scopeRows) {
                  Integer alertId = row.getInteger("alert_id");
                  scopesByAlertId.computeIfAbsent(alertId, k -> new ArrayList<>()).add(mapRowToScopeMap(row));
                }

                List<Alert> enrichedAlerts = alerts.stream()
                    .map(alert -> enrichAlertWithConditions(alert,
                        scopesByAlertId.getOrDefault(alert.getAlertId(), new ArrayList<>())))
                    .collect(Collectors.toList());

                return new GetAlertsResponse(finalTotalCount, enrichedAlerts, offset, limit);
              });
        })
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alerts from db: {}", error.getMessage());
          if (error instanceof MySQLException) {
            return Single.error(ServiceError.DATABASE_ERROR.getCustomException(error.getMessage()));
          }
          return Single.error(error);
        });
  }

  public Single<GetAllAlertsResponse> getAllAlerts() {
    Pool pool = d11MysqlClient.getWriterPool();

    Single<List<Alert>> alertsSingle = pool.preparedQuery(AlertsQuery.GET_ALL_ALERTS)
        .rxExecute()
        .map(rowSet -> {
          List<Alert> alerts = new ArrayList<>();
          for (Row row : rowSet) {
            alerts.add(mapRowToAlert(row));
          }
          return alerts;
        })
        .onErrorResumeNext(error -> {
          log.error("Error while fetching all alerts from db: {}", error.getMessage());
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(error.getMessage()));
        });

    Single<Map<Integer, List<Map<String, Object>>>> scopesSingle = pool.preparedQuery(AlertsQuery.GET_ALL_ALERT_SCOPES)
        .rxExecute()
        .map(rowSet -> {
          Map<Integer, List<Map<String, Object>>> scopesByAlertId = new HashMap<>();
          for (Row row : rowSet) {
            Integer alertId = row.getInteger("alert_id");
            scopesByAlertId.computeIfAbsent(alertId, k -> new ArrayList<>()).add(mapRowToScopeMap(row));
          }
          return scopesByAlertId;
        })
        .onErrorResumeNext(error -> {
          log.error("Error while fetching all alert scopes from db: {}", error.getMessage());
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(error.getMessage()));
        });

    return Single.zip(alertsSingle, scopesSingle, (alerts, scopesByAlertId) -> {
      List<Alert> enrichedAlerts = alerts.stream()
          .map(alert -> enrichAlertWithConditions(alert,
              scopesByAlertId.getOrDefault(alert.getAlertId(), new ArrayList<>())))
          .collect(Collectors.toList());

      return new GetAllAlertsResponse(enrichedAlerts);
    });
  }

  public Single<AlertState> getAlertState(@NotNull Integer alert_id) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.GET_CURRENT_STATE_OF_ALERT).rxExecute(Tuple.of(alert_id))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alert state from db for alert id {}: {}", alert_id, error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> {
          if (rowSet.size() > 0) {
            return AlertState.valueOf(rowSet.iterator().next().getString("current_state"));
          } else {
            logAlertNotFound(alert_id);
            throw ServiceError.NOT_FOUND.getCustomException("Alert not found");
          }
        });
  }

  public Single<Boolean> createAlertStateChangeLog(@NotNull Integer alert_id, @NotNull AlertState currentState,
                                                   @NotNull AlertState newState) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.CREATE_ALERT_STATE_HISTORY)
        .rxExecute(Tuple.of(alert_id, currentState.getAlertState(), newState.getAlertState())).onErrorResumeNext(error -> {
          log.error("Error while inserting alert state change log in db for alert id {}: {}", alert_id, error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<Boolean> createAlertEvaluationHistoryLog(@NotNull Integer alert_id, @NotNull String reading,
                                                         @NotNull Integer successInteractionCount, @NotNull Integer errorInteractionCount,
                                                         @NotNull Integer totalInteractionCount, @NotNull Long evaluationTime,
                                                         @NotNull Float threshold, Integer minSuccessInteractions,
                                                         Integer minErrorInteractions, Integer minTotalInteractions,
                                                         @NotNull AlertState currentState) {

    Tuple tuple = Tuple.tuple();
    tuple.addInteger(alert_id);
    tuple.addString(reading);
    tuple.addInteger(successInteractionCount);
    tuple.addInteger(errorInteractionCount);
    tuple.addInteger(totalInteractionCount);
    tuple.addLong(evaluationTime);
    tuple.addFloat(threshold);
    tuple.addInteger(minSuccessInteractions == null ? 0 : minSuccessInteractions);
    tuple.addInteger(minErrorInteractions == null ? 0 : minErrorInteractions);
    tuple.addInteger(minTotalInteractions == null ? 0 : minTotalInteractions);
    tuple.addString(currentState.toString());

    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.CREATE_ALERT_EVALUATION_HISTORY)
        .rxExecute(tuple)
        .onErrorResumeNext(error -> {
          log.error("Error while inserting alert evaluation history log with tuples {} in db for alert id {}: {}",
              tuple.deepToString(), alert_id, error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<List<AlertEvaluationHistoryResponseDto>> getEvaluationHistoryOfAlert(@NotNull Integer alert_id) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.GET_ALERT_EVALUATION_HISTORY).rxExecute(Tuple.of(alert_id))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alert evaluation history from db for alert id {}: {}", alert_id, error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> {
          if (rowSet.size() == 0) {
            log.error("No evaluation history found for alert id: {}", alert_id);
            throw ServiceError.NOT_FOUND.getCustomException("No evaluation history found");
          }

          List<AlertEvaluationHistoryResponseDto> evaluationHistory = new ArrayList<>();

          rowSet.forEach(row -> evaluationHistory
              .add(AlertEvaluationHistoryResponseDto.builder()
                  .reading(row.getString("reading"))
                  .successInteractionCount(row.getInteger("success_interaction_count"))
                  .errorInteractionCount(row.getInteger("error_interaction_count"))
                  .totalInteractionCount(row.getInteger("total_interaction_count"))
                  .evaluationTime(row.getFloat("evaluation_time"))
                  .evaluatedAt(Timestamp.valueOf(row.getLocalDateTime("evaluated_at")))
                  .threshold(row.getFloat("threshold"))
                  .minSuccessInteractions(row.getInteger("min_success_interactions"))
                  .minErrorInteractions(row.getInteger("min_error_interactions"))
                  .minTotalInteractions(row.getInteger("min_total_interactions"))
                  .currentState(AlertState.valueOf(row.getString("current_state")))
                  .build()));

          return evaluationHistory;
        });
  }

  public Single<List<AlertSeverityResponseDto>> getAlertSeverities() {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.GET_SEVERITIES).rxExecute().onErrorResumeNext(error -> {
      log.error("Error while fetching alert severities from db: {}", error.getMessage());
      MySQLException mySQLException = (MySQLException) error;

      return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
    }).map(rowSet -> {
      if (rowSet.size() == 0) {
        log.error("No alert severities found");
        return new ArrayList<>();
      }

      List<AlertSeverityResponseDto> severities = new ArrayList<>();

      rowSet.forEach(row -> severities.add(
          AlertSeverityResponseDto.builder().severity_id(row.getInteger("severity_id")).name(row.getInteger("name"))
              .description(row.getString("description")).build()));

      return severities;
    });
  }

  public Single<Boolean> updateAlertLastEvaluatedAt(@NotNull Integer alert_id) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.UPDATE_LAST_EVALUATED_AT).rxExecute(Tuple.of(alert_id))
        .onErrorResumeNext(error -> {
          logError(alert_id, error);
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<Boolean> createAlertSeverity(@NotNull Integer name, @NotNull String description) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.CREATE_SEVERITY).rxExecute(Tuple.of(name, description))
        .onErrorResumeNext(error -> {
          log.error("Error while inserting alert severity in db: {}", error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<List<AlertNotificationChannelResponseDto>> getNotificationChannels() {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.GET_NOTIFICATION_CHANNELS).rxExecute()
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alert notification channels from db: {}", error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> {
          if (rowSet.size() == 0) {
            log.error("No notification channels found");
            return new ArrayList<>();
          }

          List<AlertNotificationChannelResponseDto> notificationChannels = new ArrayList<>();

          rowSet.forEach(row -> notificationChannels
              .add(AlertNotificationChannelResponseDto
                  .builder()
                  .notification_channel_id(row
                      .getInteger("notification_channel_id"))
                  .name(row.getString("name"))
                  .notification_webhook_url(row.getString("notification_webhook_url"))
                  .build()));

          return notificationChannels;
        });
  }

  public Single<Boolean> createNotificationChannel(@NotNull String name, @NotNull String config) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.CREATE_NOTIFICATION_CHANNEL).rxExecute(Tuple.of(name, config))
        .onErrorResumeNext(error -> {
          log.error("Error while inserting alert notification channel in db: {}", error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<Boolean> createTagForAlert(@NotNull String tag) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.CREATE_TAG).rxExecute(Tuple.of(tag)).onErrorResumeNext(error -> {
      log.error("Error while inserting tag in db: {}", error.getMessage());
      MySQLException mySQLException = (MySQLException) error;

      return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
    }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<Boolean> createTagAndAlertMapping(@NotNull Integer alert_id, @NotNull Integer tag_id) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.CREATE_ALERT_TAG_MAPPING).rxExecute(Tuple.of(alert_id, tag_id))
        .onErrorResumeNext(error -> {
          log.error("Error while inserting tag and alert mapping in db for alert id {}: {}", alert_id, error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<List<AlertTagsResponseDto>> getAllTags() {
    return d11MysqlClient.getWriterPool().query(AlertsQuery.GET_ALL_TAGS).rxExecute().onErrorResumeNext(error -> {
      log.error("Error while fetching tags from db: {}", error.getMessage());
      MySQLException mySQLException = (MySQLException) error;

      return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
    }).map(rowSet -> {
      if (rowSet.size() == 0) {
        log.error("No tags found");
        return new ArrayList<>();
      }

      List<AlertTagsResponseDto> tags = new ArrayList<>();

      rowSet.forEach(row -> tags.add(AlertTagsResponseDto.builder().tag_id(row.getInteger("tag_id")).name(row.getString("name")).build()));

      return tags;
    });
  }

  public Single<List<AlertTagsResponseDto>> getTagsByAlertId(@NotNull Integer alert_id) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.GET_TAGS_FOR_ALERT).rxExecute(Tuple.of(alert_id))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching tags for alert id {} from db: {}", alert_id, error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> {
          if (rowSet.size() == 0) {
            log.error("No tags found for alert id: {}", alert_id);
            throw ServiceError.NOT_FOUND.getCustomException("No tags found");
          }

          List<AlertTagsResponseDto> tags = new ArrayList<>();

          rowSet.forEach(
              row -> tags.add(AlertTagsResponseDto.builder().tag_id(row.getInteger("tag_id")).name(row.getString("name")).build()));

          return tags;
        });
  }

  public Single<Boolean> deleteAlertTagMapping(@NotNull Integer alert_id, @NotNull Integer tag_id) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.DELETE_ALERT_TAG_MAPPING).rxExecute(Tuple.of(alert_id, tag_id))
        .onErrorResumeNext(error -> {
          log.error("Error while deleting tag mapping for alert id {} from db: {}", alert_id, error.getMessage());
          MySQLException mySQLException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<AlertFiltersResponseDto> getAlertsFilters() {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.GET_ALERT_FILTERS).rxExecute().onErrorResumeNext(error -> {
      log.error("Error while fetching alert filters from db: {}", error.getMessage());
      MySQLException mySQLException = (MySQLException) error;

      return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
    }).map(AlertMapper::mapRowSetToAlertFilters);
  }
}
