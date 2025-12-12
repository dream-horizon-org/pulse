package org.dreamhorizon.pulseserver.dao;

import org.dreamhorizon.pulseserver.client.mysql.MysqlClient;
import org.dreamhorizon.pulseserver.dao.query.AlertsQueryV4;
import org.dreamhorizon.pulseserver.enums.AlertState;
import org.dreamhorizon.pulseserver.error.ServiceError;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.mysqlclient.MySQLException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.sqlclient.Row;
import io.vertx.rxjava3.sqlclient.Tuple;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AlertsDaoV4 {
    private final MysqlClient d11MysqlClient;

    public Single<AlertV4Details> getAlertDetails(Integer alertId) {
        return d11MysqlClient.getWriterPool()
                .preparedQuery(AlertsQueryV4.GET_ALERT_DETAILS)
                .rxExecute(Tuple.of(alertId))
                .onErrorResumeNext(error -> {
                    log.error("Error while fetching alert details from db for alert id {}: {}", alertId, error.getMessage());
                    MySQLException mySQLException = (MySQLException) error;
                    return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
                })
                .map(rowSet -> {
                    if (rowSet.size() > 0) {
                        Row row = rowSet.iterator().next();
                        return AlertV4Details.builder()
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

    private static void logAlertNotFound(Integer alertId) {
        log.error("Alert not found for alert id: {}", alertId);
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

    public Single<List<AlertScopeDetails>> getAlertScopes(Integer alertId) {
        return d11MysqlClient.getWriterPool()
                .preparedQuery(AlertsQueryV4.GET_ALERT_SCOPES)
                .rxExecute(Tuple.of(alertId))
                .onErrorResumeNext(error -> {
                    log.error("Error while fetching alert scopes: {}", error.getMessage());
                    MySQLException mySQLException = (MySQLException) error;
                    return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
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
                .preparedQuery(AlertsQueryV4.UPDATE_SCOPE_STATE)
                .rxExecute(Tuple.of(state.toString(), scopeId))
                .onErrorResumeNext(error -> {
                    log.error("Error while updating scope state: {}", error.getMessage());
                    MySQLException mySQLException = (MySQLException) error;
                    return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
                })
                .map(rowSet -> rowSet.rowCount() > 0);
    }

    public Single<Boolean> createEvaluationHistory(Integer scopeId, String evaluationResult, AlertState state) {
        return d11MysqlClient.getWriterPool()
                .preparedQuery(AlertsQueryV4.CREATE_EVALUATION_HISTORY)
                .rxExecute(Tuple.of(scopeId, evaluationResult, state.toString()))
                .onErrorResumeNext(error -> {
                    log.error("Error while creating evaluation history: {}", error.getMessage());
                    MySQLException mySQLException = (MySQLException) error;
                    return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
                })
                .map(rowSet -> rowSet.rowCount() > 0);
    }

    public Single<String> getNotificationWebhookUrl(Integer notificationChannelId) {
        if (notificationChannelId == null) {
            return Single.just(null);
        }
        return d11MysqlClient.getWriterPool()
                .preparedQuery(AlertsQueryV4.GET_NOTIFICATION_WEBHOOK_URL)
                .rxExecute(Tuple.of(notificationChannelId))
                .onErrorResumeNext(error -> {
                    log.error("Error while fetching notification webhook URL: {}", error.getMessage());
                    MySQLException mySQLException = (MySQLException) error;
                    return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
                })
                .map(rowSet -> {
                    if (rowSet.size() > 0) {
                        Row row = rowSet.iterator().next();
                        return row.getString("notification_webhook_url");
                    }
                    return null;
                });
    }

    public Single<AlertState> getScopeState(Integer scopeId) {
        return d11MysqlClient.getWriterPool()
                .preparedQuery(AlertsQueryV4.GET_SCOPE_STATE)
                .rxExecute(Tuple.of(scopeId))
                .onErrorResumeNext(error -> {
                    log.error("Error while fetching scope state: {}", error.getMessage());
                    MySQLException mySQLException = (MySQLException) error;
                    return Single.error(ServiceError.DATABASE_ERROR.getCustomException(mySQLException.getMessage()));
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AlertV4Details {
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

