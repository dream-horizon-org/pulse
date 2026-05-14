package org.dreamhorizon.pulseserver.resources.webvitals;

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
public class ScreenVitalDto {

  @JsonProperty("screen_name")
  private String screenName;

  private Double p75;

  @JsonProperty("total_count")
  private Long totalCount;

  @JsonProperty("good_pct")
  private Double goodPct;
}
