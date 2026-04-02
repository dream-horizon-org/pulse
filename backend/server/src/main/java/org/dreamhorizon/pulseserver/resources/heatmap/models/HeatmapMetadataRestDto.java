package org.dreamhorizon.pulseserver.resources.heatmap.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

  @JsonProperty("total_events")
  private long totalEvents;

  @JsonProperty("app_version")
  private String appVersion;

  private String platform;

  /** Present only when the client passes the {@code breakpoint} query parameter. */
  private String breakpoint;

  @JsonProperty("geographical_region")
  private String geographicalRegion;

  @JsonProperty("from_date")
  private String fromDate;

  @JsonProperty("to_date")
  private String toDate;
}
