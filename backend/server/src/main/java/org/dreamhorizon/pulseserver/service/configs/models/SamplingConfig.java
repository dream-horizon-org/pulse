package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SamplingConfig {
  private DefaultSampling defaultSampling;

  private List<SamplingRule> rules;

  private CriticalEventPolicies criticalEventPolicies;

}
