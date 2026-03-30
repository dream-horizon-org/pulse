package org.dreamhorizon.pulseserver.resources.funnel.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunnelSessionDetail {
  private String sessionId;
  private String userId;
  private String eventName;
  private String exceptionType;
  private String exceptionMessage;
  private String title;
  private String screenName;
  private String timestamp;
  private String groupId;
  private String platform;
  private String appVersion;
  private String deviceModel;
}
