package org.dreamhorizon.pulseserver.service.configs.models;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@NotNull
public class CriticalSessionPolicies {
  private List<CriticalPolicyRule> alwaysSend;
}
