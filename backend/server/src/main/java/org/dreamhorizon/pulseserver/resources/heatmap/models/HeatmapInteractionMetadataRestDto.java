package org.dreamhorizon.pulseserver.resources.heatmap.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One interaction's average Apdex on a screen, for {@code metadata.interactions_metadata}.
 * Also used to deserialize ClickHouse rows (aliases must match the SELECT).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeatmapInteractionMetadataRestDto {

  @JsonProperty("interaction_name")
  private String interactionName;

  @JsonProperty("avg_score")
  private Double avgScore;
}
