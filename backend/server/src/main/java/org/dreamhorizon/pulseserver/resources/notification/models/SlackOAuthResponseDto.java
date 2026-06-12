package org.dreamhorizon.pulseserver.resources.notification.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlackOAuthResponseDto {
  private Boolean success;
  private String workspaceId;
  private String workspaceName;
  private Long channelId;
  private String message;
  private String installUrl;
}
