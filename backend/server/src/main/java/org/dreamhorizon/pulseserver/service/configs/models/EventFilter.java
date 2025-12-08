package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.configs.models.Scope;
import org.dreamhorizon.pulseserver.service.configs.models.Sdk;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EventFilter {
  @JsonProperty("name")
  private String name;

  @JsonProperty("props")
  private List<EventPropMatch> props;

  @JsonProperty("scope")
  private List<Scope> scope;

  @JsonProperty("sdks")
  private List<Sdk> sdks;
}
