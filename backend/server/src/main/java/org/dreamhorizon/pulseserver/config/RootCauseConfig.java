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
  public static final int DEFAULT_MAX_SEGMENTS = 5;
  /** Default minimum segment volume as percentage of baseline volume to be included in AI report. */
  public static final double DEFAULT_MIN_SEGMENT_VOLUME_PCT = 5.0;
  /**
   * When true, RCA pre-computes max problematic count per dimension and reorders dimensions (strong
   * signals first) before segmentation. Default off for gradual rollout.
   */
  public static final boolean DEFAULT_HYBRID_DIMENSION_ORDERING_ENABLED = false;
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
  /**
   * Per-signal ClickHouse row cap before merge / RR threshold filter; should be ≥ {@link
   * #DEFAULT_ISSUE_DRILL_DOWN_LIMIT}.
   */
  public static final int DEFAULT_ISSUE_DRILL_DOWN_CANDIDATE_LIMIT = 30;
  /**
   * Minimum finite RR for a drill-down row to appear in the merged “related” list. Values in {@code
   * [0, 1]} disable the RR floor at runtime. Use {@code < 0} in raw config (e.g. JSON key absent) so
   * {@link #withDefaults(RootCauseConfig)} applies this default; {@code 0.0} explicitly disables the
   * floor.
   */
  public static final double DEFAULT_MIN_RISK_RATIO_FOR_ISSUE_ATTRIBUTION = 2.0;
  /**
   * Minimum {@code n_treated / (n_treated + n_control)} (= share of universe {@code U}) for merged
   * error-attribution drill rows. Production default aligns with causal doc φ₀ = 0.05%. {@code <= 0}
   * disables the prevalence gate at runtime. Use {@link #withDefaults(RootCauseConfig)}: raw values
   * {@code < 0} become this default when unset in partial config builders.
   */
  public static final double DEFAULT_MIN_TREATED_PREVALENCE_FRACTION_IN_U = 0.001d;
  /**
   * When {@code true}, issue drill-down counts a session toward {@code n_treated_low} only if the
   * first issue (or first network error for {@code api}) occurs strictly before the earliest Poor
   * interaction span in the analysis window. Use {@link Boolean} wrapper so JSON can explicitly set
   * {@code false}; omitted / {@code null} in {@link #withDefaults(RootCauseConfig)} uses {@link
   * #DEFAULT_ISSUE_MUST_PRECEDE_POOR}.
   */
  public static final boolean DEFAULT_ISSUE_MUST_PRECEDE_POOR = true;
  /**
   * Default minimum combined delta signal {@code S = |Δerror_rate| + |Δpoor_user_pct|} a segment
   * must carry to remain in the cached / returned segment list. Set to {@code 0.0} (or any value
   * {@code <= 0}) to disable the gate; {@code < 0} in raw input is interpreted as "unset" and
   * replaced by this default in {@link #withDefaults(RootCauseConfig)}.
   */
  public static final double DEFAULT_MIN_COMBINED_DELTA_SIGNAL = 15.0;
  /** Default dimension order for tie-breaking and flat segments. */
  public static final List<String> DEFAULT_DIMENSION_ORDER = List.of(
      "Platform", "OsVersion", "AppVersion", "DeviceModel", "NetworkProvider", "GeoState");

  private int similarityThresholdPct;
  private int lookbackDays;
  private int maxSegments;
  private double minSegmentVolumePct;
  private boolean hybridDimensionOrderingEnabled;
  private int minPoorSessionsForErrorAttribution;
  /** Issue / endpoint drill-down: minimum {@code n_treated} in universe {@code U}. */
  private int minTreatedSessionsForIssueAttribution;
  /** Issue / endpoint drill-down: optional minimum {@code n_control}; {@code 0} = disabled. */
  private int minControlSessionsForIssueAttribution;
  /** Issue / endpoint drill-down: max rows per signal after ranking. */
  private int issueDrillDownLimit;
  /**
   * Per-signal SQL {@code LIMIT} before merge; {@code <= 0} in raw input → default in {@link
   * #withDefaults}. Builder default {@code 0} means “unset” before {@link #withDefaults}.
   */
  @Builder.Default
  private int issueDrillDownCandidateLimit = 0;
  /**
   * Minimum finite RR for merged related list; {@code < 0} in raw input → default in {@link
   * #withDefaults}; {@code [0, 1]} = RR floor off at runtime. Builder default {@code -1} = unset
   * before {@link #withDefaults}.
   */
  @Builder.Default
  private double minRiskRatioForIssueAttribution = -1.0d;
  /**
   * Track B prevalence floor: minimum {@code n_treated/n_u}; {@code 0} disables. Builder default
   * {@code -1} = unset before {@link #withDefaults(RootCauseConfig)}.
   */
  @Builder.Default
  private double minTreatedPrevalenceFractionInU = -1.0d;
  /**
   * Drill-down temporal guard (Variant A): {@code null} in raw config → {@link
   * #DEFAULT_ISSUE_MUST_PRECEDE_POOR} after {@link #withDefaults(RootCauseConfig)}; explicit {@code
   * false} disables; {@code true} enables.
   */
  private Boolean issueMustPrecedePoor;
  /**
   * Combined delta signal floor: drop pre-LLM segments where {@code |Δerror_rate| + |Δpoor_user_pct|
   * < minCombinedDeltaSignal}. Builder default {@code -1.0} = unset; {@link
   * #withDefaults(RootCauseConfig)} replaces values {@code < 0} with {@link
   * #DEFAULT_MIN_COMBINED_DELTA_SIGNAL}. {@code 0.0} preserved at runtime to disable the gate.
   */
  @Builder.Default
  private double minCombinedDeltaSignal = -1.0d;
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
          .minSegmentVolumePct(DEFAULT_MIN_SEGMENT_VOLUME_PCT)
          .hybridDimensionOrderingEnabled(DEFAULT_HYBRID_DIMENSION_ORDERING_ENABLED)
          .minPoorSessionsForErrorAttribution(DEFAULT_MIN_POOR_SESSIONS_FOR_ERROR_ATTRIBUTION)
          .minTreatedSessionsForIssueAttribution(DEFAULT_MIN_TREATED_SESSIONS_FOR_ISSUE_ATTRIBUTION)
          .minControlSessionsForIssueAttribution(DEFAULT_MIN_CONTROL_SESSIONS_FOR_ISSUE_ATTRIBUTION)
          .issueDrillDownLimit(DEFAULT_ISSUE_DRILL_DOWN_LIMIT)
          .issueDrillDownCandidateLimit(DEFAULT_ISSUE_DRILL_DOWN_CANDIDATE_LIMIT)
          .minRiskRatioForIssueAttribution(DEFAULT_MIN_RISK_RATIO_FOR_ISSUE_ATTRIBUTION)
          .minTreatedPrevalenceFractionInU(DEFAULT_MIN_TREATED_PREVALENCE_FRACTION_IN_U)
          .issueMustPrecedePoor(DEFAULT_ISSUE_MUST_PRECEDE_POOR)
          .minCombinedDeltaSignal(DEFAULT_MIN_COMBINED_DELTA_SIGNAL)
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
    final double minSegmentVolumePct = from.minSegmentVolumePct <= 0
        ? DEFAULT_MIN_SEGMENT_VOLUME_PCT
        : from.minSegmentVolumePct;
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
    final int issueDrillDownCandidateLimit =
        from.issueDrillDownCandidateLimit <= 0
            ? DEFAULT_ISSUE_DRILL_DOWN_CANDIDATE_LIMIT
            : from.issueDrillDownCandidateLimit;
    final double minRiskRatioForIssueAttribution =
        from.minRiskRatioForIssueAttribution < 0
            ? DEFAULT_MIN_RISK_RATIO_FOR_ISSUE_ATTRIBUTION
            : from.minRiskRatioForIssueAttribution;
    final double minTreatedPrevalenceFractionInU =
        from.minTreatedPrevalenceFractionInU < 0
            ? DEFAULT_MIN_TREATED_PREVALENCE_FRACTION_IN_U
            : from.minTreatedPrevalenceFractionInU;
    final boolean issueMustPrecedePoor =
        from.getIssueMustPrecedePoor() == null
            ? DEFAULT_ISSUE_MUST_PRECEDE_POOR
            : Boolean.TRUE.equals(from.getIssueMustPrecedePoor());
    final double minCombinedDeltaSignal =
        from.minCombinedDeltaSignal < 0
            ? DEFAULT_MIN_COMBINED_DELTA_SIGNAL
            : from.minCombinedDeltaSignal;
    final List<String> dimensionOrder = (from.dimensionOrder == null || from.dimensionOrder.isEmpty())
        ? DEFAULT_DIMENSION_ORDER
        : from.dimensionOrder;

    return RootCauseConfig.builder()
        .similarityThresholdPct(similarityThresholdPct)
        .lookbackDays(lookbackDays)
        .maxSegments(maxSegments)
        .minSegmentVolumePct(minSegmentVolumePct)
        .hybridDimensionOrderingEnabled(from.hybridDimensionOrderingEnabled)
        .minPoorSessionsForErrorAttribution(minPoorSessionsForErrorAttribution)
        .minTreatedSessionsForIssueAttribution(minTreatedSessionsForIssueAttribution)
        .minControlSessionsForIssueAttribution(minControlSessionsForIssueAttribution)
        .issueDrillDownLimit(issueDrillDownLimit)
        .issueDrillDownCandidateLimit(issueDrillDownCandidateLimit)
        .minRiskRatioForIssueAttribution(minRiskRatioForIssueAttribution)
        .minTreatedPrevalenceFractionInU(minTreatedPrevalenceFractionInU)
        .issueMustPrecedePoor(issueMustPrecedePoor)
        .minCombinedDeltaSignal(minCombinedDeltaSignal)
        .dimensionOrder(dimensionOrder)
        .build();
  }
}
