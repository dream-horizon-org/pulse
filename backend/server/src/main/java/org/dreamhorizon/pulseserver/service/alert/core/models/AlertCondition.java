package org.dreamhorizon.pulseserver.service.alert.core.models;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

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

