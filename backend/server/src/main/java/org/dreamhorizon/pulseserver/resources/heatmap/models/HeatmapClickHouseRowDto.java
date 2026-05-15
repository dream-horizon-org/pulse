package org.dreamhorizon.pulseserver.resources.heatmap.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeatmapClickHouseRowDto {

  @JsonProperty("xBin")
  private Double xBin;

  @JsonProperty("yBin")
  private Double yBin;

  @JsonProperty("weightNormal")
  private Long weightNormal;

  @JsonProperty("weightRage")
  private Long weightRage;

  @JsonProperty("weightDead")
  private Long weightDead;
}