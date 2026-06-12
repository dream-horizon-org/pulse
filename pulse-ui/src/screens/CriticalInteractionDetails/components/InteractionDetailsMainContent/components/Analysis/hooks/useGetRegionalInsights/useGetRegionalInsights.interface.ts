import { CriticalInteractionDetailsFilterValues } from "../../../../../../CriticalInteractionDetails.interface";

export interface RegionalDataPoint {
  name: string;
  value: number;
}

export interface RegionalInsightsData {
  errorRateByRegion: RegionalDataPoint[];
  poorUsersPercentageByRegion: RegionalDataPoint[];
}

export interface UseGetRegionalInsightsParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface UseGetRegionalInsightsReturn {
  regionalData: RegionalInsightsData;
  isLoading: boolean;
  isError: boolean;
  error?: Error | null;
}

