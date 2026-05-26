package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for the drop-off side-panel. Carries the ranked cause list plus the
 * step context needed by the UI (so the panel can title itself without a
 * second round-trip to fetch funnel metadata).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelDropoffResponse {

  private long funnelId;

  /** Zero-based index of the step whose drop-off this payload explains. */
  private int stepIndex;

  /** Display name of that step, mirrored from the funnel definition. */
  private String stepName;

  /** Funnel mode — {@code UNIQUE_USERS} or {@code SESSIONS}. Drives cohort semantics. */
  private String mode;

  /** Size of the dropper cohort (same value appears on every cause row). */
  private long dropoffCohort;

  /** Size of the converter cohort (same value appears on every cause row). */
  private long converterCohort;

  /** Ranked causes, lift-descending, capped at 50 by the DAO. */
  private List<FunnelDropoffCauseDto> causes;
}
