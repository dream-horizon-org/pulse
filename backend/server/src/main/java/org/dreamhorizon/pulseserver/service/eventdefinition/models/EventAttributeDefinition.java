package org.dreamhorizon.pulseserver.service.eventdefinition.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class EventAttributeDefinition {
  private Long id;
  private Long eventDefinitionId;
  private String attributeName;
  private String description;
  private String dataType;
  private boolean required;
  private boolean archived;
}
