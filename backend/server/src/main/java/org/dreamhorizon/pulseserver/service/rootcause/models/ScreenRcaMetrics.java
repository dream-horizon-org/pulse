package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScreenRcaMetrics {
  @JsonProperty("affected_volume")
  private Long affectedVolume;
  private String rate;
  @JsonProperty("p50_ms")
  private Long p50Ms;
  @JsonProperty("p95_ms")
  private Long p95Ms;
  // bad_clicks only — raw counts from otel_logs
  @JsonProperty("click_volume")
  private Long clickVolume;
  @JsonProperty("rage_count")
  private Long rageCount;
  @JsonProperty("dead_count")
  private Long deadCount;
}
