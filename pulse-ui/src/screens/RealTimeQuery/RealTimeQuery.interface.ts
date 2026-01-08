/**
 * Real-time Query Interfaces
 * Types for SQL query execution and results display
 */

// Re-export types from hooks for convenience
export type {
  ColumnMetadata,
  TableMetadataResponse,
} from "../../hooks/useQueryMetadata";

export type {
  QueryJobStatus,
  SubmitQueryRequest,
  SubmitQueryResponse,
  GetJobStatusResponse,
} from "../../hooks/useSubmitQuery";

// Query result column
export interface QueryResultColumn {
  name: string;
  type: string;
}

// Query result row (dynamic based on query)
export interface QueryResultRow {
  [key: string]: string | number | boolean | null | undefined;
}

// Processed query result for display
export interface QueryResult {
  columns: QueryResultColumn[];
  rows: QueryResultRow[];
  totalRows: number;
  executionTimeMs?: number;
  dataScannedInBytes?: number;
  hasMore: boolean;
  nextToken?: string | null;
}

// Query execution state
export type QueryExecutionStatus =
  | "idle"
  | "submitting"
  | "polling"
  | "completed"
  | "failed"
  | "cancelled";

// Query execution state interface
export interface QueryExecutionState {
  status: QueryExecutionStatus;
  jobId: string | null;
  errorMessage: string | null;
  pollCount: number;
}

// Visualization configuration for results
export type ChartType = "table" | "line" | "bar" | "area" | "pie";

export interface VisualizationConfig {
  chartType: ChartType;
  showLegend?: boolean;
  stacked?: boolean;
}
