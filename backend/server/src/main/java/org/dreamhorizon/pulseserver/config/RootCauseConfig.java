package org.dreamhorizon.pulseserver.config;

import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Singleton
public class RootCauseConfig {

  private static final List<String> DEFAULT_DIMENSION_ORDER = List.of(
      "Platform", "OsVersion", "AppVersion", "DeviceModel", "NetworkProvider", "GeoState");

  /** Similarity threshold (%). Segment must have at least this % of total problematic to be "similar." Default 75. */
  private int similarityThresholdPct = 75;
  /** Lookback window in days for querying otel_traces. Default 7. */
  private int lookbackDays = 7;
  /** Cache TTL in hours; entries older than this are considered expired. Default 24. */
  private int cacheTtlHours = 24;
  /** Maximum segments in the result (hierarchy + flat combined). Default 4. */
  private int maxSegments = 4;
  /** Dimension order for tie-breaking and flat segments. Default: Platform, OsVersion, AppVersion, DeviceModel, NetworkProvider, GeoState. */
  private List<String> dimensionOrder;

  /**
   * Returns a config with zero/unset values replaced by defaults.
   * If {@code from} is null, returns a fully default instance.
   */
  public static RootCauseConfig withDefaults(RootCauseConfig from) {
    if (from == null) {
      return RootCauseConfig.builder()
          .similarityThresholdPct(75)
          .lookbackDays(7)
          .cacheTtlHours(24)
          .maxSegments(4)
          .dimensionOrder(DEFAULT_DIMENSION_ORDER)
          .build();
    }
    int similarityThresholdPct = from.similarityThresholdPct <= 0 ? 75 : from.similarityThresholdPct;
    int lookbackDays = from.lookbackDays <= 0 ? 7 : from.lookbackDays;
    int cacheTtlHours = from.cacheTtlHours <= 0 ? 24 : from.cacheTtlHours;
    int maxSegments = from.maxSegments <= 0 ? 4 : from.maxSegments;
    List<String> dimensionOrder = (from.dimensionOrder == null || from.dimensionOrder.isEmpty())
        ? DEFAULT_DIMENSION_ORDER
        : from.dimensionOrder;
    return RootCauseConfig.builder()
        .similarityThresholdPct(similarityThresholdPct)
        .lookbackDays(lookbackDays)
        .cacheTtlHours(cacheTtlHours)
        .maxSegments(maxSegments)
        .dimensionOrder(dimensionOrder)
        .build();
  }
}
