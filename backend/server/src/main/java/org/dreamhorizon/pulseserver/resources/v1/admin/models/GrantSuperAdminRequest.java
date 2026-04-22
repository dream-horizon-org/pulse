package org.dreamhorizon.pulseserver.resources.v1.admin.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GrantSuperAdminRequest {

  @JsonProperty("userId")
  private String userId;
}
