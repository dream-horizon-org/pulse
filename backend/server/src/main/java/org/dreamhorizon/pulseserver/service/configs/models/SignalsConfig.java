package org.dreamhorizon.pulseserver.service.configs.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@NotNull
public class SignalsConfig {
  private int scheduleDurationMs;

  private String collectorUrl;

  private List<String> attributesToDrop;
}
