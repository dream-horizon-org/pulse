package in.horizonos.pulseserver.resources.interaction.models;

import in.horizonos.pulseserver.service.interaction.models.Event;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AllInteractionDetail {
  private Long id;

  private String name;

  private String description;

  private Integer uptimeLowerLimitInMs;

  private Integer uptimeMidLimitInMs;

  private Integer uptimeUpperLimitInMs;

  private Integer thresholdInMs;

  private List<Event> events;

  private List<Event> globalBlacklistedEvents = List.of();
}