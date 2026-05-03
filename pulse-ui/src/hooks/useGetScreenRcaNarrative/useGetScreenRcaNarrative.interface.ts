import type { ScreenRootCauseData } from "../useGetScreenRootCause";

/** Mirrors pulse_ai `ScreenRcaNarrativeV1` (snake_case JSON). */
export type ScreenRcaNarrativeV1 = {
  version: 1;
  executive_summary: string;
  recommendations: string[];
};

export type ScreenRcaReportApiResponse = {
  report?: {
    narrative?: ScreenRcaNarrativeV1 | null;
  } | null;
  cached?: boolean;
  /** ISO instant — same as interaction RCA report; set by server on cache hit or after persist. */
  cachedAt?: string | null;
};

export interface UseGetScreenRcaNarrativeParams {
  screenName: string | null | undefined;
  /** Anchor `yyyy-MM-dd` (same as screen root-cause API); sent for MySQL cache key alignment with interaction RCA. */
  anchorDate: string | null | undefined;
  windowStartIso: string | null | undefined;
  windowEndIso: string | null | undefined;
  projectId: string | null | undefined;
  /** Tabular payload from GET screen root-cause; required when enabled. */
  rootCauseData: ScreenRootCauseData | null | undefined;
  enabled?: boolean;
}
