package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Screens + filters for RCA heatmap deep links (Pulse UI + enrichment for AI context). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RcaRelatedHeatmapsWire {

  private List<String> screens;

  @JsonProperty("heatmap_filters")
  private RcaHeatmapFiltersWire heatmapFilters;
}
