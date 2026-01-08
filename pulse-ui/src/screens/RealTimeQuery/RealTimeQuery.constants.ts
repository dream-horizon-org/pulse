/**
 * Real-time Query Constants
 * Configuration and UI text constants
 */

// UI Text constants
export const REALTIME_QUERY_TEXTS = {
  PAGE_TITLE: "Query Builder",
  PAGE_SUBTITLE: "Run SQL queries on your clickstream data and visualize results",
  RESULTS_TITLE: "Results",
  RUN_QUERY: "Run Query",
  CANCEL_QUERY: "Cancel",
  SAVE_QUERY: "Save Query",
  QUERY_HISTORY: "History",
  NO_RESULTS: "No results found. Try adjusting your query.",
  LOADING_RESULTS: "Running query...",
  QUERY_SUCCESS: "Query completed successfully",
  QUERY_ERROR: "Query failed. Please check your query and try again.",
  EXECUTION_TIME: "Execution time",
  DATA_SCANNED: "Data scanned",
  LOADING_METADATA: "Loading table metadata...",
  METADATA_ERROR: "Failed to load table metadata. Please refresh the page.",
  SUBMIT_QUERY_ERROR: "Failed to submit query",
  POLLING_QUERY: "Fetching results...",
  QUERY_TIMEOUT: "Query timed out. Please try again with a simpler query.",
  QUERY_CANCELLED: "Query was cancelled",
  EMPTY_QUERY: "Please enter a SQL query to run",
  COPY_FIELD: "Click to copy field name",
};

// Query job polling configuration
export const QUERY_POLLING_CONFIG = {
  POLL_INTERVAL_MS: 5000, // Poll every 5 seconds
  MAX_POLL_ATTEMPTS: 60, // Max 5 minutes of polling (60 * 5s = 300s)
};

// Default query limit
export const DEFAULT_QUERY_LIMIT = 1000;

// Results pagination
export const RESULTS_PAGE_SIZE = 25;

// Type display colors for field types
export const TYPE_COLORS: Record<string, string> = {
  varchar: "blue",
  string: "blue",
  integer: "green",
  int: "green",
  bigint: "green",
  double: "green",
  float: "green",
  number: "green",
  timestamp: "orange",
  date: "orange",
  boolean: "grape",
  bool: "grape",
  array: "cyan",
  map: "gray",
  struct: "gray",
  row: "gray",
};

// Get display color for a column type
export function getTypeColor(type: string): string {
  const normalizedType = type.toLowerCase();
  return TYPE_COLORS[normalizedType] || "gray";
}

// Format bytes to human readable string
export function formatBytes(bytes: number): string {
  if (bytes === 0) return "0 B";
  const k = 1024;
  const sizes = ["B", "KB", "MB", "GB", "TB"];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(2))} ${sizes[i]}`;
}

// Format milliseconds to human readable string
export function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  return `${(ms / 60000).toFixed(1)}m`;
}
