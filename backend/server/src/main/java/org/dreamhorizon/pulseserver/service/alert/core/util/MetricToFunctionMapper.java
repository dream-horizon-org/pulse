package org.dreamhorizon.pulseserver.service.alert.core.util;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.dreamhorizon.pulseserver.resources.performance.models.Functions;

@Slf4j
public class MetricToFunctionMapper {

  // Network metrics that need PulseType-based variants for network_api scope
  private static final Set<String> NETWORK_METRICS = Set.of(
      "NET_0", "NET_2XX", "NET_3XX", "NET_4XX", "NET_5XX", "NET_COUNT"
  );

  public static Functions mapMetricToFunction(String metric) {
    return mapMetricToFunction(metric, null);
  }

  /**
   * Maps a metric name to a Functions enum, with scope-aware handling.
   * For NETWORK_API scope, uses PulseType-based variants for network metrics.
   */
  public static Functions mapMetricToFunction(String metric, String scope) {
    if (metric == null || metric.isEmpty()) {
      log.warn("Metric is null or empty");
      return null;
    }

    String upperMetric = metric.toUpperCase();

    // For NETWORK_API scope, use PulseType-based variants for network metrics
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
}

