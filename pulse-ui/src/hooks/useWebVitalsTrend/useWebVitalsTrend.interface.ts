import { WebVitalsTrendResponse } from "../../screens/WebVitals/WebVitals.interface";

export type UseWebVitalsTrendParams = {
  startTime: number;
  endTime: number;
  vitalName: string;
  bucketMinutes?: number;
  screenName?: string;
};
