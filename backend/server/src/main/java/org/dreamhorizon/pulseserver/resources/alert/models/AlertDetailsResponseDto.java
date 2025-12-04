package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertDetailsResponseDto {
  @NotNull
  @JsonProperty("alert_id")
  Integer alertId;

  @NotNull
  @JsonProperty("name")
  String name;

  @NotNull
  @JsonProperty("description")
  String description;

  @NotNull
  @JsonProperty("scope")
  String scope;

  @JsonProperty("dimension_filter")
  String dimensionFilter;

  @NotNull
  @JsonProperty("alerts")
  List<AlertConditionDto> alerts;

  @NotNull
  @JsonProperty("condition_expression")
  String conditionExpression;

  @NotNull
  @JsonProperty("evaluation_period")
  Integer evaluationPeriod;

  @NotNull
  @JsonProperty("evaluation_interval")
  Integer evaluationInterval;

  @NotNull
  @JsonProperty("severity_id")
  Integer severityId;

  @NotNull
  @JsonProperty("notification_channel_id")
  Integer notificationChannelId;

  @NotNull
  @JsonProperty("notification_webhook_url")
  String notificationWebhookUrl;

  @NotNull
  @JsonProperty("created_by")
  String createdBy;

  @JsonProperty("updated_by")
  String updatedBy;

  @NotNull
  @JsonProperty("created_at")
  Timestamp createdAt;

  @NotNull
  @JsonProperty("updated_at")
  Timestamp updatedAt;

  @NotNull
  @JsonProperty("is_active")
  Boolean isActive;

  @JsonProperty("last_snoozed_at")
  @JsonDeserialize(using = LocalDateTimeDeserializer.class)
  @JsonSerialize(using = LocalDateTimeSerializer.class)
  LocalDateTime lastSnoozedAt;

  @JsonProperty("snoozed_from")
  Long snoozedFrom;

  @JsonProperty("snoozed_until")
  Long snoozedUntil;

  @JsonProperty("is_snoozed")
  Boolean isSnoozed;
}
