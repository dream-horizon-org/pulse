package org.dreamhorizon.pulseserver.service.alert.core.util;

import org.dreamhorizon.pulseserver.resources.performance.models.Functions;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MetricToFunctionMapper {
  public static Functions mapMetricToFunction(String metric) {
    if (metric == null || metric.isEmpty()) {
      log.warn("Metric is null or empty");
      return null;
    }

    try {
      return Functions.valueOf(metric.toUpperCase());
    } catch (IllegalArgumentException e) {
      log.warn("Unknown metric/function: {}, returning null", metric);
      return null;
    }
  }
}

