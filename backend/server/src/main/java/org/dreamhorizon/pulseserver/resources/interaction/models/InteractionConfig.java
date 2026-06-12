package org.dreamhorizon.pulseserver.resources.interaction.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.interaction.models.Event;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InteractionConfig {
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