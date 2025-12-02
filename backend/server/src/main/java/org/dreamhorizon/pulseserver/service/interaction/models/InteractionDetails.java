package org.dreamhorizon.pulseserver.service.interaction.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class InteractionDetails {

  private Long id;

  @NotBlank(message = "Interaction name cannot be blank")
  private String name;

  @NotBlank(message = "Description cannot be blank")
  private String description;

  private @NotNull Integer uptimeLowerLimitInMs;
  private @NotNull Integer uptimeMidLimitInMs;
  private @NotNull Integer uptimeUpperLimitInMs;
  private @NotNull Integer thresholdInMs;
  private @NotNull InteractionStatus status;

  @NotNull(message = "Event sequence cannot be null")
  @Size(min = 2, message = "Event Sequence must have at least two element")
  private List<Event> events;

  @Builder.Default
  @NotNull(message = "Global blacklisted events cannot be null")
  private List<Event> globalBlacklistedEvents = List.of();

  private @NotNull Timestamp createdAt;
  private @NotNull String createdBy;
  private @NotNull Timestamp updatedAt;
  private @NotNull String updatedBy;
}