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
public class TeamsChannelConfig extends ChannelConfig {
  private String workflowUrl;
  private String defaultTitle;

  @Override
  public ChannelType getChannelType() {
    return ChannelType.TEAMS;
  }
}
