package org.dreamhorizon.pulseserver.resources.heatmap.models;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class HeatmapMetadataRestDto {

  @JsonProperty("screen_name")
  private String screenName;

  /** Presigned or public URLs for screen reference images; order is significant for the UI. */
  @JsonProperty("screenshot_urls")
  private List<String> screenshotUrls;

  @JsonProperty("total_events")
  private long totalEvents;

  @JsonProperty("from_date")
  private String fromDate;

  @JsonProperty("to_date")
  private String toDate;
}
