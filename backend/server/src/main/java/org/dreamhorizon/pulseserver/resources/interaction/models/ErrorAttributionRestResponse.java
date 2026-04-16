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
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorAttributionRestResponse {

  private Instant cachedAt;
  private String disclaimer;
  /**
   * Resolved {@code rootCause.minRiskRatioForIssueAttribution}; UI may show “RR ≥ …” when {@code > 1};
   * {@code [0,1]} means no RR floor.
   */
  private Double minRiskRatioForIssueAttribution;
  /** Merged list across signals after RR threshold + global cap. */
  private List<RelatedAttributionEntry> relatedAttributions;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class RelatedAttributionEntry {
    /** {@code crash}, {@code anr}, {@code non_fatal}, or {@code api}. */
    private String sourceSignal;
    /** {@code issue} or {@code api}. */
    private String rowKind;
    private String groupId;
    private String title;
    private String exceptionType;
    private String url;
    private String graphqlOperationName;
    private String graphqlOperationType;
    private Long occurrences;
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
