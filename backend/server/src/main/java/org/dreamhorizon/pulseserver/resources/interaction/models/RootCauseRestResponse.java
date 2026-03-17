package org.dreamhorizon.pulseserver.resources.interaction.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RootCauseRestResponse {

  private Map<String, Object> baseline;
  private List<RootCauseSegmentRest> segments;
  private String mode;
  private Instant cachedAt;
  private Boolean everythingGood;
  private Boolean noDataAvailable;
  private String message;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RootCauseSegmentRest {
    private String label;
    private Map<String, String> dimensions;
    private Map<String, Object> metrics;
    private Map<String, Double> deltas;
  }
}
