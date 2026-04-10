package org.dreamhorizon.pulseserver.service.notification.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SlackWebhookChannelConfig extends ChannelConfig {
  private String botName;
  private String iconEmoji;

  @Override
  public ChannelType getChannelType() {
    return ChannelType.SLACK_WEBHOOK;
  }
}
