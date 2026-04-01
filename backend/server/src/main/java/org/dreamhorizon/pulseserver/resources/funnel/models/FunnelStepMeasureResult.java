package org.dreamhorizon.pulseserver.resources.funnel.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One funnel step for the saved-funnel visualization — matches UI {@code FunnelStepResult}
 * (step breakdown table + bar chart).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class FunnelStepMeasureResult {

  private String stepName;

  private long count;

  /** Conversion from first step to this step (%), same semantics as Spark {@code conversion_pct}. */
  private double conversionRate;

  /** Drop-off from previous step (% of users), {@code 0} on first step. */
  private double dropoffRate;
}
