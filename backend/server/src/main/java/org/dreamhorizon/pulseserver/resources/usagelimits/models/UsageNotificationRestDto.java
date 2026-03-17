package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST DTO for a single usage notification.
 * Contains details for both sessions and events metrics.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageNotificationRestDto {
  
  @JsonProperty("projectId")
  private String projectId;

  @JsonProperty("projectName")
  private String projectName;

  @JsonProperty("threshold")
  private Integer threshold;
  
  @JsonProperty("notifyFor")
  private String notifyFor;
  
  @JsonProperty("templateName")
  private String templateName;
  
  @JsonProperty("sessionsUsed")
  private Long sessionsUsed;
  
  @JsonProperty("sessionsLimit")
  private Long sessionsLimit;
  
  @JsonProperty("sessionsPercentage")
  private Integer sessionsPercentage;
  
  @JsonProperty("sessionsOverage")
  private Integer sessionsOverage;
  
  @JsonProperty("sessionsBlocked")
  private Boolean sessionsBlocked;
  
  @JsonProperty("sessionsAtLimit")
  private Boolean sessionsAtLimit;
  
  @JsonProperty("eventsUsed")
  private Long eventsUsed;
  
  @JsonProperty("eventsLimit")
  private Long eventsLimit;
  
  @JsonProperty("eventsPercentage")
  private Integer eventsPercentage;
  
  @JsonProperty("eventsOverage")
  private Integer eventsOverage;
  
  @JsonProperty("eventsBlocked")
  private Boolean eventsBlocked;
  
  @JsonProperty("eventsAtLimit")
  private Boolean eventsAtLimit;
}
