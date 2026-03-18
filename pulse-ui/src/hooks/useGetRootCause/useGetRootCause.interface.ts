/** Baseline metrics: metric key -> value */
export type RootCauseBaseline = Record<string, number | string>;

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
  date?: string | null;
  enabled?: boolean;
  projectId?: string | null;
};
