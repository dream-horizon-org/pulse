package org.dreamhorizon.pulseserver.service.interaction.models;

import java.sql.Timestamp;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedInteractionDetails {
  private Long id;
  private String projectId;
  private List<String> pattern;
  private Integer totalOccurrences;
  private Integer uniqueSessions;
  private Double sessionPct;
  private Double meanSpanS;
  private Double medianSpanS;
  private Double p95SpanS;
  private Double cv;
  private List<SuggestedInteractionEdge> edges;
  private String status;
  private Timestamp createdAt;
}
