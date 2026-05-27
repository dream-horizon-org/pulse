package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenRcaEvidences {
  @JsonProperty("issue_sessions")
  private List<ScreenRcaIssueSessionEvidence> issueSessions;
  @JsonProperty("heatmap_available")
  private boolean heatmapAvailable;
  @JsonProperty("heatmap_date")
  private String heatmapDate;
}
