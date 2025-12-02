package in.horizonos.pulseserver.service.alert.core.models;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.FieldDefaults;

@Getter
@Builder(toBuilder = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateAlertRequest {
  @NotNull
  Integer alertId;

  @NotNull
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
  String updatedBy;

  @NotNull
  AlertScope scope;

  @NotNull
  List<String> dimensionFilters;

  @NotNull
  String conditionExpression;

  @NotNull
  List<AlertCondition> alerts;
}