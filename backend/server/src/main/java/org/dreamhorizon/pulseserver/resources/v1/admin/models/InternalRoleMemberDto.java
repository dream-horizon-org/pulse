package org.dreamhorizon.pulseserver.resources.v1.admin.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

/** One system-role holder for admin list APIs (superadmin / internal_viewer). */
@Value
@Builder
public class InternalRoleMemberDto {

  @JsonProperty("userId")
  String userId;

  /** Null if the user id has no row in Pulse DB (orphan FGA tuple). */
  @JsonProperty("email")
  String email;
}
