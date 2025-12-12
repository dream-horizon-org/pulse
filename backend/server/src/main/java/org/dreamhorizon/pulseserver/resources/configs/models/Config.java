package org.dreamhorizon.pulseserver.resources.configs.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Config {
  private long version;
  private PulseConfig configData;
}
