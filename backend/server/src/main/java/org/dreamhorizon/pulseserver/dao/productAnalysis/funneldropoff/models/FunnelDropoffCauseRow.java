package org.dreamhorizon.pulseserver.dao.productAnalysis.funneldropoff.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One (step × cause) row returned by the drop-off correlation query.
 *
 * <p>Whether the denominator ({@code dropoffCohort} / {@code converterCohort})
 * counts sessions or users depends on the funnel's {@code mode} — the DAO
 * chooses the right bridge table and propagates the counts verbatim.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelDropoffCauseRow {

  @JsonProperty("causeKind")
  private String causeKind;

  @JsonProperty("causeKey")
  private String causeKey;

  @JsonProperty("causeLabel")
  private String causeLabel;

  @JsonProperty("dropoffCohort")
  private Long dropoffCohort;

  @JsonProperty("dropoffAffected")
  private Long dropoffAffected;

  @JsonProperty("converterCohort")
  private Long converterCohort;

  @JsonProperty("converterAffected")
  private Long converterAffected;

  @JsonProperty("lift")
  private Double lift;

  @JsonProperty("exampleSessions")
  private String exampleSessions;
}
