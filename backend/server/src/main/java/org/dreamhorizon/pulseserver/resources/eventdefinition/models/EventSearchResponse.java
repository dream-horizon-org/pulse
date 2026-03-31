package org.dreamhorizon.pulseserver.resources.eventdefinition.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventSearchResponse {
  private List<EventSearchItem> eventList;
  private int recordCount;

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class EventSearchItem {
    private EventMetadata metadata;
    private List<EventProperty> properties;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class EventMetadata {
    private String eventName;
    private String description;
    @Builder.Default
    private List<String> screenNames = Collections.emptyList();
    private boolean archived;
    @Builder.Default
    private boolean isActive = true;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class EventProperty {
    private String propertyName;
    private String description;
    private boolean archived;
  }
}
