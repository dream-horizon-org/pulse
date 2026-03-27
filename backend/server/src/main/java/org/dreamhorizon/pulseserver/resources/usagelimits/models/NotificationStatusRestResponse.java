package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationStatusRestResponse {
  
  @JsonProperty("projectId")
  private String projectId;
  
  @JsonProperty("month")
  private String month;
  
  @JsonProperty("thresholdsNotified")
  private JsonNode thresholdsNotified;
  
  @JsonProperty("createdAt")
  private Instant createdAt;
  
  @JsonProperty("updatedAt")
  private Instant updatedAt;
}
