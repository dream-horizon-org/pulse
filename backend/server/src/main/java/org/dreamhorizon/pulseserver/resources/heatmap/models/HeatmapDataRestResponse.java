package org.dreamhorizon.pulseserver.resources.heatmap.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeatmapDataRestResponse {

  private HeatmapMetadataRestDto metadata;
  private HeatmapLayersRestDto layers;

  /** Sibling of {@code metadata} and {@code layers}; not nested under {@code metadata}. */
  @JsonProperty("interactions_metadata")
  private List<HeatmapInteractionMetadataRestDto> interactionsMetadata;
}