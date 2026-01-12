/**
 * QueryBuilder Interfaces
 * Defines all types for the visual query builder component
 */

// Time range presets
export type TimeRangePreset =
  | "last_15_minutes"
  | "last_1_hour"
  | "last_6_hours"
  | "last_24_hours"
  | "last_7_days"
  | "last_30_days"
  | "custom";

export interface TimeRange {
  preset: TimeRangePreset;
  // For custom range
  startDate?: Date;
  endDate?: Date;
}

// Aggregation functions supported by Athena
export type AggregationFunction =
  | "COUNT"
  | "COUNT_DISTINCT"
  | "SUM"
  | "AVG"
  | "MIN"
  | "MAX";

// Column reference - can be simple column or JSON path
export interface ColumnReference {
  column: string;
  jsonPath?: string; // For JSON extraction like $.field.nested
  isJsonField: boolean;
}

export interface Metric {
  id: string;
  column: string;
  jsonPath?: string; // For JSON fields
  aggregation: AggregationFunction;
  alias?: string;
}

// Filter operators
export type FilterOperator =
  | "equals"
  | "not_equals"
  | "contains"
  | "not_contains"
  | "starts_with"
  | "ends_with"
  | "greater_than"
  | "less_than"
  | "greater_than_or_equal"
  | "less_than_or_equal"
  | "is_null"
  | "is_not_null"
  | "in"
  | "not_in";

export interface Filter {
  id: string;
  column: string;
  jsonPath?: string; // For JSON fields
  operator: FilterOperator;
  value: string | string[];
}

export interface Dimension {
  id: string;
  column: string;
  jsonPath?: string; // For JSON fields
  alias?: string;
}

// Sort direction
export type SortDirection = "ASC" | "DESC";

export interface SortConfig {
  column: string;
  direction: SortDirection;
}

// Complete query builder state
export interface QueryBuilderState {
  timeRange: TimeRange;
  metrics: Metric[];
  dimensions: Dimension[];
  filters: Filter[];
  sortBy?: SortConfig;
  limit: number;
}

// Default state
export const DEFAULT_QUERY_BUILDER_STATE: QueryBuilderState = {
  timeRange: {
    preset: "last_24_hours",
  },
  metrics: [],
  dimensions: [],
  filters: [],
  limit: 1000,
};

// Column with additional metadata for UI
export interface EnhancedColumn {
  name: string;
  type: string;
  isJson: boolean;
  category: "string" | "number" | "boolean" | "timestamp" | "json" | "other";
}

// Props for sub-components
export interface TimeRangeSelectorProps {
  value: TimeRange;
  onChange: (value: TimeRange) => void;
}

export interface MetricSelectorProps {
  metrics: Metric[];
  availableColumns: EnhancedColumn[];
  onChange: (metrics: Metric[]) => void;
}

export interface DimensionSelectorProps {
  dimensions: Dimension[];
  availableColumns: EnhancedColumn[];
  onChange: (dimensions: Dimension[]) => void;
}

export interface FilterBuilderProps {
  filters: Filter[];
  availableColumns: EnhancedColumn[];
  onChange: (filters: Filter[]) => void;
}

export interface QueryBuilderProps {
  tableName: string;
  databaseName: string;
  columns: { name: string; type: string }[];
  /** Called whenever the generated SQL changes */
  onQueryChange: (sql: string) => void;
  isLoading?: boolean;
}

// Aggregation function display info
export const AGGREGATION_OPTIONS: {
  value: AggregationFunction;
  label: string;
  description: string;
  icon: string;
}[] = [
  { value: "COUNT", label: "Count", description: "Count all rows", icon: "🔢" },
  { value: "COUNT_DISTINCT", label: "Count Unique", description: "Count unique values", icon: "🎯" },
  { value: "SUM", label: "Sum", description: "Sum of values", icon: "➕" },
  { value: "AVG", label: "Average", description: "Average of values", icon: "📊" },
  { value: "MIN", label: "Minimum", description: "Minimum value", icon: "⬇️" },
  { value: "MAX", label: "Maximum", description: "Maximum value", icon: "⬆️" },
];

