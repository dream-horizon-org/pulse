package org.dreamhorizon.pulseserver.service.session.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlockListing {
  private List<String> blockFirstTimestamps;
  private List<String> blockLastTimestamps;
  private List<String> blockUrls;
  private String snapshotSource;
}
