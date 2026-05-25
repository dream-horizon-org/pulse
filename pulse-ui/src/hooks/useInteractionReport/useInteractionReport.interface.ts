/** Wire types for InteractionReportV1 (snake_case JSON from pulse_ai). */

export type HealthRating = "red" | "amber" | "green";
export type PrimaryKpi = "apdex" | "error_rate";
export type ConfidenceLevel = "high" | "medium" | "low";
export type ActionPriority = "P0" | "P1" | "P2" | "P3";
export type ActionType = "app" | "ux" | "config" | "infra" | "instrumentation";
export type EffortSize = "S" | "M" | "L";
export type BusinessRisk =
  | "checkout_blocked"
  | "payment_friction"
  | "browse_friction"
  | "na_test";
export type DiagnosisLens = "reliability" | "latency" | "measurement";

export interface InteractionThresholdsWire {
  excellent_ms: number;
  good_ms: number;
  average_ms: number;
  timeout_ms: number;
}

export interface ReportingPeriodWire {
  start: string;
  end: string;
}

export interface InteractionIdentityWire {
  name: string;
  business_moment: string;
  start_event: string;
  end_event: string;
  thresholds: InteractionThresholdsWire;
  reporting_period: ReportingPeriodWire;
}

export interface KpiSnapshotWire {
  metric: PrimaryKpi;
  value: number;
  display?: string | null;
}

export interface HealthVerdictWire {
  primary_kpi: KpiSnapshotWire;
  secondary_kpi: KpiSnapshotWire;
  rating: HealthRating;
  summary: string;
  poor_user_pct?: number | null;
}

export interface ExperienceMixWire {
  excellent_count: number;
  good_count: number;
  average_count: number;
  poor_count: number;
}

export interface SegmentHighlightWire {
  label: string;
  volume: number;
  volume_pct_of_total?: number | null;
  poor_user_pct?: number | null;
  delta_vs_baseline_poor_pct?: number | null;
  error_rate_pct?: number | null;
  delta_vs_baseline_error_rate_pct?: number | null;
  impact_summary: string;
  rca_rank?: number | null;
  dimensions?: Record<string, string> | null;
}

export interface UserImpactWire {
  volume: number;
  experience_mix: ExperienceMixWire;
  error_rate_pct: number;
  failure_count: number;
  funnel_link: string;
  business_risk: BusinessRisk;
  segment_highlights?: SegmentHighlightWire[] | null;
}

export interface FlowPatternWire {
  happy_path: string;
  deviant_paths?: string[];
}

export interface BehavioralSignalWire {
  signal: string;
  meaning: string;
  estimated_frequency?: string | null;
  notes?: string | null;
  example?: string | null;
}

export interface BehaviorMetricLinkWire {
  user_action: string;
  effect_on_metrics: string;
}

export interface CohortBehaviorNoteWire {
  cohort: string;
  observation: string;
}

export interface UserBehaviorWire {
  flow_pattern: FlowPatternWire;
  behavioral_signals?: BehavioralSignalWire[];
  behavior_metric_links?: BehaviorMetricLinkWire[];
  cohort_behavior?: CohortBehaviorNoteWire[] | null;
}

export interface DiagnosisWire {
  reliability?: string[];
  latency?: string[];
  measurement?: string[];
}

export interface RootCauseEvidenceWire {
  source: "rca_segment" | "session_pattern" | "journey_path" | "other";
  detail: string;
}

export interface RootCauseWire {
  primary_cause: string;
  contributing_factors?: string[];
  ruled_out?: string[] | null;
  evidence?: RootCauseEvidenceWire[];
  confidence: ConfidenceLevel;
}

export interface ImprovementActionWire {
  priority: ActionPriority;
  action: string;
  type: ActionType;
  owner: string;
  effort: EffortSize;
  target_metric: string;
  expected_lift: string;
  behavior_driven?: boolean;
}

export interface PeriodTargetsWire {
  apdex_min?: number | null;
  error_rate_max_pct?: number | null;
  poor_user_max_pct?: number | null;
}

export interface ProofAndFollowUpWire {
  sample_bad_session_ids: string[];
  pulse_drill_down_filters?: string[];
  next_period_targets: PeriodTargetsWire;
  review_date: string;
}

export interface InteractionReportV1Wire {
  version?: number;
  project_id?: string;
  generated_at?: string;
  identity: InteractionIdentityWire;
  verdict: HealthVerdictWire;
  user_impact: UserImpactWire;
  user_behavior: UserBehaviorWire;
  diagnosis: DiagnosisWire;
  root_cause: RootCauseWire;
  actions: ImprovementActionWire[];
  follow_up: ProofAndFollowUpWire;
}

export interface UseInteractionReportParams {
  entityKey: string | null;
  date: string | null | undefined;
  projectId?: string;
}

export interface UseInteractionReportResult {
  report: InteractionReportV1Wire | null;
  cached: boolean;
  cachedAt: string | null;
  loading: boolean;
  error: string | null;
  generate: (regenerate?: boolean) => Promise<void>;
}
