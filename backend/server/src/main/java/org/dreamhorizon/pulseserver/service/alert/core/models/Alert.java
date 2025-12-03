package org.dreamhorizon.pulseserver.service.alert.core.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.validation.constraints.NotNull;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.dreamhorizon.pulseserver.resources.alert.models.AlertConditionDto;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Alert {
  @NotNull
  Integer alertId;

  @NotNull
  String name;

  @NotNull
  String description;

  @NotNull
  String scope;

  @NotNull
  String dimensionFilter;

  @NotNull
  List<AlertConditionDto> alerts;

  @NotNull
  String conditionExpression;

  @NotNull
  Integer evaluationPeriod;

  @NotNull
  Integer evaluationInterval;

  @NotNull
  Integer severityId;

  @NotNull
  Integer notificationChannelId;

  @NotNull
  String notificationWebhookUrl;

  @NotNull
  String createdBy;

  String updatedBy;

  @NotNull
  Timestamp createdAt;

  @NotNull
  Timestamp updatedAt;

  @NotNull
  Boolean isActive;

  @JsonDeserialize(using = LocalDateTimeDeserializer.class)
  @JsonSerialize(using = LocalDateTimeSerializer.class)
  LocalDateTime lastSnoozedAt;

  @JsonDeserialize(using = LocalDateTimeDeserializer.class)
  @JsonSerialize(using = LocalDateTimeSerializer.class)
  LocalDateTime snoozedFrom;

  @JsonDeserialize(using = LocalDateTimeDeserializer.class)
  @JsonSerialize(using = LocalDateTimeSerializer.class)
  LocalDateTime snoozedUntil;

  Boolean isSnoozed;
}