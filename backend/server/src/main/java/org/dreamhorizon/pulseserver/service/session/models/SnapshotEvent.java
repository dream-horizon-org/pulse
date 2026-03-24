package org.dreamhorizon.pulseserver.service.session.models;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotEvent {
  private long timestamp;
  private int type;
  private Map<String, Object> data;
}
