package org.dreamhorizon.pulseserver.service.configs.models;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreateConfigResponse {
  private long version;
}
