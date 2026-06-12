import { CriticalInteractionDetailsFilterValues } from "../../../../../../CriticalInteractionDetails.interface";

export interface PlatformDataPoint {
  platform: string;
  value: number;
}

export interface PlatformInsightsData {
  poorUsersByPlatform: PlatformDataPoint[];
  errorsByPlatform: PlatformDataPoint[];
}

export interface UseGetPlatformInsightsParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface UseGetPlatformInsightsReturn {
  platformData: PlatformInsightsData;
  isLoading: boolean;
  isError: boolean;
  error?: Error | null;
}

