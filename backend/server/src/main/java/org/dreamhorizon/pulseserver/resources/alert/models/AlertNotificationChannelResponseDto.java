package org.dreamhorizon.pulseserver.resources.alert.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AlertNotificationChannelResponseDto {
    @NotNull
    @JsonProperty("notification_channel_id")
    Integer notification_channel_id;

    @NotNull
    @JsonProperty("name")
    String name;

    @NotNull
    @JsonProperty("notification_webhook_url")
    String notification_webhook_url;
}
