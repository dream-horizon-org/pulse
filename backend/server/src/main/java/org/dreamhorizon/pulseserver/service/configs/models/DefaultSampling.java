package org.dreamhorizon.pulseserver.service.configs.models;

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
public class DefaultSampling {
  @JsonProperty("sessionSampleRate")
  private double sessionSampleRate;
}
