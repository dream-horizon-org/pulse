import { WebVitalsSummaryResponse } from "../../screens/WebVitals/WebVitals.interface";

export type UseWebVitalsSummaryParams = {
  startTime: number;
  endTime: number;
  screenName?: string;
};
