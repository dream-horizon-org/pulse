package org.dreamhorizon.pulseserver.service.tnc;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.dao.tnc.models.TncVersion;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TncStatusResult {
  private boolean accepted;
  private TncVersion version;
  private String acceptedByEmail;
  private String acceptedAt;
}
