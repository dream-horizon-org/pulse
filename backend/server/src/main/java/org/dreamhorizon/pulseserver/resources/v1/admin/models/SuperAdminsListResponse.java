package org.dreamhorizon.pulseserver.resources.v1.admin.models;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SuperAdminsListResponse {
  /** FGA user ids (sorted); retained for older clients. */
  List<String> userIds;

  /** Enriched rows sorted by email (nulls last) then user id. */
  List<InternalRoleMemberDto> members;
}
