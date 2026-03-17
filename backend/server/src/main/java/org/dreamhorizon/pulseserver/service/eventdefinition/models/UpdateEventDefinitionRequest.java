package org.dreamhorizon.pulseserver.service.eventdefinition.models;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdateEventDefinitionRequest {
  @NotNull(message = "Event definition id cannot be null")
  private Long id;
  private String displayName;
  private String description;
  private String category;
  private List<EventAttributeDefinition> attributes;
  private String user;
}
