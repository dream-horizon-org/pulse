package org.dreamhorizon.pulseserver.service.rootcause.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenRcaV2Response {
  private List<ScreenRcaProblemResult> problems;
  private ScreenRcaEvidences evidences;
}
