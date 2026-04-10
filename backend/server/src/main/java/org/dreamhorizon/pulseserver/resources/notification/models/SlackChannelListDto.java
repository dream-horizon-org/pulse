package org.dreamhorizon.pulseserver.resources.notification.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlackChannelListDto {
  private String id;
  private String name;
  private Boolean isPrivate;
  private Boolean isMember;
}
