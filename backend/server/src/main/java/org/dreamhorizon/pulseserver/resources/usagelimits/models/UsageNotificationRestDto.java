package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * REST DTO for a single usage notification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageNotificationRestDto {
  
  @JsonProperty("projectId")
  private String projectId;
  
  @JsonProperty("metricType")
  private String metricType;
  
  @JsonProperty("threshold")
  private Integer threshold;
  
  @JsonProperty("percentage")
  private Integer percentage;
  
  @JsonProperty("currentUsage")
  private Long currentUsage;
  
  @JsonProperty("limit")
  private Long limit;
}
