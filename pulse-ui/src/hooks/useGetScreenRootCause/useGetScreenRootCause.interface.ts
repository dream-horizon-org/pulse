/** Mirrors backend `RootCauseRestResponse` for screen-scoped RCA. */
export type ScreenRootCauseMode = "flat" | "hierarchical";

export interface ScreenRootCauseSegment {
  label: string;
  dimensions: Record<string, string>;
  metrics: Record<string, unknown>;
  deltas?: Record<string, number> | null;
}

export interface ScreenRootCauseData {
  baseline: Record<string, unknown> | null;
  segments: ScreenRootCauseSegment[] | null;
  mode?: ScreenRootCauseMode | null;
  cachedAt?: string | null;
  everythingGood?: boolean | null;
  noDataAvailable?: boolean | null;
  message?: string | null;
}

export interface UseGetScreenRootCauseParams {
  screenName: string | null | undefined;
  /** Anchor calendar day (UTC), e.g. from filter end time — same as interaction RCA `date`. */
  date: string | null | undefined;
  /** Exclusive upper bound on event timestamps (ISO-8601) — same as interaction RCA `asOf`. */
  asOfIso: string | null | undefined;
  projectId: string | null | undefined;
  enabled?: boolean;
}
