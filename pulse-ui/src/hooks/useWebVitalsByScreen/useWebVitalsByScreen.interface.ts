import { WebVitalsByScreenResponse } from "../../screens/WebVitals/WebVitals.interface";

export type UseWebVitalsByScreenParams = {
  startTime: number;
  endTime: number;
  vitalName: string;
  /** When false, the query does not run (e.g. per-screen Web Vitals has no by-screen table). */
  enabled?: boolean;
};
