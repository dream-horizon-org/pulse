package org.dreamhorizon.pulseserver.service.userapikey.models;

import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserApiKeyPublicInfo {
  private Long id;
  private String displayName;
  private String keyPrefix;
  private Boolean isActive;
  private Instant createdAt;
}
