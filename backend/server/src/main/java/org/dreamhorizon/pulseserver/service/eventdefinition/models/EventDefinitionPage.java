package org.dreamhorizon.pulseserver.service.eventdefinition.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class EventDefinitionPage {
  private List<EventDefinition> definitions;
  private long totalCount;
}
