package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertNotificationChannelResponseDto {
  @NotNull
  @JsonProperty("notification_channel_id")
  Integer notificationChannelId;

  @NotNull
  @JsonProperty("name")
  String name;

  @NotNull
  @JsonProperty("type")
  String type;

  @NotNull
  @JsonProperty("config")
  String config;

  @NotNull
  @JsonProperty("is_active")
  Boolean isActive;
}
