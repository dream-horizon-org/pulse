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
  /**
   * Explicit window (UTC ISO instants). When both are non-empty, the request uses `start` and `end`
   * only (`end` is exclusive on the server). Matches the screen detail time filter.
   */
  windowStartIso: string | null | undefined;
  windowEndIso: string | null | undefined;
  /** Legacy: `date` + `asOf` when explicit window is not both set. */
  date?: string | null | undefined;
  asOfIso?: string | null | undefined;
  projectId: string | null | undefined;
  enabled?: boolean;
}
