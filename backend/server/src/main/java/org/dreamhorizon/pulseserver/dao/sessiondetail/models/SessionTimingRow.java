package org.dreamhorizon.pulseserver.dao.sessiondetail.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SessionTimingRow {
  @JsonProperty("session_id")
  private String sessionId;
  @JsonProperty("session_start")
  private String sessionStart;
  @JsonProperty("session_end")
  private String sessionEnd;
  @JsonProperty("durationMs")
  private long durationMs;
}
