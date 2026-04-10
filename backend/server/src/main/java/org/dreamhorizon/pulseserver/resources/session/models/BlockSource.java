package org.dreamhorizon.pulseserver.resources.session.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockSource {
  private String source;
  private String blobKey;
  private String startTimestamp;
  private String endTimestamp;
}
