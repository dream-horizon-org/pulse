package in.horizonos.pulseserver.service.interaction.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@RequiredArgsConstructor
public class CreateInteractionRequest {

  @NotBlank(message = "Interaction name cannot be blank")
  private String name;

  @NotBlank(message = "Description cannot be blank")
  private String description;

  private @NotNull Integer uptimeLowerLimitInMs;
  private @NotNull Integer uptimeMidLimitInMs;
  private @NotNull Integer uptimeUpperLimitInMs;
  private @NotNull Integer thresholdInMs;

  @Builder.Default
  @NotNull(message = "Event sequence cannot be null")
  @Size(min = 2, message = "Event Sequence must have at least two element")
  private List<Event> events = List.of();

  @Builder.Default
  @NotNull(message = "Global blacklisted events cannot be null")
  private List<Event> globalBlacklistedEvents = List.of();

  @NotBlank(message = "User cannot be blank")
  private String user;
}