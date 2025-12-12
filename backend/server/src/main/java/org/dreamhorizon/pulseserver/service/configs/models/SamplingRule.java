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
public class SamplingRule {
  @JsonProperty("name")
  private rules name;

  @JsonProperty("sdks")
  private List<Sdk> sdks;

  @JsonProperty("value")
  private String value;

  @JsonProperty("sessionSampleRate")
  private double sessionSampleRate;
}
