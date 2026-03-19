package org.dreamhorizon.pulseserver.resources.incident.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SlackActionPayload {

  private String type;
  private User user;
  @JsonProperty("trigger_id")
  private String triggerId;
  private List<Action> actions;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class User {
    private String id;
    private String username;
    private String name;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Action {
    private String type;
    @JsonProperty("action_id")
    private String actionId;
    private String value;
  }
}
