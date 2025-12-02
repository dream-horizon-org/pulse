package org.dreamhorizon.pulseserver.service.alert.core.models;

import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AlertCondition {
  @NotNull
  String alias;

  @NotNull
  Metric metric;

  @NotNull
  MetricOperator metricOperator;

  @NotNull
  Map<String, Float> threshold;
}
