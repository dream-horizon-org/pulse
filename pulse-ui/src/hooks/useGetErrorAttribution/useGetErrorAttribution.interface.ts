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

export interface ErrorAttributionResponse {
  trackBInsufficientData: boolean;
  nPoorInU: number;
  nU: number;
  riskRatios: ErrorAttributionRiskRatioEntry[];
  jointWinners?: string[] | null;
  analysisPhase?: string;
  track?: string;
  diagnosticSpecVersion?: string;
  disclaimer: string;
  cachedAt?: string | null;
}

export interface UseGetErrorAttributionParams {
  interactionName: string | null;
  /** ISO instants; must match `getErrorAttributionWindowIso(date)` (memoize in UI so `end` is stable per view). */
  start: string;
  end: string;
  projectId: string | null;
  enabled?: boolean;
}

export interface ErrorAttributionRequestContext {
  interactionName: string;
  start: string;
  end: string;
  projectId: string;
}
