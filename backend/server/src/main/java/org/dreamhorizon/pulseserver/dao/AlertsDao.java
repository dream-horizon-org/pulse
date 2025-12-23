package org.dreamhorizon.pulseserver.dao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLException;
import io.vertx.rxjava3.mysqlclient.MySQLClient;
import io.vertx.rxjava3.sqlclient.Pool;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.SqlConnection;
import io.vertx.rxjava3.sqlclient.Tuple;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.query.AlertsQuery;
import org.dreamhorizon.pulseserver.dto.response.EmptyResponse;
import org.dreamhorizon.pulseserver.error.ServiceError;
import org.dreamhorizon.pulseserver.resources.alert.enums.AlertState;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertConditionDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertFiltersResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertNotificationChannelResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertSeverityResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertTagsResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.EvaluationHistoryEntryDto;
import org.dreamhorizon.pulseserver.resources.alert.models.ScopeEvaluationHistoryDto;
import org.dreamhorizon.pulseserver.service.alert.core.models.Alert;
import org.dreamhorizon.pulseserver.service.alert.core.models.AlertCondition;
import org.dreamhorizon.pulseserver.service.alert.core.models.CreateAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.DeleteSnoozeRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.GetAlertsResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.GetAllAlertsResponse;
import org.dreamhorizon.pulseserver.service.alert.core.models.Metric;
import org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator;
import org.dreamhorizon.pulseserver.service.alert.core.models.SnoozeAlertRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.UpdateAlertRequest;
import org.dreamhorizon.pulseserver.util.AlertMapper;
import org.dreamhorizon.pulseserver.util.DateTimeUtil;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AlertsDao {
  private final MysqlClient d11MysqlClient;
  private final DateTimeUtil dateTimeUtil;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static void logAlertNotFound(Integer alertId) {
    log.error("Alert not found for alert id: {}", alertId);
  }

  private static @NotNull List<Object> getParameters(String name, String scope, Integer limit, Integer offset,
                                                     String createdBy, String updatedBy, String status) {
    // Create a list to hold query parameters
    List<Object> parameters = new ArrayList<>();
    String statusValue = status == null ? "" : status;

    // Add parameters dynamically
    parameters.add(name == null ? "" : name);
    parameters.add(name == null ? "" : name);
    parameters.add(scope == null ? "" : scope);
    parameters.add(scope == null ? "" : scope);
    parameters.add(createdBy == null ? "" : createdBy);
    parameters.add(createdBy == null ? "" : createdBy);
    parameters.add(updatedBy == null ? "" : updatedBy);
    parameters.add(updatedBy == null ? "" : updatedBy);
    parameters.add(statusValue);
    parameters.add(statusValue);
    parameters.add(statusValue);
    parameters.add(statusValue);
    parameters.add(statusValue); // For SNOOZED case
    parameters.add(limit == null ? 10 : limit);
    parameters.add(offset == null ? 0 : offset);
    return parameters;
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
      AlertState alertStatus = computeAlertStatusFromScopes(scopes);
      return alert.toBuilder()
          .alerts(alertConditions)
          .status(alertStatus)
          .build();
    } catch (Exception e) {
      log.error("Error parsing alert conditions for alert {}: {}", alert.getAlertId(), e.getMessage());
      return alert;
    }
  }

  private AlertState computeAlertStatusFromScopes(List<Map<String, Object>> scopes) {
    if (scopes == null || scopes.isEmpty()) {
      return AlertState.NORMAL;
    }

    boolean hasFiring = false;
    boolean hasNoData = false;
    boolean allNormal = true;

    for (Map<String, Object> scope : scopes) {
      String stateStr = (String) scope.get("state");
      if (stateStr == null) {
        continue;
      }

      try {
        AlertState state = AlertState.valueOf(stateStr);
        if (state == AlertState.FIRING) {
          hasFiring = true;
          allNormal = false;
        } else if (state == AlertState.NO_DATA) {
          hasNoData = true;
          allNormal = false;
        } else if (state != AlertState.NORMAL) {
          allNormal = false;
        }
      } catch (IllegalArgumentException e) {
        log.warn("Unknown alert state: {}", stateStr);
      }
    }

    if (hasFiring) {
      return AlertState.FIRING;
    } else if (hasNoData) {
      return AlertState.NO_DATA;
    } else if (allNormal) {
      return AlertState.NORMAL;
    }

    return AlertState.NORMAL;
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
      conditionMap.put("metric_operator", condition.getMetricOperator().name());
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

                    // 2. Soft delete existing scopes (mark as inactive)
                    return conn.preparedQuery(AlertsQuery.DELETE_ALERT_SCOPES)
                        .rxExecute(Tuple.of(req.getAlertId()))
                        .flatMap(deleteResult -> {
                          log.info("Deactivated {} existing scopes for alert_id: {}", deleteResult.rowCount(), req.getAlertId());

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

  public Single<Alert> getAlertDetails(@NotNull Integer alertId) {
    Pool pool = d11MysqlClient.getWriterPool();

    Single<Alert> alertSingle = pool.preparedQuery(AlertsQuery.GET_ALERT_DETAILS)
        .rxExecute(Tuple.of(alertId))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alert details from db for alert id {}: {}", alertId, error.getMessage());
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(error.getMessage()));
        })
        .map(rowSet -> {
          if (rowSet.size() > 0) {
            return mapRowToAlert(rowSet.iterator().next());
          } else {
            logAlertNotFound(alertId);
            throw ServiceError.NOT_FOUND.getCustomException("Alert not found");
          }
        });

    Single<List<Map<String, Object>>> scopesSingle = fetchAlertScopes(pool, alertId);

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
        String metricOperatorStr = (String) conditionMap.get("metric_operator");
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
                                             String createdBy, String updatedBy, String status) {
    final var parameters = getParameters(name, scope, limit, offset, createdBy, updatedBy, status);
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


  public Single<List<AlertSeverityResponseDto>> getAlertSeverities() {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.GET_SEVERITIES).rxExecute().onErrorResumeNext(error -> {
      log.error("Error while fetching alert severities from db: {}", error.getMessage());
      MySQLException mySqlException = (MySQLException) error;

      return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
    }).map(rowSet -> {
      if (rowSet.size() == 0) {
        log.error("No alert severities found");
        return new ArrayList<>();
      }

      List<AlertSeverityResponseDto> severities = new ArrayList<>();

      rowSet.forEach(row -> severities.add(
          AlertSeverityResponseDto.builder().severityId(row.getInteger("severity_id")).name(row.getInteger("name"))
              .description(row.getString("description")).build()));

      return severities;
    });
  }

  public Single<Boolean> createAlertSeverity(@NotNull Integer name, @NotNull String description) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.CREATE_SEVERITY).rxExecute(Tuple.of(name, description))
        .onErrorResumeNext(error -> {
          log.error("Error while inserting alert severity in db: {}", error.getMessage());
          MySQLException mySqlException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<List<AlertNotificationChannelResponseDto>> getNotificationChannels() {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.GET_NOTIFICATION_CHANNELS).rxExecute()
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alert notification channels from db: {}", error.getMessage());
          MySQLException mySqlException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        }).map(rowSet -> {
          if (rowSet.size() == 0) {
            log.error("No notification channels found");
            return new ArrayList<>();
          }

          List<AlertNotificationChannelResponseDto> notificationChannels = new ArrayList<>();

          rowSet.forEach(row -> notificationChannels
              .add(AlertNotificationChannelResponseDto
                  .builder()
                  .notificationChannelId(row
                      .getInteger("notification_channel_id"))
                  .name(row.getString("name"))
                  .notificationWebhookUrl(row.getString("notification_webhook_url"))
                  .build()));

          return notificationChannels;
        });
  }

  public Single<Boolean> createNotificationChannel(@NotNull String name, @NotNull String config) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.CREATE_NOTIFICATION_CHANNEL).rxExecute(Tuple.of(name, config))
        .onErrorResumeNext(error -> {
          log.error("Error while inserting alert notification channel in db: {}", error.getMessage());
          MySQLException mySqlException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<Boolean> createTagForAlert(@NotNull String tag) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.CREATE_TAG).rxExecute(Tuple.of(tag)).onErrorResumeNext(error -> {
      log.error("Error while inserting tag in db: {}", error.getMessage());
      MySQLException mySqlException = (MySQLException) error;

      return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
    }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<Boolean> createTagAndAlertMapping(@NotNull Integer alertId, @NotNull Integer tagId) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.CREATE_ALERT_TAG_MAPPING).rxExecute(Tuple.of(alertId, tagId))
        .onErrorResumeNext(error -> {
          log.error("Error while inserting tag and alert mapping in db for alert id {}: {}", alertId, error.getMessage());
          MySQLException mySqlException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<List<AlertTagsResponseDto>> getAllTags() {
    return d11MysqlClient.getWriterPool().query(AlertsQuery.GET_ALL_TAGS).rxExecute().onErrorResumeNext(error -> {
      log.error("Error while fetching tags from db: {}", error.getMessage());
      MySQLException mySqlException = (MySQLException) error;

      return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
    }).map(rowSet -> {
      if (rowSet.size() == 0) {
        log.error("No tags found");
        return new ArrayList<>();
      }

      List<AlertTagsResponseDto> tags = new ArrayList<>();

      rowSet.forEach(row -> tags.add(AlertTagsResponseDto.builder().tagId(row.getInteger("tag_id")).name(row.getString("name")).build()));

      return tags;
    });
  }

  public Single<List<AlertTagsResponseDto>> getTagsByAlertId(@NotNull Integer alertId) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.GET_TAGS_FOR_ALERT).rxExecute(Tuple.of(alertId))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching tags for alert id {} from db: {}", alertId, error.getMessage());
          MySQLException mySqlException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        }).map(rowSet -> {
          if (rowSet.size() == 0) {
            log.error("No tags found for alert id: {}", alertId);
            throw ServiceError.NOT_FOUND.getCustomException("No tags found");
          }

          List<AlertTagsResponseDto> tags = new ArrayList<>();

          rowSet.forEach(
              row -> tags.add(AlertTagsResponseDto.builder().tagId(row.getInteger("tag_id")).name(row.getString("name")).build()));

          return tags;
        });
  }

  public Single<Boolean> deleteAlertTagMapping(@NotNull Integer alertId, @NotNull Integer tagId) {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.DELETE_ALERT_TAG_MAPPING).rxExecute(Tuple.of(alertId, tagId))
        .onErrorResumeNext(error -> {
          log.error("Error while deleting tag mapping for alert id {} from db: {}", alertId, error.getMessage());
          MySQLException mySqlException = (MySQLException) error;

          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        }).map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<AlertFiltersResponseDto> getAlertsFilters() {
    return d11MysqlClient.getWriterPool().preparedQuery(AlertsQuery.GET_ALERT_FILTERS).rxExecute().onErrorResumeNext(error -> {
      log.error("Error while fetching alert filters from db: {}", error.getMessage());
      MySQLException mySqlException = (MySQLException) error;

      return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
    }).map(AlertMapper::mapRowSetToAlertFilters);
  }

  public Single<List<org.dreamhorizon.pulseserver.resources.alert.models.MetricItemDto>> getMetricsByScope(@NotNull String scope) {
    return d11MysqlClient.getWriterPool()
        .preparedQuery(AlertsQuery.GET_METRICS_BY_SCOPE)
        .rxExecute(Tuple.of(scope))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching metrics for scope {} from db: {}", scope, error.getMessage());
          MySQLException mySqlException = (MySQLException) error;
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        })
        .map(rowSet -> {
          List<org.dreamhorizon.pulseserver.resources.alert.models.MetricItemDto> metrics = new ArrayList<>();
          for (Row row : rowSet) {
            metrics.add(org.dreamhorizon.pulseserver.resources.alert.models.MetricItemDto.builder()
                .id(row.getInteger("id"))
                .name(row.getString("name"))
                .label(row.getString("label"))
                .build());
          }
          return metrics;
        });
  }

  public Single<List<org.dreamhorizon.pulseserver.resources.alert.models.AlertScopeItemDto>> getAlertScopes() {
    return d11MysqlClient.getWriterPool()
        .preparedQuery(AlertsQuery.GET_ALL_ALERT_SCOPE_TYPES)
        .rxExecute()
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alert scopes from db: {}", error.getMessage());
          MySQLException mySqlException = (MySQLException) error;
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        })
        .map(rowSet -> {
          List<org.dreamhorizon.pulseserver.resources.alert.models.AlertScopeItemDto> scopes = new ArrayList<>();
          for (Row row : rowSet) {
            scopes.add(org.dreamhorizon.pulseserver.resources.alert.models.AlertScopeItemDto.builder()
                .id(row.getInteger("id"))
                .name(row.getString("name"))
                .label(row.getString("label"))
                .build());
          }
          return scopes;
        });
  }

  public Single<AlertDetails> getAlertDetailsForEvaluation(Integer alertId) {
    return d11MysqlClient.getWriterPool()
        .preparedQuery(AlertsQuery.GET_ALERT_DETAILS_FOR_EVALUATION)
        .rxExecute(Tuple.of(alertId))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alert details from db for alert id {}: {}", alertId, error.getMessage());
          MySQLException mySqlException = (MySQLException) error;
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        })
        .map(rowSet -> {
          if (rowSet.size() > 0) {
            Row row = rowSet.iterator().next();
            return AlertDetails.builder()
                .id(row.getInteger("id"))
                .name(row.getString("name"))
                .description(row.getString("description"))
                .scope(row.getString("scope"))
                .dimensionFilter(getJsonAsString(row, "dimension_filter"))
                .conditionExpression(row.getString("condition_expression"))
                .severityId(row.getInteger("severity_id"))
                .notificationChannelId(row.getInteger("notification_channel_id"))
                .evaluationPeriod(row.getInteger("evaluation_period"))
                .evaluationInterval(row.getInteger("evaluation_interval"))
                .createdBy(row.getString("created_by"))
                .updatedBy(row.getString("updated_by"))
                .isActive(row.getBoolean("is_active"))
                .snoozedFrom(getLocalDateTime(row, "snoozed_from"))
                .snoozedUntil(getLocalDateTime(row, "snoozed_until"))
                .build();
          } else {
            logAlertNotFound(alertId);
            throw ServiceError.NOT_FOUND.getCustomException("Alert not found");
          }
        });
  }

  private static String getJsonAsString(Row row, String columnName) {
    try {
      Object value = row.getValue(columnName);
      if (value == null) {
        return null;
      }

      if (value instanceof JsonObject) {
        return ((JsonObject) value).encode();
      } else if (value instanceof JsonArray) {
        return ((JsonArray) value).encode();
      } else if (value instanceof String) {
        return (String) value;
      } else {
        log.warn("Unexpected type for column {}: {}", columnName, value.getClass().getName());
        return value.toString();
      }
    } catch (Exception e) {
      log.warn("Could not get JSON value for column {}: {}", columnName, e.getMessage());
      try {
        return row.getString(columnName);
      } catch (Exception ex) {
        log.warn("Could not get String value for column {}: {}", columnName, ex.getMessage());
        return null;
      }
    }
  }

  private static LocalDateTime getLocalDateTime(Row row, String columnName) {
    try {
      return row.getLocalDateTime(columnName);
    } catch (Exception e) {
      log.warn("Could not get LocalDateTime value for column {}: {}", columnName, e.getMessage());
      return null;
    }
  }

  public Single<List<AlertScopeDetails>> getAlertScopesForEvaluation(Integer alertId) {
    return d11MysqlClient.getWriterPool()
        .preparedQuery(AlertsQuery.GET_ALERT_SCOPES)
        .rxExecute(Tuple.of(alertId))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching alert scopes: {}", error.getMessage());
          MySQLException mySqlException = (MySQLException) error;
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        })
        .map(rowSet -> {
          List<AlertScopeDetails> scopes = new ArrayList<>();
          for (Row row : rowSet) {
            scopes.add(AlertScopeDetails.builder()
                .id(row.getInteger("id"))
                .alertId(row.getInteger("alert_id"))
                .name(row.getString("name"))
                .conditions(getJsonAsString(row, "conditions"))
                .state(AlertState.valueOf(row.getString("state")))
                .build());
          }
          return scopes;
        });
  }

  public Single<Boolean> updateScopeState(Integer scopeId, AlertState state) {
    return d11MysqlClient.getWriterPool()
        .preparedQuery(AlertsQuery.UPDATE_SCOPE_STATE)
        .rxExecute(Tuple.of(state.toString(), scopeId))
        .onErrorResumeNext(error -> {
          log.error("Error while updating scope state: {}", error.getMessage());
          MySQLException mySqlException = (MySQLException) error;
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        })
        .map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<Boolean> createEvaluationHistory(Integer scopeId, String evaluationResult, AlertState state) {
    return d11MysqlClient.getWriterPool()
        .preparedQuery(AlertsQuery.CREATE_EVALUATION_HISTORY)
        .rxExecute(Tuple.of(scopeId, evaluationResult, state.toString()))
        .onErrorResumeNext(error -> {
          log.error("Error while creating evaluation history: {}", error.getMessage());
          MySQLException mySqlException = (MySQLException) error;
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        })
        .map(rowSet -> rowSet.rowCount() > 0);
  }

  public Single<AlertState> getScopeState(Integer scopeId) {
    return d11MysqlClient.getWriterPool()
        .preparedQuery(AlertsQuery.GET_SCOPE_STATE)
        .rxExecute(Tuple.of(scopeId))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching scope state: {}", error.getMessage());
          MySQLException mySqlException = (MySQLException) error;
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        })
        .map(rowSet -> {
          if (rowSet.size() > 0) {
            Row row = rowSet.iterator().next();
            String stateStr = row.getString("state");
            if (stateStr != null) {
              try {
                return AlertState.valueOf(stateStr);
              } catch (IllegalArgumentException e) {
                log.warn("Invalid alert state: {}", stateStr);
                return null;
              }
            }
          }
          return null;
        });
  }

  public Single<List<ScopeEvaluationHistoryDto>> getEvaluationHistoryByAlert(Integer alertId) {
    return d11MysqlClient.getWriterPool()
        .preparedQuery(AlertsQuery.GET_EVALUATION_HISTORY_BY_ALERT)
        .rxExecute(Tuple.of(alertId))
        .onErrorResumeNext(error -> {
          log.error("Error while fetching evaluation history for alert id {}: {}", alertId, error.getMessage());
          MySQLException mySqlException = (MySQLException) error;
          return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySqlException.getMessage()));
        })
        .map(rowSet -> {
          Map<Integer, ScopeEvaluationHistoryDto> scopeMap = new HashMap<>();

          for (Row row : rowSet) {
            Integer scopeId = row.getInteger("scope_id");
            String scopeName = row.getString("scope_name");

            ScopeEvaluationHistoryDto scopeHistory = scopeMap.computeIfAbsent(scopeId, id ->
                ScopeEvaluationHistoryDto.builder()
                    .scopeId(id)
                    .scopeName(scopeName)
                    .evaluationHistory(new ArrayList<>())
                    .build()
            );

            EvaluationHistoryEntryDto entry = EvaluationHistoryEntryDto.builder()
                .evaluationId(row.getInteger("evaluation_id"))
                .evaluationResult(getJsonAsString(row, "evaluation_result"))
                .state(AlertState.valueOf(row.getString("state")))
                .evaluatedAt(Timestamp.valueOf(row.getLocalDateTime("evaluated_at")))
                .build();

            scopeHistory.getEvaluationHistory().add(entry);
          }

          return new ArrayList<>(scopeMap.values());
        });
  }

  @lombok.Data
  @lombok.Builder
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
  public static class AlertDetails {
    private Integer id;
    private String name;
    private String description;
    private String scope;
    private String dimensionFilter;
    private String conditionExpression;
    private Integer severityId;
    private Integer notificationChannelId;
    private Integer evaluationPeriod;
    private Integer evaluationInterval;
    private String createdBy;
    private String updatedBy;
    private Boolean isActive;
    private LocalDateTime snoozedFrom;
    private LocalDateTime snoozedUntil;
  }

  @lombok.Data
  @lombok.Builder
  public static class AlertScopeDetails {
    private Integer id;
    private Integer alertId;
    private String name;
    private String conditions;
    private AlertState state;
  }
}
