package in.horizonos.pulseserver.resources.interaction.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GetAllInteractionsResponse {
  List<AllInteractionDetail> interactions;
  Integer totalInteractions;
}
