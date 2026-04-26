package org.dreamhorizon.pulseserver.service.userapikey.models;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserApiKeyInfo {
  private Long id;
  private String displayName;
  private String rawApiKey;
  private String keyPrefix;
  private Instant createdAt;
}
