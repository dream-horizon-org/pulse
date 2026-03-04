/**
 * QueryBuilder Interfaces - Simplified Grafana-style
 * Clean, unified query builder types
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
  startDate?: Date;
  endDate?: Date;
}

// Aggregation functions (Data operations)
export type DataOperation =
  | "COUNT"
  | "COUNT_DISTINCT"
  | "SUM"
  | "AVG"
  | "MIN"
  | "MAX";

// Sort direction
export type SortDirection = "ASC" | "DESC";

// Filter operators
export type FilterOperator =
  | "="
  | "!="
  | ">"
  | "<"
  | ">="
  | "<="
  | "LIKE"
  | "NOT LIKE"
  | "IN"
  | "NOT IN"
  | "IS NULL"
  | "IS NOT NULL";

/**
 * Query column - combines column selection with optional data operation
 * Supports JSON field extraction via jsonPath
 */
export interface QueryColumn {
  id: string;
  column: string;
  dataOperation?: DataOperation;
  alias?: string;
  /** JSON path for extracting fields from JSON columns (e.g., "$.fieldName" or "fieldName") */
  jsonPath?: string;
}

/**
 * Filter condition
 * Supports filtering on JSON fields via jsonPath
 */
export interface FilterCondition {
  id: string;
  column: string;
  operator: FilterOperator;
  value: string;
  /** JSON path for filtering on JSON column fields (e.g., "$.fieldName" or "fieldName") */
  jsonPath?: string;
}

/**
 * Complete query builder state - simplified
 */
export interface QueryBuilderState {
  // Data source
  tableName: string;
  databaseName: string;
  
  // Time range (required, uses partition columns)
  timeRange: TimeRange;
  
  // Query columns (with optional aggregation)
  columns: QueryColumn[];
  
  // Filters
  filters: FilterCondition[];
  
  // Group by
  groupByColumns: string[];
  
  // Order by
  orderByColumn: string;
  orderDirection: SortDirection;
  
  // Limit (optional - undefined means no limit)
  limit?: number;
  
  // Section visibility toggles
  showFilter: boolean;
  showGroupBy: boolean;
  showOrderBy: boolean;
}

/**
 * Default state
 */
export const DEFAULT_QUERY_BUILDER_STATE: QueryBuilderState = {
  tableName: "",
  databaseName: "",
  timeRange: {
    preset: "last_24_hours",
  },
  columns: [],
  filters: [],
  groupByColumns: [],
  orderByColumn: "",
  orderDirection: "DESC",
  limit: undefined,
  showFilter: true,
  showGroupBy: true,
  showOrderBy: true,
};

/**
 * Props for the QueryBuilder component
 */
export interface QueryBuilderProps {
  tableName: string;
  databaseName: string;
  columns: { columnName: string; dataType: string }[];
  onQueryChange: (sql: string) => void;
  isLoading?: boolean;
}

/**
 * Column with metadata for UI
 */
export interface EnhancedColumn {
  name: string;
  type: string;
  isNumeric: boolean;
  isJson: boolean;
}

/**
 * Data operation options for dropdown
 */
export const DATA_OPERATIONS: { value: DataOperation; label: string }[] = [
  { value: "COUNT", label: "Count" },
  { value: "COUNT_DISTINCT", label: "Count distinct" },
  { value: "SUM", label: "Sum" },
  { value: "AVG", label: "Avg" },
  { value: "MIN", label: "Min" },
  { value: "MAX", label: "Max" },
];

/**
 * Filter operator options
 */
export const FILTER_OPERATORS: { value: FilterOperator; label: string }[] = [
  { value: "=", label: "=" },
  { value: "!=", label: "!=" },
  { value: ">", label: ">" },
  { value: "<", label: "<" },
  { value: ">=", label: ">=" },
  { value: "<=", label: "<=" },
  { value: "LIKE", label: "LIKE" },
  { value: "NOT LIKE", label: "NOT LIKE" },
  { value: "IN", label: "IN" },
  { value: "NOT IN", label: "NOT IN" },
  { value: "IS NULL", label: "IS NULL" },
  { value: "IS NOT NULL", label: "IS NOT NULL" },
];

/**
 * Time range preset options
 */
export const TIME_RANGE_PRESETS: { value: TimeRangePreset; label: string }[] = [
  { value: "last_15_minutes", label: "Last 15 minutes" },
  { value: "last_1_hour", label: "Last 1 hour" },
  { value: "last_6_hours", label: "Last 6 hours" },
  { value: "last_24_hours", label: "Last 24 hours" },
  { value: "last_7_days", label: "Last 7 days" },
  { value: "last_30_days", label: "Last 30 days" },
  { value: "custom", label: "Custom range" },
];

/**
 * Helper to check if a type is numeric
 */
export function isNumericType(type: string): boolean {
  const lowerType = type.toLowerCase();
  return ["bigint", "integer", "int", "double", "float", "decimal", "numeric", "long", "smallint", "tinyint"]
    .some(t => lowerType.includes(t));
}

/**
 * Helper to check if a column type is JSON
 * In Athena, JSON data is typically stored as STRING, VARCHAR, or MAP/STRUCT types
 * We'll also check for common JSON column naming patterns
 */
export function isJsonColumn(columnName: string, dataType: string): boolean {
  const lowerType = dataType.toLowerCase();
  const lowerName = columnName.toLowerCase();
  
  // Check data type indicators
  const jsonTypes = ["json", "map", "struct", "array"];
  const isJsonType = jsonTypes.some(t => lowerType.includes(t));
  
  // Check common JSON column naming patterns
  const jsonNamePatterns = ["props", "properties", "metadata", "attributes", "data", "payload", "context", "extra", "custom", "config", "settings"];
  const hasJsonName = jsonNamePatterns.some(pattern => lowerName.includes(pattern));
  
  return isJsonType || hasJsonName;
}

/** Partition columns managed by the Time Range picker — excluded from user-selectable lists */
const PARTITION_COLUMN_NAMES = new Set(["date", "hour"]);

/**
 * Enhance raw columns with metadata.
 * Partition columns (date, hour) are excluded because the Time Range section
 * generates their filters automatically.
 */
export function enhanceColumns(columns: { columnName: string; dataType: string }[] | undefined | null): EnhancedColumn[] {
  if (!columns || !Array.isArray(columns)) return [];
  return columns
    .filter(col => col && col.columnName && !PARTITION_COLUMN_NAMES.has(col.columnName))
    .map(col => ({
      name: col.columnName,
      type: col.dataType || "unknown",
      isNumeric: isNumericType(col.dataType || ""),
      isJson: isJsonColumn(col.columnName, col.dataType || ""),
    }));
}
