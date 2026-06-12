package org.dreamhorizon.pulseserver.service.interaction.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionDetailUploadMetadata {
  private Long id;
  private Long interactionId;
  private Status status;

  public enum Status {
    SUCCESS, PENDING, FAILED, SKIPPED
  }
}
