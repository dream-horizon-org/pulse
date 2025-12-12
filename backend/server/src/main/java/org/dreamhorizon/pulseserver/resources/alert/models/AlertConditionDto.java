package org.dreamhorizon.pulseserver.resources.alert.models;

import org.dreamhorizon.pulseserver.service.alert.core.models.Metric;
import org.dreamhorizon.pulseserver.service.alert.core.models.MetricOperator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AlertConditionDto {
    @NotNull(message = "alias cannot be null")
    @JsonProperty("alias")
    String alias;

    @NotNull(message = "metric cannot be null")
    @JsonProperty("metric")
    Metric metric;

    @NotNull(message = "metric_operator cannot be null")
    @JsonProperty("metric_operator")
    MetricOperator metricOperator;

    @NotNull(message = "threshold cannot be null")
    @JsonProperty("threshold")
    Map<String, Float> threshold;
}

