package org.dreamhorizon.pulseserver.resources.v1.auth.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class TenantLookupResponseDto {
  @JsonProperty("gcpTenantId")
  private String gcpTenantId;

  @JsonProperty("tenantId")
  private String tenantId;

  @JsonProperty("tenantName")
  private String tenantName;
}

