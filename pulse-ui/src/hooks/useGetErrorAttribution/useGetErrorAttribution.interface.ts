export type ErrorAttributionRrUndefinedReason =
  | "INFINITE_RR"
  | "EMPTY_TREATED_ARM"
  | "EMPTY_CONTROL_ARM"
  | "ZERO_POOR"
  | string;

export type ErrorAttributionSignal = "crash" | "anr" | "non_fatal" | "api";

export type RelatedAttributionRowKind = "issue" | "api";

export interface RelatedAttributionEntry {
  sourceSignal: ErrorAttributionSignal | string;
  rowKind: RelatedAttributionRowKind;
  groupId?: string | null;
  title?: string | null;
  exceptionType?: string | null;
  url?: string | null;
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

/**
 * Error distribution payload merged into {@code report.structured.errorAttribution} on the RCA
 * report response (camelCase JSON).
 */
export interface ErrorAttributionResponse {
  disclaimer: string;
  cachedAt?: string | null;
  minRiskRatioForIssueAttribution?: number | null;
  relatedAttributions?: RelatedAttributionEntry[] | null;
}
