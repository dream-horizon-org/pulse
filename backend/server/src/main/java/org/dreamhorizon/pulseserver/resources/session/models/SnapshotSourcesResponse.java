package org.dreamhorizon.pulseserver.resources.session.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotSourcesResponse {
  private String sessionId;
  private String snapshotSource;
  private List<BlockSource> sources;
}
