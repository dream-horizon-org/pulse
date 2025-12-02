package in.horizonos.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.horizonos.pulseserver.service.alert.core.models.Metric;
import in.horizonos.pulseserver.service.alert.core.models.MetricOperator;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

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
