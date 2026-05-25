import type { InteractionReportV1Wire } from "../../../../../hooks/useInteractionReport";

/** PaymentGateway-shaped sample for UI tests (matches pulse_ai fixture). */
export const mockInteractionReportV1: InteractionReportV1Wire = {
  version: 1,
  project_id: "proj-test",
  generated_at: "2026-05-24T12:00:00Z",
  identity: {
    name: "PaymentGatewayHandshakeLatency",
    business_moment: "User taps Pay → Juspay SDK handshake completes",
    start_event: "JUSPAY_INITIATE_REQUEST",
    end_event: "JUSPAY_INITIATE_RESULT_SUCCESS",
    thresholds: {
      excellent_ms: 316,
      good_ms: 1400,
      average_ms: 2100,
      timeout_ms: 20000,
    },
    reporting_period: { start: "2026-05-18", end: "2026-05-24" },
  },
  verdict: {
    primary_kpi: { metric: "apdex", value: 0.72, display: "0.72" },
    secondary_kpi: { metric: "error_rate", value: 5.2, display: "5.2%" },
    rating: "amber",
    summary: "Payment handshake is slow for a meaningful subset of users.",
    poor_user_pct: 12,
  },
  user_impact: {
    volume: 12000,
    experience_mix: {
      excellent_count: 3000,
      good_count: 4000,
      average_count: 2000,
      poor_count: 3000,
    },
    error_rate_pct: 5.2,
    failure_count: 624,
    funnel_link:
      "Sits between view_cart and begin_checkout in checkout funnel.",
    business_risk: "payment_friction",
    segment_highlights: [
      {
        label: "NetworkProvider: Vi India",
        volume: 2400,
        volume_pct_of_total: 20,
        poor_user_pct: 52,
        delta_vs_baseline_poor_pct: 22,
        impact_summary:
          "~20% of attempts; poor UX roughly 2× the interaction baseline.",
        rca_rank: 1,
      },
    ],
  },
  user_behavior: {
    flow_pattern: {
      happy_path: "JUSPAY_INITIATE_REQUEST → JUSPAY_INITIATE_RESULT_SUCCESS",
      deviant_paths: ["Back press during Juspay init"],
    },
    behavioral_signals: [],
    behavior_metric_links: [
      {
        user_action: "Back press during init",
        effect_on_metrics: "Correlates with elevated error rate on retry.",
      },
    ],
  },
  diagnosis: {
    reliability: ["Error rate 5.2% exceeds the 3% reliability threshold."],
    latency: ["P95 handshake latency elevated vs excellent threshold (316ms)."],
  },
  root_cause: {
    primary_cause:
      "Carrier and OS-specific latency tails concentrate poor experiences.",
    contributing_factors: ["Vi India network path"],
    evidence: [
      {
        source: "rca_segment",
        detail: "NetworkProvider: Vi India — poor user % +22 vs baseline.",
      },
    ],
    confidence: "medium",
  },
  actions: [
    {
      priority: "P0",
      action: "Pre-warm Juspay SDK on checkout entry",
      type: "app",
      owner: "Mobile",
      effort: "M",
      target_metric: "apdex",
      expected_lift: "Reduce poor bucket by improving init latency",
    },
    {
      priority: "P1",
      action: "Surface retry guidance when init exceeds good threshold",
      type: "ux",
      owner: "Product",
      effort: "S",
      target_metric: "error_rate",
      expected_lift: "Lower abandon after failed init",
    },
  ],
  follow_up: {
    sample_bad_session_ids: ["sess-fixture-1", "sess-fixture-2"],
    pulse_drill_down_filters: ["NetworkProvider", "OsVersion"],
    next_period_targets: {
      apdex_min: 0.85,
      error_rate_max_pct: 3,
      poor_user_max_pct: 5,
    },
    review_date: "2026-05-31",
  },
};
