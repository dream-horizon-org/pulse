package org.dreamhorizon.pulseserver.service.errorattribution;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
public class ErrorAttributionDrillDownResult {

  public static final String TEMPORAL_RULE_ISSUE_BEFORE_POOR = "issue_ts_before_poor_interaction_ts";

  private String signal;
  /** API contract: how rows were filtered (Poor-touch eligibility + U counts). */
  private String eligibility;
  /**
   * When set to {@value #TEMPORAL_RULE_ISSUE_BEFORE_POOR}, issue timestamps must precede earliest
   * Poor interaction in window for {@code n_treated_low}. Omitted / {@code null} when off.
   */
  private String temporalRule;
  private List<IssueRow> issues;
  private List<NetworkEndpointRow> networkEndpoints;

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class IssueRow {
    private String groupId;
    private String title;
    /** Distinct sessions in {@code U} with this issue (same as {@code nTreated}). */
    private Long occurrences;
    private String exceptionType;
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

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @JsonIgnoreProperties(ignoreUnknown = true)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class NetworkEndpointRow {
    private String url;
    private String graphqlOperationName;
    private String graphqlOperationType;
    /** From {@code SpanAttributes['http.request.method']} or {@code SpanAttributes['http.method']}. */
    private String httpMethod;
    /** From {@code SpanAttributes['http.response.status_code']} or {@code SpanAttributes['http.status_code']}. */
    private String httpStatusCode;
    /** Distinct sessions in {@code U} with this endpoint key (same as {@code nTreated}). */
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
