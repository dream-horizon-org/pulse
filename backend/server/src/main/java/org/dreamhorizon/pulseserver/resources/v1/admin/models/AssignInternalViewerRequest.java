package org.dreamhorizon.pulseserver.resources.v1.admin.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AssignInternalViewerRequest {

  @JsonProperty("userId")
  private String userId;

  /** Exactly one of {@code userId} or {@code email} must be set. */
  @JsonProperty("email")
  private String email;
}
