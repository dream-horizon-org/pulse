package org.dreamhorizon.pulseserver.service.interaction.models;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSuggestedInteractionsRequest {
  @NotEmpty(message = "suggestions cannot be empty")
  @Valid
  private List<SuggestedInteractionCreateItem> suggestions;

  /**
   * When true, deletes existing PENDING suggestions for the project before inserting.
   */
  @Builder.Default
  private boolean replacePending = false;
}
