package org.dreamhorizon.pulseserver.service.rootcause.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Result of root cause analysis: baseline, segments, mode, cache info, edge-case flags. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RootCauseResult {

  private Map<String, Object> baseline;
  private List<RootCauseSegment> segments;
  /** "hierarchical" | "flat" */
  private String mode;
  private Instant cachedAt;
  /** True when volume > 0 and total problematic count = 0. */
  private Boolean everythingGood;
  /** True when volume = 0 (no data in window). */
  private Boolean noDataAvailable;
  private String message;
}
