// Data source types
export type DataSourceType = "events" | "users" | "sessions";

export interface DataSource {
  id: DataSourceType;
  label: string;
  description: string;
  icon: string;
  estimatedRows?: number;
  lastUpdated?: string;
}

// Filter types
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
  | "in"
  | "not_in"
  | "is_null"
  | "is_not_null";

export type FilterLogic = "AND" | "OR";

export interface FilterCondition {
  id: string;
  field: string;
  operator: FilterOperator;
  value: string | string[] | number | null;
}

export interface FilterGroup {
  id: string;
  logic: FilterLogic;
  conditions: FilterCondition[];
}

// Metric types
export type AggregationType =
  | "count"
  | "count_distinct"
  | "sum"
  | "avg"
  | "min"
  | "max"
  | "percentile_50"
  | "percentile_90"
  | "percentile_95"
  | "percentile_99";

export interface Metric {
  id: string;
  aggregation: AggregationType;
  field: string | null; // null for COUNT(*)
  alias?: string;
}

// Group by types
export type DateGranularity =
  | "minute"
  | "hour"
  | "day"
  | "week"
  | "month"
  | "quarter"
  | "year";

export interface GroupByDimension {
  id: string;
  field: string;
  granularity?: DateGranularity; // Only for date fields
  limit?: number;
}

// Time range types
export interface TimeRange {
  type: "preset" | "custom";
  preset?: string;
  startDate?: Date;
  endDate?: Date;
}

// Visualization types
export type ChartType = "line" | "bar" | "pie" | "area" | "table";

export interface VisualizationConfig {
  chartType: ChartType;
  showLegend: boolean;
  stacked?: boolean;
  colorScheme?: string;
}

// Query state
export interface QueryState {
  dataSource: DataSourceType;
  metrics: Metric[];
  filters: FilterGroup;
  groupBy: GroupByDimension[];
  timeRange: TimeRange;
  visualization: VisualizationConfig;
  orderBy?: {
    field: string;
    direction: "asc" | "desc";
  };
  limit?: number;
}

// Field metadata
export interface FieldMetadata {
  name: string;
  type: "string" | "number" | "date" | "boolean" | "array" | "object";
  description?: string;
  isNullable?: boolean;
  sampleValues?: string[];
}

// Query results
export interface QueryResultColumn {
  name: string;
  type: string;
}

export interface QueryResultRow {
  [key: string]: string | number | boolean | null;
}

export interface QueryResult {
  columns: QueryResultColumn[];
  rows: QueryResultRow[];
  totalRows: number;
  executionTimeMs: number;
  hasMore: boolean;
  pageToken?: string;
}

// Saved query
export interface SavedQuery {
  id: string;
  name: string;
  description?: string;
  query: QueryState;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
  tags?: string[];
  isFavorite?: boolean;
}

// Component Props
export interface RealTimeQueryProps {
  initialQuery?: Partial<QueryState>;
}

