package org.dreamhorizon.pulseserver.service.interaction.models;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@RequiredArgsConstructor
public class DeleteInteractionRequest {
  @NotBlank(message = "Interaction name cannot be blank")
  String name;

  @NotBlank(message = "user email cannot be blank")
  String userEmail;
}