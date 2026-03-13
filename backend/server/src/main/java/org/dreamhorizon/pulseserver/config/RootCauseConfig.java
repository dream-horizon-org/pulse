package org.dreamhorizon.pulseserver.config;

import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Singleton
public class RootCauseConfig {
  /** Minimum share of total problematic (error OR poor) that a single dimension value must reach to enter hierarchical mode. Default 75. */
  private int firstDimensionThresholdPct = 75;
  /** When adding further dimensions, minimum share of total problematic that a sub-segment must maintain. Default 75. */
  private int addDimensionThresholdPct = 75;
  /** Lookback window in days for querying otel_traces. Default 7. */
  private int lookbackDays = 7;
  /** Cache TTL in hours; entries older than this are considered expired. Default 24. */
  private int cacheTtlHours = 24;
}
