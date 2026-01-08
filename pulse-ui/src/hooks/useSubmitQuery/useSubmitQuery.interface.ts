/**
 * Query job status types
 */
export type QueryJobStatus =
  | "QUEUED"
  | "RUNNING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

/**
 * Request body for submitting a query
 */
export interface SubmitQueryRequest {
  queryString: string;
  parameters?: string[];
  timestamp?: string;
}

/**
 * Response from submitting a query
 */
export interface SubmitQueryResponse {
  jobId: string;
  status: QueryJobStatus;
  message: string;
  queryExecutionId?: string;
  resultLocation?: string;
  resultData?: Record<string, unknown>[] | null;
  nextToken?: string | null;
  dataScannedInBytes?: number;
  createdAt?: string;
  completedAt?: string;
}

/**
 * Response from getting job status
 */
export interface GetJobStatusResponse {
  jobId: string;
  queryString?: string;
  queryExecutionId?: string;
  status: QueryJobStatus;
  resultLocation?: string;
  errorMessage?: string;
  resultData?: Record<string, unknown>[] | null;
  nextToken?: string | null;
  dataScannedInBytes?: number;
  createdAt?: string;
  updatedAt?: string;
  completedAt?: string;
}

/**
 * Error response for query APIs
 */
export interface QueryErrorResponse {
  error: {
    message: string;
    cause: string;
  };
  data: null;
  status: number;
}

/**
 * Processed query result for display
 */
export interface ProcessedQueryResult {
  columns: { name: string; type: string }[];
  rows: Record<string, unknown>[];
  totalRows: number;
  executionTimeMs?: number;
  dataScannedInBytes?: number;
  hasMore: boolean;
  nextToken?: string | null;
}

