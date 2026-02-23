/**
 * Type definitions for the Session Timeline flame chart data structures
 */

import type { AttributeValue } from "../../../types/attributes";

/**
 * Raw item in the flame chart before tree building
 */
export interface FlameChartItem {
  id: string;
  name: string;
  start: number; // Relative time from session start in ms
  duration: number; // Duration in ms
  type: "span" | "log" | "exception" | "orphan-span" | "orphan-log";
  color?: string;
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  metadata?: Record<string, AttributeValue>;
}

/**
 * Node in the hierarchical flame chart tree
 */
export interface FlameChartNode {
  id: string;
  name: string;
  start: number;
  duration: number;
  type: "span" | "log" | "exception" | "orphan-span" | "orphan-log";
  color?: string;
  traceId: string;
  spanId: string;
  parentSpanId?: string;
  children: FlameChartNode[];
  metadata?: Record<string, AttributeValue>;
}

/**
 * Basic flame chart data structure (for flame-chart-js library)
 */
export interface FlameChartData {
  name: string;
  start: number;
  duration: number;
  type?: string;
  children?: FlameChartData[];
  color?: string;
}

/**
 * Extended flame chart data with additional metadata
 */
export interface ExtendedFlameChartData extends FlameChartData {
  id?: string;
  traceId?: string;
  spanId?: string;
  parentSpanId?: string;
  metadata?: Record<string, AttributeValue>;
}

/**
 * Result of transforming API data to flame chart format
 */
export interface TransformResult {
  flameChartData: FlameChartNode[];
  sessionStartTime: number;
  sessionDuration: number;
  itemsMap: Map<string, FlameChartItem>;
  orphanItems: FlameChartItem[];
  totalDepth: number; // Maximum depth of the tree for minimap
}

/**
 * Raw span row from API response
 */
export interface RawSpanRow {
  traceId: string;
  spanId: string;
  parentSpanId: string;
  spanName: string;
  spanKind: string;
  serviceName: string;
  timestamp: string;
  duration: number;
  statusCode: string;
  statusMessage: string;
  pulseType: string;
}

/**
 * Raw log row from API response
 */
export interface RawLogRow {
  traceId: string;
  spanId: string;
  timestamp: string;
  severityText: string;
  severityNumber: number;
  body: string;
  pulseType: string;
  eventName: string;
  serviceName: string;
  scopeName: string;
  logAttributesJson?: string;
  resourceAttributesJson?: string;
}

/**
 * Raw exception row from API response
 */
export interface RawExceptionRow {
  traceId: string;
  spanId: string;
  timestamp: string;
  eventName: string;
  title: string;
  exceptionMessage: string;
  exceptionType: string;
  screenName: string;
  groupId: string;
  pulseType: string;
}

/**
 * Filter options for data transformation
 */
export interface ActiveFilters {
  showSpans: boolean;
  showLogs: boolean;
  showExceptions: boolean;
}

/**
 * API response structure for traces
 */
export interface TracesApiResponse {
  fields: string[];
  rows: any[][];
}

/**
 * API response structure for logs
 */
export interface LogsApiResponse {
  fields: string[];
  rows: any[][];
}

/**
 * API response structure for exceptions
 */
export interface ExceptionsApiResponse {
  fields: string[];
  rows: any[][];
}
