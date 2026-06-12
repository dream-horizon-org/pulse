import { CriticalInteractionDetailsFilterValues } from "../../../../../../CriticalInteractionDetails.interface";

export interface NetworkIssueData {
  networkProvider: string;
  errors: number;
}

export interface NetworkIssuesData {
  connectionTimeoutErrorsByNetwork: NetworkIssueData[];
  error5xxByNetwork: NetworkIssueData[];
  error4xxByNetwork: NetworkIssueData[];
}

export interface UseGetNetworkIssuesParams {
  interactionName: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  dashboardFilters?: CriticalInteractionDetailsFilterValues;
}

export interface UseGetNetworkIssuesReturn {
  networkIssuesData: NetworkIssuesData;
  isLoading: boolean;
  isError: boolean;
  error?: any;
}

