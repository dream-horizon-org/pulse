package org.dreamhorizon.pulseserver.resources.screen.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.rootcause.models.ScreenRcaProblemResult;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenRcaV2Response {
  private List<ScreenRcaProblemResult> problems;
  private String summary;
  private Long timestamp;
}
