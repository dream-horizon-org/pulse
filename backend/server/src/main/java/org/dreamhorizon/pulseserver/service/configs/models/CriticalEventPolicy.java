package org.dreamhorizon.pulseserver.service.configs.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.dreamhorizon.pulseserver.service.configs.models.Scope;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CriticalEventPolicy {
  private String name;

  private List<EventPropMatch> props;

  private List<Scope> scope;
}
