package org.dreamhorizon.pulseserver.dao.webvitals.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebVitalByScreenRow {

  @JsonProperty("screen_name")
  private String screenName;

  private String p75;

  @JsonProperty("total_count")
  private String totalCount;

  @JsonProperty("good_pct")
  private String goodPct;
}
