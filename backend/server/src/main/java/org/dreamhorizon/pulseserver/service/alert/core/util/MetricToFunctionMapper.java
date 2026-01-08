package org.dreamhorizon.pulseserver.service.alert.core.util;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.performance.models.Functions;
import org.dreamhorizon.pulseserver.resources.performance.models.QueryRequest;

@Slf4j
public class MetricToFunctionMapper {

  private static final Set<String> NETWORK_METRICS = Set.of(
      "NET_0", "NET_2XX", "NET_3XX", "NET_4XX", "NET_5XX", "NET_COUNT"
  );

  private static final Set<String> EXCEPTIONS_METRICS = Set.of(
      "CRASH_USERS",
      "CRASH_SESSIONS",
      "ANR_USERS",
      "ANR_SESSIONS",
      "NON_FATAL_USERS",
      "NON_FATAL_SESSIONS"
  );

  private static final Set<String> COMPOSITE_METRICS = Set.of(
      "CRASH_FREE_USERS_PERCENTAGE",
      "CRASH_FREE_SESSIONS_PERCENTAGE",
      "ANR_FREE_USERS_PERCENTAGE",
      "ANR_FREE_SESSIONS_PERCENTAGE",
      "NON_FATAL_FREE_USERS_PERCENTAGE",
      "NON_FATAL_FREE_SESSIONS_PERCENTAGE"
  );

  public static Functions mapMetricToFunction(String metric) {
    return mapMetricToFunction(metric, null);
  }

  public static Functions mapMetricToFunction(String metric, String scope) {
    if (metric == null || metric.isEmpty()) {
      log.warn("Metric is null or empty");
      return null;
    }

    String upperMetric = metric.toUpperCase();

    if ("NETWORK_API".equalsIgnoreCase(scope) && NETWORK_METRICS.contains(upperMetric)) {
      String pulseTypeMetric = upperMetric + "_BY_PULSE_TYPE";
      try {
        return Functions.valueOf(pulseTypeMetric);
      } catch (IllegalArgumentException e) {
        log.warn("PulseType variant not found for metric: {}, falling back to default", metric);
      }
    }

    try {
      return Functions.valueOf(upperMetric);
    } catch (IllegalArgumentException e) {
      log.warn("Unknown metric/function: {}, returning null", metric);
      return null;
    }
  }

  public static QueryRequest.DataType getDataTypeForMetric(String metric, String scope) {
    if (metric == null || metric.isEmpty()) {
      return QueryRequest.DataType.TRACES;
    }

    String upperMetric = metric.toUpperCase();
    if (EXCEPTIONS_METRICS.contains(upperMetric)) {
      return QueryRequest.DataType.EXCEPTIONS;
    }

    if ("APP_VITALS".equalsIgnoreCase(scope) && 
        (upperMetric.equals("ALL_USERS") || upperMetric.equals("ALL_SESSIONS"))) {
      return QueryRequest.DataType.LOGS;
    }

    if (COMPOSITE_METRICS.contains(upperMetric)) {
      return null;
    }

    return QueryRequest.DataType.TRACES;
  }

  public static QueryRequest.DataType getDataTypeForMetric(String metric) {
    return getDataTypeForMetric(metric, null);
  }

  public static boolean isCompositeMetric(String metric) {
    if (metric == null || metric.isEmpty()) {
      return false;
    }
    return COMPOSITE_METRICS.contains(metric.toUpperCase());
  }

  public static CompositeMetricComponents getCompositeMetricComponents(String metric, String scope) {
    if (metric == null || metric.isEmpty()) {
      return null;
    }

    String upperMetric = metric.toUpperCase();
    boolean isAppVitals = "APP_VITALS".equalsIgnoreCase(scope);
    QueryRequest.DataType totalMetricDataType = isAppVitals 
        ? QueryRequest.DataType.LOGS 
        : QueryRequest.DataType.TRACES;

    return switch (upperMetric) {
      case "CRASH_FREE_USERS_PERCENTAGE" -> new CompositeMetricComponents("ALL_USERS", "CRASH_USERS", totalMetricDataType);
      case "CRASH_FREE_SESSIONS_PERCENTAGE" -> new CompositeMetricComponents("ALL_SESSIONS", "CRASH_SESSIONS", totalMetricDataType);
      case "ANR_FREE_USERS_PERCENTAGE" -> new CompositeMetricComponents("ALL_USERS", "ANR_USERS", totalMetricDataType);
      case "ANR_FREE_SESSIONS_PERCENTAGE" -> new CompositeMetricComponents("ALL_SESSIONS", "ANR_SESSIONS", totalMetricDataType);
      case "NON_FATAL_FREE_USERS_PERCENTAGE" -> new CompositeMetricComponents("ALL_USERS", "NON_FATAL_USERS", totalMetricDataType);
      case "NON_FATAL_FREE_SESSIONS_PERCENTAGE" -> new CompositeMetricComponents("ALL_SESSIONS", "NON_FATAL_SESSIONS", totalMetricDataType);
      default -> null;
    };
  }

  public static CompositeMetricComponents getCompositeMetricComponents(String metric) {
    return getCompositeMetricComponents(metric, null);
  }

  public static class CompositeMetricComponents {
    public final String tracesMetric;
    public final String exceptionsMetric;
    public final QueryRequest.DataType totalMetricDataType;

    public CompositeMetricComponents(String tracesMetric, String exceptionsMetric) {
      this.tracesMetric = tracesMetric;
      this.exceptionsMetric = exceptionsMetric;
      this.totalMetricDataType = QueryRequest.DataType.TRACES;
    }

    public CompositeMetricComponents(String tracesMetric, String exceptionsMetric, QueryRequest.DataType totalMetricDataType) {
      this.tracesMetric = tracesMetric;
      this.exceptionsMetric = exceptionsMetric;
      this.totalMetricDataType = totalMetricDataType;
    }
  }
}
