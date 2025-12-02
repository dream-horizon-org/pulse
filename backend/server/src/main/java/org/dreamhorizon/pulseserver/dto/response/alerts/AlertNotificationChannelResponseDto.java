package org.dreamhorizon.pulseserver.dto.response.alerts;

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
  @JsonProperty("notification_webhook_url")
  String notificationWebhookUrl;
}
