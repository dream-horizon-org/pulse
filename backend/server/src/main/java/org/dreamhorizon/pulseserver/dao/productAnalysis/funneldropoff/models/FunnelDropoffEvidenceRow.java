package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One example-session row for evidence drill-in from the drop-off panel.
 * Each row corresponds to a single session ID that exhibited a given cause,
 * with enough context (timestamp, trace, screen) for the UI to build deep links
 * into session replay, trace waterfall, and the stack-trace group view.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelDropoffEvidenceRow {

  @JsonProperty("sessionId")
  private String sessionId;

  @JsonProperty("userId")
  private String userId;

  @JsonProperty("lastReachedAt")
  private String lastReachedAt;

  @JsonProperty("traceId")
  private String traceId;

  @JsonProperty("screen")
  private String screen;

  @JsonProperty("appVersion")
  private String appVersion;

  @JsonProperty("platform")
  private String platform;
}
