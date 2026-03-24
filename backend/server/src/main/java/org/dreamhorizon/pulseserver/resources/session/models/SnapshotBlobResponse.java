package org.dreamhorizon.pulseserver.resources.session.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.session.models.SnapshotEvent;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotBlobResponse {
  private List<SnapshotEvent> snapshots;
}
