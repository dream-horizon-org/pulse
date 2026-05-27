package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreenRcaIssueSessionEvidence {
  private int rank;
  @JsonProperty("problem_type")
  private String problemType;
  private String segment;
  @JsonProperty("segment_filters")
  private Map<String, String> segmentFilters;
  @JsonProperty("session_id")
  private String sessionId;
}
