package org.dreamhorizon.pulseserver.service.interaction.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UpdateInteractionRequest {

  @NotNull(message = "Interaction name cannot be null")
  private String name;

  private String description;

  private Integer uptimeLowerLimitInMs;
  private Integer uptimeMidLimitInMs;
  private Integer uptimeUpperLimitInMs;
  private Integer interactionThresholdInMS;
  private InteractionStatus status;

  @Size(min = 2, message = "Event Sequence must have at least two element")
  private List<Event> events;

  private List<Event> globalBlacklistedEvents;

  @NotBlank(message = "User cannot be blank")
  private String user;
}