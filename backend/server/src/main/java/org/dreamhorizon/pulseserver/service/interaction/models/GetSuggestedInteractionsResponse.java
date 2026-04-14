package org.dreamhorizon.pulseserver.service.interaction.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetSuggestedInteractionsResponse {
  private List<SuggestedInteractionDetails> suggestions;
  private Integer totalSuggestions;
}
