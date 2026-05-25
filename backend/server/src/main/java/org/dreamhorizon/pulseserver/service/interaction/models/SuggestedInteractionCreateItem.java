package org.dreamhorizon.pulseserver.service.interaction.models;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuggestedInteractionCreateItem {
  @NotEmpty(message = "events cannot be empty")
  @Valid
  private List<Event> events;

  @NotNull
  private Integer totalOccurrences;

  @NotNull
  private Integer uniqueSessions;

  @NotNull
  private Double sessionPct;

  @NotNull
  private Double meanSpanS;

  @NotNull
  private Double medianSpanS;

  @NotNull
  private Double p95SpanS;

  @NotNull
  private Double cv;

  private List<SuggestedInteractionEdge> edges;
}
