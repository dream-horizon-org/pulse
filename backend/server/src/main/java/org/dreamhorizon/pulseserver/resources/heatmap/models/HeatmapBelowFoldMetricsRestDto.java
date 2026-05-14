package org.dreamhorizon.pulseserver.resources.heatmap.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeatmapBelowFoldMetricsRestDto {

  @JsonProperty("total_clicks")
  private Long totalClicks;

  @JsonProperty("total_click_bins")
  private Long totalClickBins;

  @JsonProperty("rage_taps")
  private Long rageTaps;

  @JsonProperty("rage_bins")
  private Long rageBins;

  @JsonProperty("dead_taps")
  private Long deadTaps;

  @JsonProperty("dead_bins")
  private Long deadBins;
}
