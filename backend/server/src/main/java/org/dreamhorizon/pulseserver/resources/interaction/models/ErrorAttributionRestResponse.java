package org.dreamhorizon.pulseserver.resources.interaction.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorAttributionRestResponse {

  private Boolean trackBInsufficientData;
  private Long nPoorInU;
  private Long nU;
  private List<RiskRatioEntry> riskRatios;
  private List<String> jointWinners;
  private String analysisPhase;
  private String track;
  private String diagnosticSpecVersion;
  private Instant cachedAt;
  private String disclaimer;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class RiskRatioEntry {
    private String signal;
    private Long nTreated;
    private Long nControl;
    private Long nTreatedLow;
    private Long nControlLow;
    private Double p1;
    private Double p2;
    private Double rr;
    private Boolean rrUndefined;
    private String rrUndefinedReason;
  }
}
