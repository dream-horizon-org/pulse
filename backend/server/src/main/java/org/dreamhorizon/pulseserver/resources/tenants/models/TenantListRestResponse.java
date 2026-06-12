package org.dreamhorizon.pulseserver.resources.tenants.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantListRestResponse {
  private List<TenantRestResponse> tenants;
  private Integer totalCount;
}
