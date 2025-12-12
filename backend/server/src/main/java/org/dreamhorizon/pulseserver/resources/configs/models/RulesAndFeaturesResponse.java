package org.dreamhorizon.pulseserver.resources.configs.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RulesAndFeaturesResponse {
  private List<String> rules;
  private List<String> features;
}
