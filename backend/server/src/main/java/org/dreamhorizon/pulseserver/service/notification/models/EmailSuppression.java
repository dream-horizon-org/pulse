package org.dreamhorizon.pulseserver.service.notification.models;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailSuppression {
  private Long id;
  private String projectId;
  private String email;
  private SuppressionReason reason;
  private String bounceType;
  private String sourceMessageId;
  private Instant suppressedAt;
  private Instant expiresAt;
}
