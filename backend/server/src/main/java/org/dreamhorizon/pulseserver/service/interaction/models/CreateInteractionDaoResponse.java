package org.dreamhorizon.pulseserver.service.interaction.models;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateInteractionDaoResponse {
  InteractionDetails interactionDetails;
  InteractionDetailUploadMetadata interactionDetailUploadMetadata;
}
