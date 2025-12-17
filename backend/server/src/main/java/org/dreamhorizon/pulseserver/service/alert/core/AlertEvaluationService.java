package org.dreamhorizon.pulseserver.service.alert.core;

import afu.org.checkerframework.checker.nullness.qual.Nullable;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.core.eventbus.Message;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.config.ApplicationConfig;
import org.dreamhorizon.pulseserver.constant.Constants;
import org.dreamhorizon.pulseserver.dao.AlertsDao;
import org.dreamhorizon.pulseserver.resources.alert.enums.AlertState;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertEvaluationResponseDto;
import org.dreamhorizon.pulseserver.resources.alert.models.EvaluateAlertResponseDto;
import org.dreamhorizon.pulseserver.resources.performance.models.Functions;
import org.dreamhorizon.pulseserver.resources.performance.models.PerformanceMetricDistributionRes;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;
import org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator;
import org.dreamhorizon.pulseserver.service.alert.core.operatror.MetricOperatorFactory;
import org.dreamhorizon.pulseserver.service.alert.core.util.ExpressionEvaluator;
import org.dreamhorizon.pulseserver.service.alert.core.util.MetricToFunctionMapper;
import org.dreamhorizon.pulseserver.service.interaction.ClickhouseMetricService;
import org.dreamhorizon.pulseserver.util.DateTimeUtil;
import org.dreamhorizon.pulseserver.util.RxObjectMapper;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class AlertEvaluationService {
  private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private final ApplicationConfig applicationConfig;
  private final AlertsDao alertsDao;
  private final ClickhouseMetricService clickhouseMetricService;
  private final MetricOperatorFactory metricOperatorFactory;
  private final ObjectMapper objectMapper;
  private final Vertx vertx;
  private final RxObjectMapper rxObjectMapper;

  public Single<EvaluateAlertResponseDto> evaluateAlertById(Integer alertId) {
    return alertsDao.getAlertDetailsForEvaluation(alertId)
        .flatMap(alertDetails -> {
          triggerEvaluation(alertDetails);
          return Single.just(EvaluateAlertResponseDto.builder()
              .alertId(String.valueOf(alertId))
              .build());
        });
  }

  public void registerConsumers() {
    updateScopeStateEventBusConsumer();
    updateEvaluationHistoryEventBusConsumer();
  }

  private void triggerEvaluation(AlertsDao.AlertDetails alertDetails) {
    LocalTime startTime = LocalTime.now();
    ZonedDateTime endTime = ZonedDateTime.now(ZoneId.of("UTC"));
    ZonedDateTime startTimeWindow = endTime.minusSeconds(alertDetails.getEvaluationPeriod());
    LocalDateTime evaluationWindowStart = startTimeWindow.toLocalDateTime();
    LocalDateTime evaluationWindowEnd = endTime.toLocalDateTime();

    alertsDao.getAlertScopesForEvaluation(alertDetails.getId())
        .flatMap(scopes -> {
          if (scopes.isEmpty()) {
            log.warn("No scopes found for alert id: {}", alertDetails.getId());
            return Single.just(new ArrayList<>());
          }

          QueryRequest queryRequest = buildQueryRequest(alertDetails, scopes);
          return clickhouseMetricService.getMetricDistribution(queryRequest)
              .map(result -> evaluateMetrics(alertDetails, scopes, result));
        })
        .doOnSuccess(evaluationResults -> {
          @SuppressWarnings("unchecked")
          List<EvaluationResult> results = (List<EvaluationResult>) evaluationResults;
          for (EvaluationResult result : results) {
            AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto
                .builder()
                .alert(alertDetails)
                .scopeId(result.getScopeId())
                .evaluationResult(result.getEvaluationResult())
                .timeTaken(Duration.between(startTime, LocalTime.now()).toSeconds())
                .evaluationStartTime(DateTimeUtil.utcToIstTime(evaluationWindowStart).format(formatter))
                .evaluationEndTime(DateTimeUtil.utcToIstTime(evaluationWindowEnd).format(formatter))
                .status(Constants.QUERY_COMPLETED_STATUS)
                .state(result.getState())
                .build();

            triggerSuccessEvent(responseDto);
          }
        })
        .doOnError(error -> {
          log.error("Error in alert evaluation for alert id: {}", alertDetails.getId(), error);
          AlertEvaluationResponseDto responseDto = AlertEvaluationResponseDto
              .builder()
              .alert(alertDetails)
              .timeTaken(Duration.between(startTime, LocalTime.now()).toSeconds())
              .error(error.getMessage())
              .build();
          triggerErrorEvent(responseDto);
        })
        .subscribe();
  }

  private QueryRequest buildQueryRequest(AlertsDao.AlertDetails alertDetails, List<AlertsDao.AlertScopeDetails> scopes) {
    Integer evaluationPeriod = alertDetails.getEvaluationPeriod();
    String bucket = evaluationPeriod + "m";
    ZonedDateTime endTime = ZonedDateTime.now(ZoneId.of("UTC"));
    ZonedDateTime startTime = endTime.minusSeconds(evaluationPeriod);

    QueryRequest.TimeRange timeRange = new QueryRequest.TimeRange();
    timeRange.setStart(startTime.toInstant().toString());
    timeRange.setEnd(endTime.toInstant().toString());

    List<QueryRequest.SelectItem> selectItems = new ArrayList<>();
    boolean isAppVitals = "APP_VITALS".equalsIgnoreCase(alertDetails.getScope());

    if (!isAppVitals) {
      QueryRequest.SelectItem timeBucket = new QueryRequest.SelectItem();
      timeBucket.setFunction(Functions.TIME_BUCKET);
      Map<String, String> timeBucketParams = new HashMap<>();
      timeBucketParams.put("field", "Timestamp");
      timeBucketParams.put("bucket", bucket);
      timeBucket.setParam(timeBucketParams);
      timeBucket.setAlias("t1");
      selectItems.add(timeBucket);
    }

    Set<String> metrics = new HashSet<>();
    for (AlertsDao.AlertScopeDetails scope : scopes) {
      List<Map<String, Object>> alerts = parseConditionsArray(scope.getConditions());
      if (alerts != null) {
        for (Map<String, Object> alert : alerts) {
          String metric = (String) alert.get("metric");
          if (metric != null) {
            metrics.add(metric);
          }
        }
      }
    }

    for (String metric : metrics) {
      Functions function = MetricToFunctionMapper.mapMetricToFunction(metric, alertDetails.getScope());
      if (function != null) {
        QueryRequest.SelectItem selectItem = new QueryRequest.SelectItem();
        selectItem.setFunction(function);
        selectItem.setAlias(metric.toLowerCase());
        selectItems.add(selectItem);
      }
    }

    String scopeField = getScopeField(alertDetails.getScope());
    String scopeFieldAlias = getScopeFieldAlias(alertDetails.getScope());

    if (!isAppVitals) {
      QueryRequest.SelectItem scopeFieldItem = new QueryRequest.SelectItem();
      scopeFieldItem.setFunction(Functions.COL);
      Map<String, String> scopeFieldParams = new HashMap<>();
      scopeFieldParams.put("field", scopeField);
      scopeFieldItem.setParam(scopeFieldParams);
      scopeFieldItem.setAlias(scopeFieldAlias);
      selectItems.add(scopeFieldItem);
    }

    List<QueryRequest.Filter> filters = new ArrayList<>();

    if (!isAppVitals && !scopes.isEmpty()) {
      List<String> scopeNames = new ArrayList<>();
      for (AlertsDao.AlertScopeDetails scope : scopes) {
        if (scope.getName() != null && !scope.getName().isEmpty()) {
          scopeNames.add(scope.getName());
        }
      }
      if (!scopeNames.isEmpty()) {
        QueryRequest.Filter scopeNameFilter = new QueryRequest.Filter();
        scopeNameFilter.setField(scopeField);
        if (scopeNames.size() == 1) {
          scopeNameFilter.setOperator(QueryRequest.Operator.EQ);
        } else {
          scopeNameFilter.setOperator(QueryRequest.Operator.IN);
        }
        scopeNameFilter.setValue(new ArrayList<>(scopeNames));
        filters.add(scopeNameFilter);
      }
    }

    if (!isAppVitals && alertDetails.getScope() != null && !alertDetails.getScope().isEmpty()) {
      QueryRequest.Filter spanTypeFilter = new QueryRequest.Filter();
      spanTypeFilter.setField("SpanType");

      if ("SCREEN".equalsIgnoreCase(alertDetails.getScope())) {
        spanTypeFilter.setOperator(QueryRequest.Operator.IN);
        spanTypeFilter.setValue(List.of("screen_session", "screen_load"));
      } else if ("NETWORK_API".equalsIgnoreCase(alertDetails.getScope())) {
        // Network SpanTypes are like "network.200", "network.404", etc.
        spanTypeFilter.setOperator(QueryRequest.Operator.LIKE);
        spanTypeFilter.setValue(List.of("network.%"));
      } else {
        spanTypeFilter.setOperator(QueryRequest.Operator.IN);
        spanTypeFilter.setValue(List.of(alertDetails.getScope()));
      }

      filters.add(spanTypeFilter);
    }

    if (alertDetails.getDimensionFilter() != null && !alertDetails.getDimensionFilter().isEmpty()) {
      String dimensionFilterSql = extractSqlCondition(alertDetails.getDimensionFilter());
      if (dimensionFilterSql != null && !dimensionFilterSql.isEmpty()) {
        QueryRequest.Filter additionalFilter = new QueryRequest.Filter();
        additionalFilter.setField(isAppVitals ? "Additional" : "SpanType");
        additionalFilter.setOperator(QueryRequest.Operator.ADDITIONAL);
        additionalFilter.setValue(List.of(dimensionFilterSql));
        filters.add(additionalFilter);
      }
    }

    List<String> groupBy = new ArrayList<>();
    if (!isAppVitals) {
      groupBy.add("t1");
      groupBy.add(getScopeFieldAlias(alertDetails.getScope()));
    }

    QueryRequest queryRequest = new QueryRequest();
    queryRequest.setDataType(isAppVitals ? QueryRequest.DataType.EXCEPTIONS : QueryRequest.DataType.TRACES);
    queryRequest.setTimeRange(timeRange);
    queryRequest.setSelect(selectItems);
    queryRequest.setFilters(filters);
    queryRequest.setGroupBy(groupBy);
    queryRequest.setLimit(1000);

    return queryRequest;
  }

  private List<EvaluationResult> evaluateMetrics(AlertsDao.AlertDetails alertDetails,
                                                 List<AlertsDao.AlertScopeDetails> scopes,
                                                 PerformanceMetricDistributionRes queryResult) {
    List<EvaluationResult> results = new ArrayList<>();

    // If query returned no rows, fields will be empty - treat all scopes as NO_DATA
    if (queryResult.getFields() == null || queryResult.getFields().isEmpty()) {
      log.info("No data returned from query for alert {}. All scopes will be set to NO_DATA.",
          alertDetails.getId());
      for (AlertsDao.AlertScopeDetails scope : scopes) {
        List<Map<String, Object>> alerts = parseConditionsArray(scope.getConditions());
        Map<String, Float> noDataResult = buildNoDataEvaluationResult(alerts != null ? alerts : new ArrayList<>());
        String evaluationResultJson = buildEvaluationResultJson(noDataResult, new HashMap<>(), false);
        
        results.add(EvaluationResult.builder()
            .scopeId(scope.getId())
            .state(AlertState.NO_DATA)
            .isFiring(false)
            .evaluationResult(evaluationResultJson)
            .build());
      }
      return results;
    }

    Map<String, Integer> fieldIndexMap = new HashMap<>();
    for (int i = 0; i < queryResult.getFields().size(); i++) {
      fieldIndexMap.put(queryResult.getFields().get(i), i);
    }

    boolean isAppVitals = "APP_VITALS".equalsIgnoreCase(alertDetails.getScope());
    String scopeFieldAlias = getScopeFieldAlias(alertDetails.getScope());

    for (AlertsDao.AlertScopeDetails scope : scopes) {
      List<Map<String, Object>> alerts = parseConditionsArray(scope.getConditions());
      if (alerts == null || alerts.isEmpty()) {
        continue;
      }

      String interactionName = scope.getName();
      Map<String, Boolean> variableValues = new HashMap<>();
      Map<String, Float> metricReadings = new HashMap<>();

      for (Map<String, Object> alert : alerts) {
        String alias = (String) alert.get("alias");
        String metric = (String) alert.get("metric");
        String operator = (String) alert.get("metric_operator");
        Object thresholdObj = alert.get("threshold");

        if (alias == null || metric == null || operator == null || thresholdObj == null) {
          continue;
        }

        Float threshold;
        if (thresholdObj instanceof Number) {
          threshold = ((Number) thresholdObj).floatValue();
        } else if (thresholdObj instanceof String) {
          try {
            threshold = Float.parseFloat((String) thresholdObj);
          } catch (NumberFormatException e) {
            log.warn("Could not parse threshold value: {}", thresholdObj);
            variableValues.put(alias, false);
            continue;
          }
        } else {
          log.warn("Threshold is not a Number or String for alert {}", alias);
          variableValues.put(alias, false);
          continue;
        }

        Integer metricIndex = fieldIndexMap.get(metric.toLowerCase());
        if (metricIndex == null) {
          log.warn("Metric {} not found in query results", metric);
          variableValues.put(alias, false);
          continue;
        }

        boolean isFiring = false;
        Float metricValue = null;

        if (isAppVitals) {
          if (!queryResult.getRows().isEmpty()) {
            List<String> row = queryResult.getRows().get(0);
            if (row.size() > metricIndex) {
              try {
                metricValue = Float.parseFloat(row.get(metricIndex));
                MetricOperator metricOp = MetricOperator.valueOf(operator);
                isFiring = metricOperatorFactory.getProcessor(metricOp).isFiring(threshold, metricValue);
              } catch (NumberFormatException e) {
                log.warn("Could not parse metric value: {}", row.get(metricIndex));
              }
            }
          }
        } else {
          Integer scopeFieldIndex = fieldIndexMap.get(scopeFieldAlias);
          if (scopeFieldIndex == null) {
            log.warn("Scope field {} not found in query results", scopeFieldAlias);
            variableValues.put(alias, false);
            continue;
          }

          for (List<String> row : queryResult.getRows()) {
            if (row.size() > metricIndex && row.size() > scopeFieldIndex) {
              String rowScopeName = row.get(scopeFieldIndex);
              if (interactionName.equals(rowScopeName)) {
                try {
                  metricValue = Float.parseFloat(row.get(metricIndex));
                  MetricOperator metricOp = MetricOperator.valueOf(operator);
                  isFiring = metricOperatorFactory.getProcessor(metricOp).isFiring(threshold, metricValue);
                  break;
                } catch (NumberFormatException e) {
                  log.warn("Could not parse metric value: {}", row.get(metricIndex));
                }
              }
            }
          }
        }

        if (metricValue != null) {
          metricReadings.put(metric, metricValue);
        }
        variableValues.put(alias, isFiring);
      }

      boolean expressionResult = ExpressionEvaluator.evaluate(alertDetails.getConditionExpression(), variableValues);
      AlertState finalState = metricReadings.isEmpty()
          ? AlertState.NO_DATA
          : (expressionResult ? AlertState.FIRING : AlertState.NORMAL);

      EvaluationResult result = new EvaluationResult();
      result.setScopeId(scope.getId());
      result.setState(finalState);
      result.setFiring(finalState == AlertState.FIRING);
      
      Map<String, Float> evaluationResultMap = metricReadings.isEmpty()
          ? buildNoDataEvaluationResult(alerts)
          : metricReadings;
      result.setEvaluationResult(buildEvaluationResultJson(evaluationResultMap, variableValues, expressionResult));
      results.add(result);
    }

    return results;
  }

  private List<Map<String, Object>> parseConditionsArray(String json) {
    try {
      if (json == null || json.isEmpty()) {
        return new ArrayList<>();
      }
      return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
      });
    } catch (Exception e) {
      log.error("Error parsing conditions JSON array: {}", json, e);
      return new ArrayList<>();
    }
  }

  private String extractSqlCondition(String dimensionFilter) {
    if (dimensionFilter == null || dimensionFilter.isEmpty()) {
      return null;
    }

    if (dimensionFilter.trim().startsWith("(") || dimensionFilter.contains("=") || dimensionFilter.contains("AND")
        || dimensionFilter.contains("OR")) {
      log.debug("Dimension filter appears to be SQL, using as-is: {}", dimensionFilter);
      return dimensionFilter;
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> filterMap = (Map<String, Object>) objectMapper.readValue(dimensionFilter, Map.class);
      List<String> conditions = new ArrayList<>();

      for (Map.Entry<String, Object> entry : filterMap.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();

        if (value instanceof Map) {
          @SuppressWarnings("unchecked")
          Map<String, Object> nestedMap = (Map<String, Object>) value;
          for (Map.Entry<String, Object> nestedEntry : nestedMap.entrySet()) {
            String nestedKey = nestedEntry.getKey();
            Object nestedValue = nestedEntry.getValue();
            if (nestedValue != null) {
              conditions.add(String.format("`%s` = '%s'", nestedKey, nestedValue.toString()));
            }
          }
        } else if (value != null) {
          conditions.add(String.format("`%s` = '%s'", key, value.toString()));
        }
      }

      String sqlCondition = String.join(" and ", conditions);
      if (!sqlCondition.isEmpty()) {
        return "(" + sqlCondition + ")";
      }
      return null;
    } catch (Exception e) {
      log.warn("Dimension filter is not JSON, using as raw SQL string: {}", dimensionFilter);
      return dimensionFilter;
    }
  }

  private Map<String, Float> buildNoDataEvaluationResult(List<Map<String, Object>> alerts) {
    Map<String, Float> noDataResult = new HashMap<>();
    for (Map<String, Object> alert : alerts) {
      String metric = (String) alert.get("metric");
      if (metric != null) {
        noDataResult.put(metric, null);
      }
    }
    return noDataResult;
  }

  private String buildEvaluationResultJson(Map<String, Float> metricReadings, Map<String, Boolean> variableValues,
                                           boolean expressionResult) {
    try {
      return objectMapper.writeValueAsString(metricReadings);
    } catch (Exception e) {
      log.error("Error building evaluation result JSON", e);
      return "{}";
    }
  }

  private void triggerSuccessEvent(AlertEvaluationResponseDto responseDto) {
    toHashMap(responseDto)
        .doOnSuccess(responseDtoJson -> {
          JsonObject message = new JsonObject(responseDtoJson);
          vertx.eventBus().send(Constants.EVENT_BUS_RESPONSE_UPDATE_ALERT_EVALUATION_LOGS_CHANNEL, message);
          vertx.eventBus().send(Constants.EVENT_BUS_RESPONSE_UPDATE_ALERT_STATE_CHANNEL, message);
        })
        .doOnError(error -> log.error("Error converting response DTO to hashmap", error))
        .subscribe();
  }

  private void triggerErrorEvent(AlertEvaluationResponseDto responseDto) {
    toHashMap(responseDto)
        .doOnSuccess(responseDtoJson -> {
          JsonObject message = new JsonObject(responseDtoJson);
          vertx.eventBus().send(Constants.EVENT_BUS_RESPONSE_UPDATE_ALERT_EVALUATION_LOGS_CHANNEL, message);
          vertx.eventBus().send(Constants.EVENT_BUS_RESPONSE_UPDATE_ALERT_STATE_CHANNEL, message);
        })
        .doOnError(error -> log.error("Error converting error response DTO to hashmap", error))
        .subscribe();
  }

  private Single<Map<String, Object>> toHashMap(AlertEvaluationResponseDto responseDto) {
    return rxObjectMapper.convertValue(responseDto, new TypeReference<Map<String, Object>>() {
    });
  }

  private void updateScopeStateEventBusConsumer() {
    vertx.eventBus().consumer(Constants.EVENT_BUS_RESPONSE_UPDATE_ALERT_STATE_CHANNEL, this::updateScopeState);
  }

  private void updateScopeState(Message<Object> message) {
    AlertEvaluationResponseDto responseDto = getAlertEvaluationResponseDto(message);
    if (responseDto == null) {
      return;
    }

    if (!Constants.QUERY_COMPLETED_STATUS.equals(responseDto.getStatus())) {
      logError(responseDto);
      if (responseDto.getScopeId() != null) {
        updateScopeState(responseDto.getScopeId(), AlertState.ERRORED)
            .doOnError(error -> logErrorWhileUpdatingScopeState(error, responseDto))
            .subscribe();
      }
      return;
    }

    if (responseDto.getScopeId() != null && responseDto.getState() != null) {
      updateScopeState(responseDto.getScopeId(), responseDto.getState())
          .doOnError(error -> logErrorWhileUpdatingScopeState(error, responseDto))
          .subscribe();

      alertsDao.getAlertScopesForEvaluation(responseDto.getAlert().getId())
          .flatMap(scopes -> {
            String scopeName = scopes.stream()
                .filter(scope -> scope.getId().equals(responseDto.getScopeId()))
                .map(AlertsDao.AlertScopeDetails::getName)
                .findFirst()
                .orElse("Unknown Scope");

            Float metricReading = extractMetricReading(responseDto.getEvaluationResult());

            return alertsDao.getScopeState(responseDto.getScopeId())
                .map(currentState -> {
                  createIncidentIfRequired(responseDto.getState(), responseDto, metricReading, scopeName, currentState);
                  return true;
                });
          })
          .doOnError(error -> log.error("Error fetching scope details for incident: {}", error.getMessage()))
          .subscribe();
    }
  }

  private Single<Boolean> updateScopeState(Integer scopeId, AlertState state) {
    return alertsDao.updateScopeState(scopeId, state);
  }

  private void createIncidentIfRequired(
      AlertState alertState,
      AlertEvaluationResponseDto responseDto,
      Float metricReading,
      String scopeName,
      AlertState currentScopeState
  ) {
    if (!shouldCreateIncident(alertState, responseDto, currentScopeState)) {
      return;
    }

    String message = buildNotificationMessage(responseDto, scopeName, metricReading);
    sendNotification(message);
  }

  private boolean shouldCreateIncident(AlertState alertState, AlertEvaluationResponseDto responseDto, AlertState currentScopeState) {
    if (isAlertSnoozed(responseDto.getAlert())) {
      return false;
    }

    if (alertState == AlertState.NO_DATA) {
      return false;
    }

    return alertState == AlertState.FIRING && !alertState.equals(currentScopeState);
  }

  private boolean isAlertSnoozed(AlertsDao.AlertDetails alert) {
    if (alert == null) {
      return false;
    }
    return isAlertSnoozed(alert.getSnoozedFrom(), alert.getSnoozedUntil());
  }

  private boolean isAlertSnoozed(LocalDateTime snoozedFrom, LocalDateTime snoozedUntil) {
    LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

    if (Objects.isNull(snoozedFrom) || Objects.isNull(snoozedUntil)) {
      return false;
    }

    return (snoozedFrom.isEqual(now) || snoozedFrom.isBefore(now))
        && snoozedUntil.isAfter(now);
  }

  private Float extractMetricReading(String evaluationResult) {
    if (evaluationResult == null || evaluationResult.isEmpty()) {
      return null;
    }
    try {
      Map<String, Object> result = objectMapper.readValue(
          evaluationResult,
          new TypeReference<Map<String, Object>>() {
          });
      return result.values().stream()
          .filter(value -> value instanceof Number)
          .map(value -> ((Number) value).floatValue())
          .findFirst()
          .orElse(null);
    } catch (Exception e) {
      log.warn("Could not extract metric reading from evaluation result: {}", e.getMessage());
      return null;
    }
  }

  private String buildNotificationMessage(AlertEvaluationResponseDto responseDto, String scopeName, Float metricReading) {
    StringBuilder message = new StringBuilder();
    message.append(String.format("Alert threshold breached for alert '%s' for scope '%s'. ",
        responseDto.getAlert().getName(), scopeName));

    if (metricReading != null) {
      message.append(String.format("The metric reading is %f. ", metricReading));
    }

    message.append(String.format("Evaluation Period = %s - %s. ",
        responseDto.getEvaluationStartTime(), responseDto.getEvaluationEndTime()));

    if (responseDto.getEvaluationResult() != null && !responseDto.getEvaluationResult().isEmpty()) {
      try {
        Map<String, Object> evaluationResult = objectMapper.readValue(
            responseDto.getEvaluationResult(),
            new TypeReference<Map<String, Object>>() {
            });
        if (!evaluationResult.isEmpty()) {
          message.append("Metric readings: ");
          evaluationResult.forEach((metric, value) -> {
            message.append(String.format("%s = %s, ", metric, value));
          });
          if (message.length() > 2) {
            message.setLength(message.length() - 2);
          }
        }
      } catch (Exception e) {
        log.warn("Could not parse evaluation result for notification message: {}", e.getMessage());
        message.append("Evaluation result: ").append(responseDto.getEvaluationResult());
      }
    }

    return message.toString();
  }

  private void sendNotification(String message) {
    JsonObject payload = new JsonObject().put("text", message);
    WebClient.create(vertx)
        .postAbs(applicationConfig.getWebhookUrl())
        .putHeader("Content-Type", "application/json")
        .rxSendJsonObject(payload)
        .doOnError(error -> log.error("Failed to send notification", error))
        .subscribe(response -> {
          if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Notification sent successfully");
          } else {
            log.error("Notification failed with status: {}", response.statusCode());
          }
        }, error -> log.error("Notification sending error", error));
  }

  private void updateEvaluationHistoryEventBusConsumer() {
    vertx.eventBus().consumer(Constants.EVENT_BUS_RESPONSE_UPDATE_ALERT_EVALUATION_LOGS_CHANNEL, this::updateEvaluationHistory);
  }

  private void updateEvaluationHistory(Message<Object> message) {
    AlertEvaluationResponseDto responseDto = getAlertEvaluationResponseDto(message);
    if (responseDto == null) {
      return;
    }

    if (Constants.QUERY_COMPLETED_STATUS.equals(responseDto.getStatus())) {
      log.info("Query execution succeeded for alert: {}, scope: {}",
          responseDto.getAlert().getId(), responseDto.getScopeId());

      if (responseDto.getScopeId() != null) {
        String evaluationResult = responseDto.getEvaluationResult();
        if (evaluationResult == null || evaluationResult.isEmpty()) {
          evaluationResult = "{}";
        }
        
        createEvaluationHistory(responseDto.getScopeId(),
            evaluationResult,
            responseDto.getState())
            .doOnError(error -> logErrorWhileUpdatingEvaluationHistory(error, responseDto))
            .subscribe();
      }
    } else {
      logError(responseDto);
      if (responseDto.getScopeId() != null) {
        createEvaluationHistory(responseDto.getScopeId(), "", AlertState.ERRORED)
            .doOnError(error -> logErrorWhileUpdatingEvaluationHistory(error, responseDto))
            .subscribe();
      }
    }
  }

  private Single<Boolean> createEvaluationHistory(Integer scopeId, String evaluationResult, AlertState state) {
    return alertsDao.createEvaluationHistory(scopeId, evaluationResult, state);
  }

  private @Nullable AlertEvaluationResponseDto getAlertEvaluationResponseDto(Message<Object> message) {
    AlertEvaluationResponseDto responseDto;
    try {
      responseDto = objectMapper.readValue(message.body().toString(), AlertEvaluationResponseDto.class);
    } catch (JsonProcessingException e) {
      logParsingError(e);
      return null;
    }
    return responseDto;
  }

  private void logError(AlertEvaluationResponseDto responseDto) {
    log.error("Query execution failed for alert: {}, scope: {} with error: {}",
        responseDto.getAlert().getId(), responseDto.getScopeId(), responseDto.getError());
  }

  private void logErrorWhileUpdatingScopeState(Throwable error, AlertEvaluationResponseDto responseDto) {
    log.error("Error while updating scope state: {} for scope id: {}",
        error.getMessage(), responseDto.getScopeId());
  }

  private void logErrorWhileUpdatingEvaluationHistory(Throwable error, AlertEvaluationResponseDto responseDto) {
    log.error("Error while updating evaluation history: {} for scope id: {}",
        error.getMessage(), responseDto.getScopeId());
  }

  private void logParsingError(JsonProcessingException e) {
    log.error("Error while parsing response to update scope state: {}", e.getMessage());
  }

  private String getScopeField(String scope) {
    if (scope == null || scope.isEmpty()) {
      return "SpanName";
    }
    return switch (scope.toUpperCase()) {
      case "INTERACTION" -> "SpanName";
      case "SCREEN" -> "SpanAttributes['screen.name']";
      case "NETWORK_API" -> "SpanAttributes['http.url']";
      case "APP_VITALS" -> "GroupId";
      default -> "SpanName";
    };
  }

  private String getScopeFieldAlias(String scope) {
    if (scope == null || scope.isEmpty()) {
      return "scopeName";
    }
    return scope.toLowerCase() + "Name";
  }

  @lombok.Data
  @lombok.Builder
  @lombok.NoArgsConstructor
  @lombok.AllArgsConstructor
  private static class EvaluationResult {
    private Integer scopeId;
    private AlertState state;
    private boolean isFiring;
    private String evaluationResult;
  }
}

