package org.dreamhorizon.pulseserver.service.alert.core.models;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateAlertRequest {
  @NotNull(message = "name cannot be null")
  String name;

  @NotNull
  String description;

  @NotNull
  Integer evaluationPeriod;

  @NotNull
  Integer evaluationInterval;

  @NotNull
  Integer severity;

  @NotNull
  Integer notificationChannelId;

  @NotNull
  String createdBy;

  @NotNull
  AlertScope scope;

  @NotNull
  List<String> dimensionFilters;

  @NotNull
  String conditionExpression;

  @NotNull
  List<AlertCondition> alerts;
}