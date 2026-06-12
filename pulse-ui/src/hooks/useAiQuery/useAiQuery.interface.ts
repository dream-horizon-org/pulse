import {
  SubmitQueryResponse,
  QueryErrorResponse,
} from "../useSubmitQuery/useSubmitQuery.interface";

/**
 * Request body for AI-powered natural language query
 */
export interface AiQueryRequest {
  query: string;
  /** Optional context from pinned findings for follow-up queries */
  context?: string;
}

/** Severity level that drives visual hierarchy in the UI */
export type KeyPointSeverity = "critical" | "warning" | "healthy" | "info";

/** Prominent metric shown as a badge on the finding headline */
export interface KeyPointMetric {
  /** Short label (e.g. "P95", "Crash Rate") */
  label: string;
  /** Current value (e.g. "2,800ms", "4.2%") */
  value: string;
  /** Previous / baseline value for comparison (e.g. "420ms") */
  previousValue?: string;
}

/**
 * A single key finding from an AI response.
 * Each finding carries a headline, a detailed explanation,
 * the concrete data it considered, and an optional chart.
 */
export interface KeyPoint {
  /** Short headline (e.g. "Crash rate spiked to 4.2%") */
  text: string;
  /** Deeper explanation of what this finding means */
  detail: string;
  /** Specific data points / metrics the AI considered */
  evidence: string[];
  /** Optional per-finding chart configuration */
  chartConfig?: AiChartConfig;
  /** Severity drives border color, icon, and auto-expand behavior */
  severity: KeyPointSeverity;
  /** The single most important number for this finding */
  metric?: KeyPointMetric;
}

/**
 * AI-generated insights — concise answer + rich key findings
 */
export interface AiInsights {
  /** Direct answer to the user's question */
  answer: string;
  /** Rich key findings from the data */
  keyPoints: KeyPoint[];
}

/**
 * Chart configuration returned by the AI backend.
 * Re-exported from AiChat.interface for the response layer.
 */
export interface AiChartConfig {
  type: "bar" | "line" | "pie" | "area";
  title: string;
  xAxisLabel?: string;
  yAxisLabel?: string;
  data: {
    labels: string[];
    datasets: { name: string; values: number[] }[];
  };
}

/**
 * Response from AI query endpoint - extends SubmitQueryResponse
 * with insights and generated SQL
 */
export interface AiQueryResponse extends SubmitQueryResponse {
  /** The SQL query generated from the natural language input */
  generatedSql?: string;
  /** AI-generated insights from the query results */
  insights?: AiInsights;
  /** Tables/databases analyzed */
  sourcesAnalyzed?: string[];
  /** Time range analyzed (simple strings) */
  timeRange?: { start: string; end: string };
}

/**
 * Re-export error type for convenience
 */
export type { QueryErrorResponse as AiQueryErrorResponse };
