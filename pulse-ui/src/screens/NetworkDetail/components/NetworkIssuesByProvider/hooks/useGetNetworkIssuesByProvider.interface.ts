export interface UseGetNetworkIssuesByProviderParams {
  method: string;
  url: string;
  startTime: string;
  endTime: string;
  enabled?: boolean;
  additionalFilters?: Array<{
    field: string;
    operator: "LIKE" | "EQ";
    value: string[];
  }>;
}

export interface NetworkIssueData {
  networkProvider: string;
  errors: number;
}

export interface NetworkIssuesByProviderData {
  connectionTimeoutErrorsByNetwork: NetworkIssueData[];
  error4xxByNetwork: NetworkIssueData[];
  error5xxByNetwork: NetworkIssueData[];
}

export interface UseGetNetworkIssuesByProviderReturn {
  data: NetworkIssuesByProviderData;
  isLoading: boolean;
  isError: boolean;
  error: unknown;
}

