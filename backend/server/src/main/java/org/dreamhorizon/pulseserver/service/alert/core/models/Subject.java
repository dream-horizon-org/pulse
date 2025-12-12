package org.dreamhorizon.pulseserver.service.alert.core.models;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Subject {
  @NotNull
  AlertScope scope;

  @NotNull
  List<String> dimensionFilters;
}

