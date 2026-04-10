package org.dreamhorizon.pulseserver.service.eventdefinition.models;

import java.sql.Timestamp;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class EventDefinition {
  private Long id;
  private String projectId;
  private String eventName;
  private String displayName;
  private String description;
  private String category;
  private boolean archived;
  private String createdBy;
  private String updatedBy;
  private Timestamp createdAt;
  private Timestamp updatedAt;
  private List<EventAttributeDefinition> attributes;
}
