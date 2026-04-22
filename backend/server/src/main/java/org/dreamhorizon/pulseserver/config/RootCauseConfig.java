package org.dreamhorizon.pulseserver.config;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RootCauseConfig {

  /** Default similarity threshold (%). Segment must have at least this % of total problematic to be "similar." */
  public static final int DEFAULT_SIMILARITY_THRESHOLD_PCT = 75;
  /** Default lookback window in days for querying otel_traces. */
  public static final int DEFAULT_LOOKBACK_DAYS = 7;
  /** Default maximum segments in the result (hierarchy + flat combined). */
  public static final int DEFAULT_MAX_SEGMENTS = 4;
  /**
   * When true, RCA pre-computes max problematic count per dimension and reorders dimensions (strong
   * signals first) before segmentation. Default off for gradual rollout.
   */
  public static final boolean DEFAULT_HYBRID_DIMENSION_ORDERING_ENABLED = false;
  /** Default dimension order for tie-breaking and flat segments. */
  public static final List<String> DEFAULT_DIMENSION_ORDER = List.of(
      "Platform", "OsVersion", "AppVersion", "DeviceModel", "NetworkProvider", "GeoState");

  private int similarityThresholdPct;
  private int lookbackDays;
  private int maxSegments;
  private boolean hybridDimensionOrderingEnabled;
  private List<String> dimensionOrder;

  /**
   * Creates a config with defaults applied for any unset (zero or null) values.
   *
   * @param from the source config, may have zero/null values
   * @return a new config with defaults applied
   */
  public static RootCauseConfig withDefaults(RootCauseConfig from) {
    final boolean isFromNull = from == null;
    if (isFromNull) {
      return RootCauseConfig.builder()
          .similarityThresholdPct(DEFAULT_SIMILARITY_THRESHOLD_PCT)
          .lookbackDays(DEFAULT_LOOKBACK_DAYS)
          .maxSegments(DEFAULT_MAX_SEGMENTS)
          .hybridDimensionOrderingEnabled(DEFAULT_HYBRID_DIMENSION_ORDERING_ENABLED)
          .dimensionOrder(DEFAULT_DIMENSION_ORDER)
          .build();
    }

    final int similarityThresholdPct = from.similarityThresholdPct <= 0
        ? DEFAULT_SIMILARITY_THRESHOLD_PCT
        : from.similarityThresholdPct;
    final int lookbackDays = from.lookbackDays <= 0
        ? DEFAULT_LOOKBACK_DAYS
        : from.lookbackDays;
    final int maxSegments = from.maxSegments <= 0
        ? DEFAULT_MAX_SEGMENTS
        : from.maxSegments;
    final List<String> dimensionOrder = (from.dimensionOrder == null || from.dimensionOrder.isEmpty())
        ? DEFAULT_DIMENSION_ORDER
        : from.dimensionOrder;

    return RootCauseConfig.builder()
        .similarityThresholdPct(similarityThresholdPct)
        .lookbackDays(lookbackDays)
        .maxSegments(maxSegments)
        .hybridDimensionOrderingEnabled(from.hybridDimensionOrderingEnabled)
        .dimensionOrder(dimensionOrder)
        .build();
  }
}
