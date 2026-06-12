export type ErrorRateResponse = {
  readings?: Array<ErrorRateDataMapType>;
  jobComplete: boolean;
  jobReference?: {
    jobId: string;
  };
};

export type ErrorRateDataMapType = {
  errorRate: string;
  useCaseId: string;
  timestamp: number;
};
