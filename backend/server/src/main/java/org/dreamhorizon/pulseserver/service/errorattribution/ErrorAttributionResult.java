package org.dreamhorizon.pulseserver.service.errorattribution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Internal Track B summary aggregate shape (risk ratios, joint winners). Used for tests and offline
 * validation via {@link ErrorAttributionSummaryEngine}; the REST error-attribution endpoint is drill-only
 * and does not persist this payload on {@code otel.root_cause_cache}.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorAttributionResult {

  /** Serialized as JSON string values for {@code rrUndefinedReason}. */
  public static final String RR_EMPTY_TREATED_ARM = "EMPTY_TREATED_ARM";
  public static final String RR_EMPTY_CONTROL_ARM = "EMPTY_CONTROL_ARM";
  public static final String RR_INFINITE_RR = "INFINITE_RR";
  public static final String RR_ZERO_POOR = "ZERO_POOR";

  private Boolean trackBInsufficientData;
  /** Minimum Poor sessions required for full Track B output; mirrors server {@code rootCause} config. */
  private Integer minPoorSessionsForErrorAttribution;
  private Long nPoorInU;
  private Long nU;
  private List<RiskRatioRow> riskRatios;
  private List<String> jointWinners;
  private String analysisPhase;
  private String track;
  private String diagnosticSpecVersion;
  private String disclaimer;
  private Instant cachedAt;

  @Data
  @Builder(toBuilder = true)
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class RiskRatioRow {
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
