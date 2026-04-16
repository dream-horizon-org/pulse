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
   * Minimum Poor sessions in the analysis window required before Track B emits joint winners and
   * clears {@code trackBInsufficientData}. Production default; override lower for local/dev demos.
   */
  public static final int DEFAULT_MIN_POOR_SESSIONS_FOR_ERROR_ATTRIBUTION = 1000;
  /**
   * Minimum treated sessions (sessions in {@code U} with the issue or endpoint) before a drill-down
   * row is returned; reduces noisy per-issue p1/p2/RR.
   */
  public static final int DEFAULT_MIN_TREATED_SESSIONS_FOR_ISSUE_ATTRIBUTION = 5;
  /**
   * When {@code > 0}, drill-down rows must also have at least this many control sessions (sessions in
   * {@code U} without the issue). {@code 0} disables the gate.
   */
  public static final int DEFAULT_MIN_CONTROL_SESSIONS_FOR_ISSUE_ATTRIBUTION = 0;
  /** Default cap on drill-down rows per signal (Mode A ranking). */
  public static final int DEFAULT_ISSUE_DRILL_DOWN_LIMIT = 5;
  /** Default dimension order for tie-breaking and flat segments. */
  public static final List<String> DEFAULT_DIMENSION_ORDER = List.of(
      "Platform", "OsVersion", "AppVersion", "DeviceModel", "NetworkProvider", "GeoState");

  private int similarityThresholdPct;
  private int lookbackDays;
  private int maxSegments;
  private int minPoorSessionsForErrorAttribution;
  /** Issue / endpoint drill-down: minimum {@code n_treated} in universe {@code U}. */
  private int minTreatedSessionsForIssueAttribution;
  /** Issue / endpoint drill-down: optional minimum {@code n_control}; {@code 0} = disabled. */
  private int minControlSessionsForIssueAttribution;
  /** Issue / endpoint drill-down: max rows per signal after ranking. */
  private int issueDrillDownLimit;
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
          .minPoorSessionsForErrorAttribution(DEFAULT_MIN_POOR_SESSIONS_FOR_ERROR_ATTRIBUTION)
          .minTreatedSessionsForIssueAttribution(DEFAULT_MIN_TREATED_SESSIONS_FOR_ISSUE_ATTRIBUTION)
          .minControlSessionsForIssueAttribution(DEFAULT_MIN_CONTROL_SESSIONS_FOR_ISSUE_ATTRIBUTION)
          .issueDrillDownLimit(DEFAULT_ISSUE_DRILL_DOWN_LIMIT)
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
    final int minPoorSessionsForErrorAttribution =
        from.minPoorSessionsForErrorAttribution <= 0
            ? DEFAULT_MIN_POOR_SESSIONS_FOR_ERROR_ATTRIBUTION
            : from.minPoorSessionsForErrorAttribution;
    final int minTreatedSessionsForIssueAttribution =
        from.minTreatedSessionsForIssueAttribution <= 0
            ? DEFAULT_MIN_TREATED_SESSIONS_FOR_ISSUE_ATTRIBUTION
            : from.minTreatedSessionsForIssueAttribution;
    final int minControlSessionsForIssueAttribution =
        from.minControlSessionsForIssueAttribution < 0
            ? DEFAULT_MIN_CONTROL_SESSIONS_FOR_ISSUE_ATTRIBUTION
            : from.minControlSessionsForIssueAttribution;
    final int issueDrillDownLimit =
        from.issueDrillDownLimit <= 0 ? DEFAULT_ISSUE_DRILL_DOWN_LIMIT : from.issueDrillDownLimit;
    final List<String> dimensionOrder = (from.dimensionOrder == null || from.dimensionOrder.isEmpty())
        ? DEFAULT_DIMENSION_ORDER
        : from.dimensionOrder;

    return RootCauseConfig.builder()
        .similarityThresholdPct(similarityThresholdPct)
        .lookbackDays(lookbackDays)
        .maxSegments(maxSegments)
        .minPoorSessionsForErrorAttribution(minPoorSessionsForErrorAttribution)
        .minTreatedSessionsForIssueAttribution(minTreatedSessionsForIssueAttribution)
        .minControlSessionsForIssueAttribution(minControlSessionsForIssueAttribution)
        .issueDrillDownLimit(issueDrillDownLimit)
        .dimensionOrder(dimensionOrder)
        .build();
  }
}
