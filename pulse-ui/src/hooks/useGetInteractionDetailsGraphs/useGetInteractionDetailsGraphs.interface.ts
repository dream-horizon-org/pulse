import { CriticalInteractionDetailsFilterValues } from "../../screens/CriticalInteractionDetails";

export type InteractionDetailsGraphsData = {
  timestamp: number;
  apdex: number;
  errorRate: number;
  userAvg: number;
  userGood: number;
  userExcellent: number;
  userPoor: number;
};

export type InteractionDetailsMetricsData = {
  apdex: number | null;
  errorRate: number | null;
  p50: number | null;
  p95: number | null;
  frozenFrameRate: number | null;
  crashRate: number | null;
  anrRate: number | null;
  networkErrorRate: number | null;
  excellentUsersPercentage: string | null;
  goodUsersPercentage: string | null;
  averageUsersPercentage: string | null;
  poorUsersPercentage: string | null;
  hasData: boolean;
};

export interface UseGetInteractionDetailsGraphsParams {
  interactionName?: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface UseGetInteractionDetailsGraphsReturn {
  graphData: InteractionDetailsGraphsData[];
  metrics: InteractionDetailsMetricsData;
  isLoading: boolean;
  isError: boolean;
}

