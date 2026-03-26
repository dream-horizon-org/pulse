package org.dreamhorizon.pulseserver.service.notification.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResult {
  private boolean success;
  private String externalId;
  private String errorCode;
  private String errorMessage;
  private boolean permanentFailure;
  private String providerResponse;
  private long latencyMs;
}
