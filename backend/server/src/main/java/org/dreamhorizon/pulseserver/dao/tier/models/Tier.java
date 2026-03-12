package org.dreamhorizon.pulseserver.dao.tier.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tier {
  private Integer tierId;
  private String name;
  private String displayName;
  private Boolean isCustomLimitsAllowed;
  private String usageLimitDefaults; // JSON string
  private Boolean isActive;
  private Instant createdAt;
}
