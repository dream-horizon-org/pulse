import type { ScreenRootCauseData } from "../useGetScreenRootCause";

export type UseRegenerateScreenRcaNarrativeParams = {
  screenName: string;
  windowStartIso: string;
  windowEndIso: string;
  projectId: string;
  rootCauseData: ScreenRootCauseData;
};
