package org.dreamhorizon.pulseserver.resources.eventdefinition.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestEventDefinitionListResponse {
  private List<RestEventDefinition> eventDefinitions;
  private long totalCount;
}
