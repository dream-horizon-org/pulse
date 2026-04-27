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
};

export interface UseGetScreenRcaNarrativeParams {
  screenName: string | null | undefined;
  windowStartIso: string | null | undefined;
  windowEndIso: string | null | undefined;
  projectId: string | null | undefined;
  /** Tabular payload from GET screen root-cause; required when enabled. */
  rootCauseData: ScreenRootCauseData | null | undefined;
  enabled?: boolean;
}
