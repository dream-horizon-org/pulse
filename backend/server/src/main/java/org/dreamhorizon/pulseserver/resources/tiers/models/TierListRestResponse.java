package org.dreamhorizon.pulseserver.resources.tiers.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * Full tier list response for  endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TierListRestResponse {
  private List<TierRestResponse> tiers;
  private Integer totalCount;
}

