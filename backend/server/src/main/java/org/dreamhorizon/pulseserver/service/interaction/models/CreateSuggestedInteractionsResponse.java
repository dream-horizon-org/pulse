package org.dreamhorizon.pulseserver.service.interaction.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSuggestedInteractionsResponse {
  private int createdCount;
  private int replacedPendingCount;
}
