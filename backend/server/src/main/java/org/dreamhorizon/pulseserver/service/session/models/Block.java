package org.dreamhorizon.pulseserver.service.session.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Block {
  private String firstTimestamp;
  private String lastTimestamp;
  private String blockUrl;
}
