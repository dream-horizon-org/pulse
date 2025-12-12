package org.dreamhorizon.pulseserver.resources.alert.models;

import org.dreamhorizon.pulseserver.service.alert.core.models.AlertScope;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SubjectDto {
  @NotNull(message = "scope cannot be null")
  @JsonProperty("scope")
  AlertScope scope;

  @NotNull(message = "identifiers cannot be null")
  @JsonProperty("dimension_filters")
  List<String> dimensionFilters;
}

