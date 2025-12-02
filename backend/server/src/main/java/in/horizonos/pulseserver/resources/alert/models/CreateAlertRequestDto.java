package in.horizonos.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.horizonos.pulseserver.service.alert.core.models.AlertScope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateAlertRequestDto {
  @NotNull(message = "name cannot be null")
  @JsonProperty("name")
  String name;

  @NotNull
  @JsonProperty("description")
  String description;

  @NotNull
  @JsonProperty("evaluation_period")
  Integer evaluationPeriod;

  @NotNull
  @JsonProperty("evaluation_interval")
  Integer evaluationInterval;

  @NotNull
  @JsonProperty("severity_id")
  Integer severity;

  @NotNull
  @JsonProperty("notification_channel_id")
  Integer notificationChannelId;

  @NotNull
  @JsonProperty("created_by")
  String createdBy;

  @NotNull(message = "scope cannot be null")
  @JsonProperty("scope")
  AlertScope scope;

  @NotNull(message = "identifiers cannot be null")
  @JsonProperty("dimension_filters")
  List<String> dimensionFilters;

  @NotNull(message = "condition_expression cannot be null")
  @JsonProperty("condition_expression")
  String conditionExpression;

  @NotNull(message = "alerts cannot be null")
  @Valid
  @JsonProperty("alerts")
  List<AlertConditionDto> alerts;
}
