package org.dreamhorizon.pulseserver.dao.userapikey.models;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserApiKey {
  private Long id;
  private String userId;
  private String displayName;
  private String apiKeyHash;
  private String keyPrefix;
  private Boolean isActive;
  private Instant createdAt;
  private Instant revokedAt;
  private String revokedBy;
}
