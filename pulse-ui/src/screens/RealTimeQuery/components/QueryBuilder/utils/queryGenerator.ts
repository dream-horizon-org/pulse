/**
 * Query Generator Utility - Simplified Version
 * Converts QueryBuilderState to valid Athena SQL
 * Includes partition-efficient time filtering
 */

import {
  QueryBuilderState,
  QueryColumn,
  FilterCondition,
  FilterOperator,
  TimeRange,
  TimeRangePreset,
} from "../QueryBuilder.interface";

// Partition column names (simplified date/hour structure)
const PARTITION_COLUMNS = {
  date: "date",
  hour: "hour",
};

// Timestamp column
const TIMESTAMP_COLUMN = "timestamp";

/**
 * Escape a string value for SQL
 */
function escapeValue(value: string): string {
  return value.replace(/'/g, "''");
}

/**
 * Calculate start date from a time range preset
 */
function getStartDateFromPreset(preset: TimeRangePreset): Date {
  const now = new Date();
  switch (preset) {
    case "last_15_minutes":
      return new Date(now.getTime() - 15 * 60 * 1000);
    case "last_1_hour":
      return new Date(now.getTime() - 60 * 60 * 1000);
    case "last_6_hours":
      return new Date(now.getTime() - 6 * 60 * 60 * 1000);
    case "last_24_hours":
      return new Date(now.getTime() - 24 * 60 * 60 * 1000);
    case "last_7_days":
      return new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
    case "last_30_days":
      return new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
    default:
      return new Date(now.getTime() - 24 * 60 * 60 * 1000);
  }
}

/**
 * Format a date for SQL TIMESTAMP literal (in UTC)
 */
function formatDateForSql(date: Date): string {
  const year = date.getUTCFullYear();
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  const hours = String(date.getUTCHours()).padStart(2, "0");
  const minutes = String(date.getUTCMinutes()).padStart(2, "0");
  const seconds = String(date.getUTCSeconds()).padStart(2, "0");
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

/**
 * Format a Date to the partition date string 'YYYY-MM-DD' in UTC.
 */
function formatPartitionDate(d: Date): string {
  const y = d.getUTCFullYear();
  const m = String(d.getUTCMonth() + 1).padStart(2, "0");
  const day = String(d.getUTCDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

/**
 * Format a Date to the partition hour string 'HH' in UTC.
 */
function formatPartitionHour(d: Date): string {
  return String(d.getUTCHours()).padStart(2, "0");
}

/**
 * Generate partition-efficient time filter for Athena.
 *
 * Partition layout: date STRING ('YYYY-MM-DD'), hour STRING ('HH')
 * stored at s3://…/vector-logs/YYYY-MM-DD/HH/
 *
 * For same-day queries we can prune both date and hour partitions.
 * For multi-day queries the hour range may wrap (e.g. start 20:00 → end 08:00),
 * so we only prune on the date partition and let the precise timestamp filter
 * handle the hour boundaries.
 */
function generatePartitionTimeFilter(startDate: Date, endDate: Date): string {
  const startDateStr = formatPartitionDate(startDate);
  const endDateStr = formatPartitionDate(endDate);

  const conditions: string[] = [];

  const isSameDay = startDateStr === endDateStr;

  if (isSameDay) {
    conditions.push(`${PARTITION_COLUMNS.date} = '${startDateStr}'`);
    const startHour = formatPartitionHour(startDate);
    const endHour = formatPartitionHour(endDate);
    conditions.push(`${PARTITION_COLUMNS.hour} >= '${startHour}'`);
    conditions.push(`${PARTITION_COLUMNS.hour} <= '${endHour}'`);
  } else {
    conditions.push(`${PARTITION_COLUMNS.date} >= '${startDateStr}'`);
    conditions.push(`${PARTITION_COLUMNS.date} <= '${endDateStr}'`);
  }

  // Precise timestamp filters always applied for exact boundaries
  conditions.push(`${TIMESTAMP_COLUMN} >= TIMESTAMP '${formatDateForSql(startDate)}'`);
  conditions.push(`${TIMESTAMP_COLUMN} <= TIMESTAMP '${formatDateForSql(endDate)}'`);

  return conditions.join(" AND ");
}

/**
 * Generate the time filter expression
 */
function generateTimeFilter(timeRange: TimeRange): string {
  let startDate: Date;
  let endDate: Date;

  if (timeRange.preset === "custom") {
    if (timeRange.startDate && timeRange.endDate) {
      startDate = timeRange.startDate;
      endDate = timeRange.endDate;
    } else {
      // Fallback to last 24 hours if custom dates not set
      endDate = new Date();
      startDate = new Date(endDate.getTime() - 24 * 60 * 60 * 1000);
    }
  } else {
    endDate = new Date();
    startDate = getStartDateFromPreset(timeRange.preset);
  }

  return generatePartitionTimeFilter(startDate, endDate);
}

/**
 * Normalize JSON path to ensure it starts with $ properly
 * Handles both dot notation ($.field) and bracket notation ($["field.with.dots"])
 */
function normalizeJsonPath(path: string): string {
  const trimmed = path.trim();
  
  // Already starts with $ - return as is
  if (trimmed.startsWith("$")) {
    return trimmed;
  }
  
  // Starts with bracket notation - add $ prefix
  if (trimmed.startsWith("[")) {
    return `$${trimmed}`;
  }
  
  // Starts with dot - add $ prefix
  if (trimmed.startsWith(".")) {
    return `$${trimmed}`;
  }
  
  // Plain field name - add $. prefix
  return `$.${trimmed}`;
}

/**
 * Extract a clean alias from a JSON path
 * Handles both dot notation and bracket notation
 */
function extractAliasFromJsonPath(jsonPath: string): string {
  const trimmed = jsonPath.trim();
  
  // Handle bracket notation: ["service.name"] or $["service.name"]
  const bracketMatch = trimmed.match(/\["([^"]+)"\]$/);
  if (bracketMatch) {
    return bracketMatch[1];
  }
  
  // Handle dot notation: $.field.name or field.name
  const cleanPath = trimmed.replace(/^\$\.?/, "");
  const parts = cleanPath.split(".");
  return parts[parts.length - 1] || "";
}

/**
 * Generate a column reference, handling JSON extraction if jsonPath is provided
 */
function generateColumnRef(column: string, jsonPath?: string): string {
  if (jsonPath && jsonPath.trim()) {
    const normalizedPath = normalizeJsonPath(jsonPath);
    return `json_extract_scalar("${column}", '${normalizedPath}')`;
  }
  return `"${column}"`;
}

/**
 * Generate column expression with optional aggregation and JSON extraction
 */
function generateColumnExpression(col: QueryColumn): string {
  const columnRef = generateColumnRef(col.column, col.jsonPath);
  
  let expression: string;
  
  if (col.dataOperation) {
    switch (col.dataOperation) {
      case "COUNT":
        expression = `COUNT(${columnRef})`;
        break;
      case "COUNT_DISTINCT":
        expression = `COUNT(DISTINCT ${columnRef})`;
        break;
      case "SUM":
        expression = `SUM(${columnRef})`;
        break;
      case "AVG":
        expression = `AVG(${columnRef})`;
        break;
      case "MIN":
        expression = `MIN(${columnRef})`;
        break;
      case "MAX":
        expression = `MAX(${columnRef})`;
        break;
      default:
        expression = columnRef;
    }
  } else {
    expression = columnRef;
  }
  
  // Add alias if provided, or generate one for JSON paths
  if (col.alias && col.alias.trim()) {
    return `${expression} AS "${col.alias.trim()}"`;
  } else if (col.jsonPath && col.jsonPath.trim()) {
    // Auto-generate alias for JSON paths
    const autoAlias = extractAliasFromJsonPath(col.jsonPath) || col.column;
    return `${expression} AS "${autoAlias}"`;
  }
  
  return expression;
}

/**
 * Generate filter condition with JSON extraction support
 */
function generateFilterCondition(filter: FilterCondition): string {
  // Use JSON extraction if jsonPath is provided
  const column = generateColumnRef(filter.column, filter.jsonPath);
  const value = filter.value;
  
  const operatorMap: Record<FilterOperator, () => string> = {
    "=": () => `${column} = '${escapeValue(value)}'`,
    "!=": () => `${column} != '${escapeValue(value)}'`,
    ">": () => `${column} > '${escapeValue(value)}'`,
    "<": () => `${column} < '${escapeValue(value)}'`,
    ">=": () => `${column} >= '${escapeValue(value)}'`,
    "<=": () => `${column} <= '${escapeValue(value)}'`,
    "LIKE": () => `${column} LIKE '${escapeValue(value)}'`,
    "NOT LIKE": () => `${column} NOT LIKE '${escapeValue(value)}'`,
    "IN": () => {
      const values = value.split(",").map(v => `'${escapeValue(v.trim())}'`).join(", ");
      return `${column} IN (${values})`;
    },
    "NOT IN": () => {
      const values = value.split(",").map(v => `'${escapeValue(v.trim())}'`).join(", ");
      return `${column} NOT IN (${values})`;
    },
    "IS NULL": () => `${column} IS NULL`,
    "IS NOT NULL": () => `${column} IS NOT NULL`,
  };

  const generator = operatorMap[filter.operator];
  return generator ? generator() : "";
}

/**
 * Main query generation function
 */
export function generateQuery(
  state: QueryBuilderState,
  tableName: string,
  databaseName: string
): string {
  const fullTableName = `${databaseName}.${tableName}`;
  
  // Build SELECT clause
  let selectClause: string;
  
  if (state.columns.length === 0) {
    selectClause = "*";
  } else {
    const validColumns = state.columns.filter(col => col.column);
    if (validColumns.length === 0) {
      selectClause = "*";
    } else {
      selectClause = validColumns.map(generateColumnExpression).join(", ");
    }
  }

  // Build WHERE clause - always starts with time filter
  const whereConditions: string[] = [generateTimeFilter(state.timeRange)];
  
  // Add user filters
  const validFilters = state.filters.filter(f => f.column);
  validFilters.forEach(filter => {
    const condition = generateFilterCondition(filter);
    if (condition) {
      whereConditions.push(condition);
    }
  });

  const whereClause = `WHERE ${whereConditions.join(" AND ")}`;

  // Build GROUP BY clause
  const validGroupBy = state.groupByColumns.filter(col => col);
  let groupByClause = "";
  if (validGroupBy.length > 0) {
    groupByClause = `GROUP BY ${validGroupBy.map(col => `"${col}"`).join(", ")}`;
  }

  // Build ORDER BY clause
  let orderByClause = "";
  if (state.orderByColumn) {
    orderByClause = `ORDER BY "${state.orderByColumn}" ${state.orderDirection}`;
  }

  // Assemble the query
  const queryParts = [
    `SELECT ${selectClause}`,
    `FROM ${fullTableName}`,
    whereClause,
  ];

  if (groupByClause) {
    queryParts.push(groupByClause);
  }

  if (orderByClause) {
    queryParts.push(orderByClause);
  }

  // Add LIMIT clause only if limit is defined
  if (state.limit !== undefined && state.limit > 0) {
    queryParts.push(`LIMIT ${state.limit}`);
  }

  return queryParts.join(" ") + ";";
}

/**
 * Validate the query builder state
 */
export function validateQueryBuilderState(state: QueryBuilderState): string[] {
  const errors: string[] = [];

  // Validate time range for custom
  if (state.timeRange.preset === "custom") {
    if (!state.timeRange.startDate) {
      errors.push("Start date is required for custom time range");
    }
    if (!state.timeRange.endDate) {
      errors.push("End date is required for custom time range");
    }
    if (state.timeRange.startDate && state.timeRange.endDate) {
      if (state.timeRange.startDate >= state.timeRange.endDate) {
        errors.push("Start date must be before end date");
      }
    }
  }

  // Validate limit (only if provided)
  if (state.limit !== undefined && (state.limit < 1 || state.limit > 10000)) {
    errors.push("Limit must be between 1 and 10,000");
  }

  // Validate filters have values where required
  state.filters.forEach((filter, index) => {
    if (!filter.column) {
      errors.push(`Filter ${index + 1}: Column is required`);
    }
    
    const needsValue = !["IS NULL", "IS NOT NULL"].includes(filter.operator);
    if (needsValue && (!filter.value || filter.value.trim() === "")) {
        errors.push(`Filter ${index + 1}: Value is required`);
    }
  });

  // Validate columns
  state.columns.forEach((col, index) => {
      if (!col.column) {
      errors.push(`Column ${index + 1}: Column selection is required`);
    }
  });

  return errors;
}

/**
 * Generate a plain English description of the query
 */
export function generateQueryDescription(state: QueryBuilderState): string {
  const parts: string[] = [];

  // Columns
  if (state.columns.length === 0) {
      parts.push("Select all columns");
  } else {
    const colCount = state.columns.filter(c => c.column).length;
    const aggCount = state.columns.filter(c => c.dataOperation).length;
    if (aggCount > 0) {
      parts.push(`${aggCount} aggregation(s)`);
    }
    if (colCount > aggCount) {
      parts.push(`${colCount - aggCount} column(s)`);
    }
  }

  // Time range
  parts.push(`for ${state.timeRange.preset.replace(/_/g, " ")}`);

  // Filters
  const filterCount = state.filters.filter(f => f.column).length;
  if (filterCount > 0) {
    parts.push(`${filterCount} filter(s)`);
  }

  // Group by
  const groupCount = state.groupByColumns.filter(c => c).length;
  if (groupCount > 0) {
    parts.push(`grouped by ${groupCount} column(s)`);
  }

  // Order
  if (state.orderByColumn) {
    parts.push(`ordered by ${state.orderByColumn} ${state.orderDirection}`);
  }

  // Limit
  if (state.limit !== undefined && state.limit > 0) {
    parts.push(`limit ${state.limit}`);
  }

  return parts.join(", ");
}
