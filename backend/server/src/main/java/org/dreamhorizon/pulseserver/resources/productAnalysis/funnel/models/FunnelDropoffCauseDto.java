package org.dreamhorizon.pulseserver.resources.productAnalysis.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One cause row as shown in the drop-off side-panel. {@code lift} is the
 * dropper-affected rate divided by the converter-affected rate (how much more
 * likely a dropper was to experience this signal than a converter).
 *
 * <p>{@code exampleSessionIds} is capped on the backend (typically 5) and is the
 * input list the UI hands back to {@code GET .../evidence} to fill the drill-in.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelDropoffCauseDto {

  /** crash | anr | non_fatal | http_5xx | http_4xx | frozen_frame. */
  private String causeKind;

  /** Stable dedupe key — e.g. {@code NullPointerException@CheckoutScreen}. */
  private String causeKey;

  /** Human-friendly label for UI display. */
  private String causeLabel;

  /** Sessions/users that dropped at this step (denominator, dropper side). */
  private long dropoffCohort;

  /** Droppers that experienced this cause within the attribution window. */
  private long dropoffAffected;

  /** Sessions/users that converted (denominator, converter side). */
  private long converterCohort;

  /** Converters that also experienced this cause. */
  private long converterAffected;

  /** Attribution lift. {@code 999.0} means "only droppers saw this cause". */
  private double lift;

  /** Percentage of the dropper cohort hit by this cause ({@code dropoffAffected / dropoffCohort}). */
  private double dropoffRate;

  /** Example session IDs — used by the UI for the evidence drill-in request. */
  private List<String> exampleSessionIds;
}
