import { CriticalInteractionDetailsFilterValues } from "../../../../../../CriticalInteractionDetails.interface";

export interface ReleasePerformanceData {
  version: string;
  apdex: number;
  crashes: number;
  anr: number;
}

export interface UseGetReleasePerformanceParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface UseGetReleasePerformanceReturn {
  releaseData: ReleasePerformanceData[];
  isLoading: boolean;
  isError: boolean;
  error?: Error | null;
}

