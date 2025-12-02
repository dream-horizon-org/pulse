package org.dreamhorizon.pulseserver.service.alert.core.models;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Subject {
  @NotNull
  AlertScope scope;

  @NotNull
  List<String> dimensionFilters;
}