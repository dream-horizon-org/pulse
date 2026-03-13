package org.dreamhorizon.pulseserver.service.rootcause;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/** Request for building and running a root-cause metrics query against otel_traces. */
@Data
@Builder
public class RootCauseQueryRequest {
  private String projectId;
  private String interactionName;
  private Instant startTime;
  private Instant endTime;
  /** Metric keys to include (from RootCauseMetricsRegistry.METRIC_KEYS). */
  private List<String> metricKeys;
  /** Dimension keys for GROUP BY (e.g. ["Platform", "AppVersion"]). Empty for baseline. */
  private List<String> groupByDimensions;
  /** Dimension filters: dimension key -> value (e.g. Platform -> android). Applied as AND. */
  private Map<String, String> dimensionFilters;
  /** Max rows when grouping (default 1000). */
  @Builder.Default
  private int limit = 1000;
}
