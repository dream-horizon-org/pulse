/**
 * Query Stats API Types
 * GET /query/stats?startDate=...&endDate=...
 */

export type QueryStatsResponse = {
  totalQueries: number;
  successfulQueries: number;
  failedQueries: number;
  cancelledQueries: number;
  totalDataScannedBytes: number;
  averageExecutionTimeMs: number;
  totalExecutionTimeMs: number;
  queriesByDay?: {
    date: string;
    count: number;
    dataScannedBytes: number;
  }[];
};

export type QueryStatsParams = {
  startDate: Date;
  endDate: Date;
  enabled?: boolean;
};

