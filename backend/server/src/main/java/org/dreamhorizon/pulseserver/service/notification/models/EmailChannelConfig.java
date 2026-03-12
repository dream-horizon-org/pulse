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
public class EmailChannelConfig extends ChannelConfig {
  private String fromAddress;
  private String fromName;
  private String replyToAddress;
  private String configurationSetName;

  @Override
  public ChannelType getChannelType() {
    return ChannelType.EMAIL;
  }
}
