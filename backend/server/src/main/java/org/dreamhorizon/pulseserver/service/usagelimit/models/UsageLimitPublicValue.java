package org.dreamhorizon.pulseserver.service.usagelimit.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simplified usage limit value for public API responses.
 * Contains only displayName, windowType, and value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageLimitPublicValue {
  private String displayName;
  private String windowType;
  private Long value;
}

