import { CriticalInteractionDetailsFilterValues } from "../../../../../../CriticalInteractionDetails.interface";

export interface LatencyByNetworkData {
  networkProvider: string;
  latency: number;
}

export interface LatencyByDeviceData {
  device: string;
  latency: number;
}

export interface LatencyByOsData {
  os: string;
  latency: number;
}

export interface LatencyAnalysisData {
  latencyByNetwork: LatencyByNetworkData[];
  latencyByDevice: LatencyByDeviceData[];
  latencyByOS: LatencyByOsData[];
}

export interface UseGetLatencyAnalysisParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface UseGetLatencyAnalysisReturn {
  latencyAnalysisData: LatencyAnalysisData;
  isLoading: boolean;
  isError: boolean;
  error?: any;
}

