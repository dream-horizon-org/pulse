package org.dreamhorizon.pulseserver.service.notification.models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = SlackChannelConfig.class, name = "SLACK"),
  @JsonSubTypes.Type(value = SlackWebhookChannelConfig.class, name = "SLACK_WEBHOOK"),
  @JsonSubTypes.Type(value = EmailChannelConfig.class, name = "EMAIL"),
  @JsonSubTypes.Type(value = TeamsChannelConfig.class, name = "TEAMS")
})
public abstract class ChannelConfig {
  public abstract ChannelType getChannelType();
}
