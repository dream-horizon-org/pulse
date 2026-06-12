package org.dreamhorizon.pulseserver.resources.tiers.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Simplified tier list response for public endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierPublicListRestResponse {
  private List<TierPublicRestResponse> tiers;
  private Integer totalCount;
}

