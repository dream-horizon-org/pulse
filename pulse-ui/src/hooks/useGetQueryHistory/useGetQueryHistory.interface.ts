/**
 * Query History API Types
 * GET /query/history
 */

export type QueryHistoryItem = {
  queryId: string;
  query: string;
  status: "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "CANCELLED";
  submittedAt: string;
  completedAt?: string;
  executionTimeMs?: number;
  dataScannedInBytes?: number;
  rowCount?: number;
  errorMessage?: string;
};

export type GetQueryHistoryResponse = {
  queries: QueryHistoryItem[];
  totalCount: number;
};

export type GetQueryHistoryParams = {
  enabled?: boolean;
};
