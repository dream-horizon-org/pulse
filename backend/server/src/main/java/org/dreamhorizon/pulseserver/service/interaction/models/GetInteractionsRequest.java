package org.dreamhorizon.pulseserver.service.interaction.models;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@RequiredArgsConstructor
public class GetInteractionsRequest {
  private @NotNull Integer page;
  private @NotNull Integer size;
  private String userEmail;
  private String name;
  private InteractionStatus status;
}