package org.dreamhorizon.pulseserver.resources.usagelimits.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * REST response containing list of usage notifications that need to be sent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageNotificationRestResponse {
  
  @JsonProperty("notifications")
  private List<UsageNotificationRestDto> notifications;
  
  @JsonProperty("totalProjectsChecked")
  private Integer totalProjectsChecked;
  
  @JsonProperty("notificationsDue")
  private Integer notificationsDue;
  
  @JsonProperty("checkedAt")
  private String checkedAt;
}
