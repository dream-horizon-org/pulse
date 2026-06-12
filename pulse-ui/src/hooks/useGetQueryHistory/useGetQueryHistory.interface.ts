/**
 * Query History API Types
 * GET /query/history
 */

export type QueryHistoryItem = {
  jobId: string;
  queryString: string;
  queryExecutionId?: string;
  status: "SUBMITTED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
  resultLocation?: string;
  errorMessage?: string;
  dataScannedInBytes?: number;
  createdAt?: number; // timestamp in milliseconds
  updatedAt?: number; // timestamp in milliseconds
  completedAt?: number; // timestamp in milliseconds
};

export type GetQueryHistoryResponse = {
  queries: QueryHistoryItem[];
  total: number;
  limit: number;
  offset: number;
};

export type GetQueryHistoryParams = {
  enabled?: boolean;
};
