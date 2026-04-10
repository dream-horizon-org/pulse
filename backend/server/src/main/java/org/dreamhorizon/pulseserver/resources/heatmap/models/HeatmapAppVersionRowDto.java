package org.dreamhorizon.pulseserver.resources.heatmap.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Row from {@link org.dreamhorizon.pulseserver.dao.heatmap.HeatmapQueries#DISTINCT_APP_VERSIONS_IN_SLICE}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HeatmapAppVersionRowDto {

  @JsonProperty("app_version")
  private String appVersion;
}
