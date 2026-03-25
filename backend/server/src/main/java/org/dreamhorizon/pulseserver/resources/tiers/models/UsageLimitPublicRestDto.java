package org.dreamhorizon.pulseserver.resources.tiers.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Simplified usage limit for public API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageLimitPublicRestDto {
  private String displayName;
  private String windowType;
  private Long value;
}

