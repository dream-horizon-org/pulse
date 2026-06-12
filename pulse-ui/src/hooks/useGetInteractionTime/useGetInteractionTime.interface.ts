export type GetApdexScoreParams = {
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
};

export type DataMapType = {
  empty: boolean;
  map: {
    f: {
      v: number;
    }[];
  };
};

export type GetGraphDataResponse = {
  filteredResults: Array<DataMapType>;
  apdexResults: Array<DataMapType>;
  interactionTimeResults: Array<DataMapType>;
  userCategorizationResults: Array<DataMapType>;
};
