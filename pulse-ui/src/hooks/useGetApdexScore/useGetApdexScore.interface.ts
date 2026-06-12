export type GraphDataParams = {
  requestBody: {
    useCaseId?: string;
    startTime: string;
    endTime: string;
    appVersion?: string;
    platform?: string;
    osVersion?: string;
    networkProvider?: string;
    deviceModel?: string;
    state?: string;
  };
  refetchInterval?: number;
  enabled?: boolean;
  jobId?: string;
  graphDataEndpoint?: string;
};

export type DataMapType = {
  empty: boolean;
  f: {
    v: number;
  }[];
};

export type GetGraphDataResponse = {
  filteredResults: Array<DataMapType>;
  apdexResults: Array<DataMapType>;
  interactionTimeResults: Array<DataMapType>;
  userCategorizationResults: Array<DataMapType>;
  errorInteractionResults: Array<DataMapType>;
  jobComplete: boolean;
  jobReference: {
    jobId: string;
  };
};