// Filter operator display info
export const FILTER_OPERATORS: {
  value: FilterOperator;
  label: string;
  requiresValue: boolean;
  isArrayValue?: boolean;
}[] = [
  { value: "equals", label: "equals", requiresValue: true },
  { value: "not_equals", label: "does not equal", requiresValue: true },
  { value: "contains", label: "contains", requiresValue: true },
  { value: "not_contains", label: "does not contain", requiresValue: true },
  { value: "starts_with", label: "starts with", requiresValue: true },
  { value: "ends_with", label: "ends with", requiresValue: true },
  { value: "greater_than", label: ">", requiresValue: true },
  { value: "less_than", label: "<", requiresValue: true },
  { value: "greater_than_or_equal", label: ">=", requiresValue: true },
  { value: "less_than_or_equal", label: "<=", requiresValue: true },
  { value: "is_null", label: "is null", requiresValue: false },
  { value: "is_not_null", label: "is not null", requiresValue: false },
  { value: "in", label: "in list", requiresValue: true, isArrayValue: true },
  { value: "not_in", label: "not in list", requiresValue: true, isArrayValue: true },
];

// Time range preset display info
export const TIME_RANGE_PRESETS: {
  value: TimeRangePreset;
  label: string;
  athenaExpression: string;
}[] = [
  { 
    value: "last_15_minutes", 
    label: "Last 15 minutes",
    athenaExpression: "date_add('minute', -15, current_timestamp)"
  },
  { 
    value: "last_1_hour", 
    label: "Last 1 hour",
    athenaExpression: "date_add('hour', -1, current_timestamp)"
  },
  { 
    value: "last_6_hours", 
    label: "Last 6 hours",
    athenaExpression: "date_add('hour', -6, current_timestamp)"
  },
  { 
    value: "last_24_hours", 
    label: "Last 24 hours",
    athenaExpression: "date_add('hour', -24, current_timestamp)"
  },
  { 
    value: "last_7_days", 
    label: "Last 7 days",
    athenaExpression: "date_add('day', -7, current_timestamp)"
  },
  { 
    value: "last_30_days", 
    label: "Last 30 days",
    athenaExpression: "date_add('day', -30, current_timestamp)"
  },
  { 
    value: "custom", 
    label: "Custom range",
    athenaExpression: "" // Will be generated from dates
  },
];

// JSON types in Athena
export const JSON_TYPES = ["json", "map", "struct", "row", "array"];

// Helper to check if a type is JSON-like
export function isJsonType(type: string): boolean {
  const lowerType = type.toLowerCase();
  return JSON_TYPES.some((jt) => lowerType.includes(jt));
}

// Helper to categorize column types
export function categorizeColumnType(type: string): EnhancedColumn["category"] {
  const lowerType = type.toLowerCase();
  
  if (isJsonType(type)) return "json";
  if (lowerType.includes("timestamp") || lowerType.includes("date") || lowerType.includes("time")) return "timestamp";
  if (["bigint", "integer", "int", "double", "float", "decimal", "numeric", "long", "smallint", "tinyint"].some((t) => lowerType.includes(t))) return "number";
  if (lowerType.includes("boolean") || lowerType.includes("bool")) return "boolean";
  if (lowerType.includes("varchar") || lowerType.includes("string") || lowerType.includes("char")) return "string";
  
  return "other";
}

// Convert raw columns to enhanced columns
export function enhanceColumns(columns: { name: string; type: string }[]): EnhancedColumn[] {
  return columns.map((col) => ({
    name: col.name,
    type: col.type,
    isJson: isJsonType(col.type),
    category: categorizeColumnType(col.type),
  }));
}

// Quick start templates
export interface QueryTemplate {
  id: string;
  name: string;
  description: string;
  state: Partial<QueryBuilderState>;
}

export const QUERY_TEMPLATES: QueryTemplate[] = [
  {
    id: "count_events",
    name: "Count Events",
    description: "Count total events in time range",
    state: {
      metrics: [{ id: "m1", column: "*", aggregation: "COUNT" }],
      dimensions: [],
      filters: [],
    },
  },
  {
    id: "events_by_name",
    name: "Events by Name",
    description: "Count events grouped by event name",
    state: {
      metrics: [{ id: "m1", column: "*", aggregation: "COUNT" }],
      dimensions: [{ id: "d1", column: "eventName" }],
      filters: [],
    },
  },
  {
    id: "unique_users",
    name: "Unique Users",
    description: "Count unique users",
    state: {
      metrics: [{ id: "m1", column: "userId", aggregation: "COUNT_DISTINCT" }],
      dimensions: [],
      filters: [],
    },
  },
  {
    id: "events_by_platform",
    name: "Events by Platform",
    description: "Count events grouped by platform",
    state: {
      metrics: [{ id: "m1", column: "*", aggregation: "COUNT" }],
      dimensions: [{ id: "d1", column: "platform" }],
      filters: [],
    },
  },
];
