package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One evidence row shown in the side-panel's "View examples" drill-in. The UI uses
 * these fields to build deep links into session replay ({@code sessionId}), trace
 * waterfall ({@code traceId}), and screen-scoped filters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelDropoffEvidenceDto {

  private String sessionId;

  private String userId;

  /** ISO-8601 UTC timestamp the cohort row recorded as the last-reached moment. */
  private String lastReachedAt;

  /** Trace that covers the dropper's final step, if known. May be null/empty. */
  private String traceId;

  /** Screen the user was on when the drop-off was recorded. */
  private String screen;

  private String appVersion;

  private String platform;
}
