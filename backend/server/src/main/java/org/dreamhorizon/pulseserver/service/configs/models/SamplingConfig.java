package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import org.dreamhorizon.pulseserver.resources.configs.models.PulseConfig;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@NotNull
public class SamplingConfig {
  private DefaultSampling defaultSampling;

  private List<SamplingRule> rules;

  private CriticalEventPolicies criticalEventPolicies;

  private CriticalSessionPolicies criticalSessionPolicies;
}
