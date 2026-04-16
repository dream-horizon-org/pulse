export type ErrorAttributionRrUndefinedReason =
  | "INFINITE_RR"
  | "EMPTY_TREATED_ARM"
  | "EMPTY_CONTROL_ARM"
  | "ZERO_POOR"
  | string;

export type ErrorAttributionSignal = "crash" | "anr" | "non_fatal" | "api";

export interface ErrorAttributionRiskRatioEntry {
  signal: ErrorAttributionSignal;
  nTreated: number;
  nControl: number;
  nTreatedLow: number;
  nControlLow: number;
  p1?: number | null;
  p2?: number | null;
  rr?: number | null;
  rrUndefined: boolean;
  rrUndefinedReason?: ErrorAttributionRrUndefinedReason | null;
}

export interface ErrorAttributionDrillDownIssue {
  groupId: string;
  title: string;
  occurrences: number;
  exceptionType?: string | null;
  nTreated?: number | null;
  nControl?: number | null;
  nTreatedLow?: number | null;
  nControlLow?: number | null;
  p1?: number | null;
  p2?: number | null;
  rr?: number | null;
  rrUndefined?: boolean | null;
  rrUndefinedReason?: ErrorAttributionRrUndefinedReason | null;
}

export interface ErrorAttributionDrillDownNetworkEndpoint {
  url: string;
  graphqlOperationName?: string | null;
  graphqlOperationType?: string | null;
  occurrences: number;
  nTreated?: number | null;
  nControl?: number | null;
  nTreatedLow?: number | null;
  nControlLow?: number | null;
  p1?: number | null;
  p2?: number | null;
  rr?: number | null;
  rrUndefined?: boolean | null;
  rrUndefinedReason?: ErrorAttributionRrUndefinedReason | null;
}

export type ErrorAttributionDrillDownTemporalRule =
  | "none"
  | "issue_ts_before_poor_interaction_ts"
  | string;

export interface ErrorAttributionDrillDownPayload {
  signal?: string;
  eligibility?: string | null;
  /** When set, UI may show temporal-ordering footnote for drill-down rows. */
  temporalRule?: ErrorAttributionDrillDownTemporalRule | null;
  issues?: ErrorAttributionDrillDownIssue[] | null;
  networkEndpoints?: ErrorAttributionDrillDownNetworkEndpoint[] | null;
}

export interface ErrorAttributionResponse {
  trackBInsufficientData: boolean;
  /** Present on fresh compute; older cache rows may omit (UI falls back to 1,000). */
  minPoorSessionsForErrorAttribution?: number;
  nPoorInU: number;
  nU: number;
  riskRatios: ErrorAttributionRiskRatioEntry[];
  jointWinners?: string[] | null;
  analysisPhase?: string;
  track?: string;
  diagnosticSpecVersion?: string;
  disclaimer: string;
  cachedAt?: string | null;
  /** Present when `drillDown=` query param was sent; not in cache JSON. */
  drillDown?: Partial<
    Record<ErrorAttributionSignal, ErrorAttributionDrillDownPayload>
  > | null;
}

export interface UseGetErrorAttributionParams {
  interactionName: string | null;
  /** ISO instants; must match `getErrorAttributionWindowIso(date)` (memoize in UI so `end` is stable per view). */
  start: string;
  end: string;
  projectId: string | null;
  /** When set, appended as `drillDown=` comma-separated signals on `GET .../error-attribution`. */
  drillDownSignals?: ErrorAttributionSignal[] | null;
  enabled?: boolean;
}

export interface ErrorAttributionRequestContext {
  interactionName: string;
  start: string;
  end: string;
  projectId: string;
  drillDownSignals?: ErrorAttributionSignal[] | null;
}
