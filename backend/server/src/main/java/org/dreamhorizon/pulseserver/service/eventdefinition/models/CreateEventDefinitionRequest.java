package org.dreamhorizon.pulseserver.service.eventdefinition.models;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreateEventDefinitionRequest {
  @NotBlank(message = "Event name cannot be blank")
  private String eventName;
  private String displayName;
  private String description;
  private String category;
  private List<EventAttributeDefinition> attributes;
  @NotBlank(message = "User cannot be blank")
  private String user;
}
