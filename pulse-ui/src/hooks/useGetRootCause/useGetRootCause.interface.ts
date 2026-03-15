/** Baseline metrics: metric key -> value */
export type RootCauseBaseline = Record<string, number | string>;

/** One segment: label from backend (e.g. "Android + App 3.4.5 + Jio" or "Platform: Android") */
export type RootCauseSegment = {
  label: string;
  dimensions?: Record<string, string>;
  metrics: Record<string, number | string>;
  deltas: Record<string, number | string>;
};

export type RootCauseMode = "hierarchical" | "flat";

export type RootCauseResponse = {
  baseline: RootCauseBaseline;
  segments: RootCauseSegment[];
  mode: RootCauseMode;
  cachedAt: string;
  everythingGood?: boolean;
  message?: string;
  noDataAvailable?: boolean;
};

export type UseGetRootCauseParams = {
  interactionName: string | null;
  /** Optional date (YYYY-MM-DD); when omitted backend uses today UTC */
  date?: string | null;
  /** When true, query is enabled */
  enabled?: boolean;
};
