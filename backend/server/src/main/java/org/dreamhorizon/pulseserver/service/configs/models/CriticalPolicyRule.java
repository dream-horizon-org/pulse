package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class CriticalPolicyRule {
  @JsonProperty("name")
  private String name;

  @JsonProperty("props")
  private List<EventPropMatch> props;

  @JsonProperty("scopes")
  private List<Scope> scopes;

  @JsonProperty("sdks")
  private List<Sdk> sdks;
}
