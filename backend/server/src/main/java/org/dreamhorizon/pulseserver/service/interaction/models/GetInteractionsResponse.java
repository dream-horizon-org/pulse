package org.dreamhorizon.pulseserver.service.interaction.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GetInteractionsResponse {
  List<InteractionDetails> interactions;
  Integer totalInteractions;
}
