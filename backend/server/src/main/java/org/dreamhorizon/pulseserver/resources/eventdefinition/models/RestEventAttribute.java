package org.dreamhorizon.pulseserver.resources.eventdefinition.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class RestEventAttribute {
  private Long id;
  private String attributeName;
  private String description;
  private String dataType;
  private Boolean isRequired;
  private Boolean isArchived;
}
