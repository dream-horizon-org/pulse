package org.dreamhorizon.pulseserver.service.session.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockCoordinates {
  private String bucket;
  private String key;
  private long startByte;
  private long endByte;
}
