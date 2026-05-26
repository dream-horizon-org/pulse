package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonInclude;
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
  private Long affectedVolume;
  private String rate;
  private Long p50Ms;
  private Long p95Ms;
  // bad_clicks only — raw counts from otel_logs
  private Long clickVolume;
  private Long rageCount;
  private Long deadCount;
}
