package org.dreamhorizon.pulseserver.dao.sessiondetail.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
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
public class SessionCoreRow {
  @JsonProperty("session_id")
  private String sessionId;
  @JsonProperty("user_id")
  private String userId;
  private String platform;
  private String device;
  private String osVersion;
  private String appVersion;
  @JsonProperty("session_start")
  private String sessionStart;
  @JsonProperty("session_end")
  private String sessionEnd;
  private long durationMs;
  private String geography;
  private double qualityScore;
  private List<String> journey;
}
