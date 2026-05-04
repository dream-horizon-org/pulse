package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Heatmap filter wire shape aligned with UI / {@code RcaRelatedHeatmapsMerger} output. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class RcaHeatmapFiltersWire {

  private String breakpoint;

  private String platform;

  @JsonProperty("app_version")
  private String appVersion;

  @JsonProperty("geographical_region")
  private String geographicalRegion;

  @JsonProperty("from_date")
  private String fromDate;

  @JsonProperty("to_date")
  private String toDate;
}
