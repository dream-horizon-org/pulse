package org.dreamhorizon.pulseserver.service.userapikey.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserApiKeyInfo {
  private Long id;
  private String displayName;
  private String rawApiKey;
  private String keyPrefix;
  private Instant createdAt;
}
