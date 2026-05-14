package org.dreamhorizon.pulseserver.resources.heatmap.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapLayersRestDto {

  @JsonProperty("glow_map")
  private List<HeatmapPointRestDto> glowMap;

  @JsonProperty("frustration_map")
  private HeatmapFrustrationRestDto frustrationMap;

  @JsonProperty("below_fold_metrics")
  private HeatmapBelowFoldMetricsRestDto belowFoldMetrics;
}