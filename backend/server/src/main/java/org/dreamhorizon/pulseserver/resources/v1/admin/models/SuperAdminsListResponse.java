package org.dreamhorizon.pulseserver.resources.v1.admin.models;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SuperAdminsListResponse {
  List<String> userIds;
}
