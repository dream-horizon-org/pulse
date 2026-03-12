package org.dreamhorizon.pulseserver.service.notification.oauth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlackOAuthResult {
  private String accessToken;
  private String workspaceId;
  private String workspaceName;
  private String botUserId;
}
