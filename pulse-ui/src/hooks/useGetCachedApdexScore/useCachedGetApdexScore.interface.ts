export type ApdexResponse = {
  readings?: Array<{
    apdexScore: string;
    timestamp: number;
    useCaseId: string;
  }>;
  jobComplete: boolean;
  jobReference?: {
    jobId: string;
  };
};

export type ApdexDataMapType = {
  apdexScore: string;
  timestamp: number;
  useCaseId: string;
};
