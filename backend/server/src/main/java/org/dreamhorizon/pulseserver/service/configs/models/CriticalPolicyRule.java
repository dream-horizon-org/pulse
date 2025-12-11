package org.dreamhorizon.pulseserver.service.configs.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonProperty;

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

  @JsonProperty("scope")
  private List<Scope> scope;

  @JsonProperty("sdks")
  private List<Sdk> sdks;
}
