/**
 * Query Generator Utility
 * Converts QueryBuilderState to valid Athena SQL
 * 
 * Key features:
 * - Always generates time-bound queries
 * - Proper escaping of values
 * - Valid Athena SQL syntax
 * - JSON path extraction support
 */

import {
  QueryBuilderState,
  TimeRange,
  TimeRangePreset,
  Metric,
  Dimension,
  Filter,
  FilterOperator,
  TIME_RANGE_PRESETS,
  SortConfig,
  EnhancedColumn,
  AGGREGATION_OPTIONS,
  SelectedColumn,
} from "../QueryBuilder.interface";

// Default timestamp column name
const TIMESTAMP_COLUMN = "timestamp";

// Partition column names
const PARTITION_COLUMNS = {
  year: "year",
  month: "month",
  day: "day",
  hour: "hour",
};

/**
 * Escape a string value for SQL
 */
function escapeValue(value: string): string {
  // Escape single quotes by doubling them
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
 * Generate partition-efficient time filter for Athena
 * Uses tuple comparison for partition pruning + precise timestamp filter
 * All values are in UTC to match S3 data storage format
 * 
 * Example output:
 *   (year, month, day, hour) >= (2026, 1, 1, 5) AND (year, month, day, hour) <= (2026, 1, 7, 15)
 *   AND timestamp >= TIMESTAMP '2026-01-01 05:00:00' AND timestamp <= TIMESTAMP '2026-01-07 15:00:00'
 */
function generatePartitionTimeFilter(startDate: Date, endDate: Date): string {
  // Use UTC values to match S3 data storage format
  const startYear = startDate.getUTCFullYear();
  const startMonth = startDate.getUTCMonth() + 1;
  const startDay = startDate.getUTCDate();
  const startHour = startDate.getUTCHours();

  const endYear = endDate.getUTCFullYear();
  const endMonth = endDate.getUTCMonth() + 1;
  const endDay = endDate.getUTCDate();
  const endHour = endDate.getUTCHours();

  // Tuple comparison for partition columns
  const partitionTuple = `(${PARTITION_COLUMNS.year}, ${PARTITION_COLUMNS.month}, ${PARTITION_COLUMNS.day}, ${PARTITION_COLUMNS.hour})`;
  const startTuple = `(${startYear}, ${startMonth}, ${startDay}, ${startHour})`;
  const endTuple = `(${endYear}, ${endMonth}, ${endDay}, ${endHour})`;

  const conditions: string[] = [];

  // Partition tuple comparison
  conditions.push(`${partitionTuple} >= ${startTuple}`);
  conditions.push(`${partitionTuple} <= ${endTuple}`);

  // Precise timestamp filters (always included for accuracy)
  conditions.push(`${TIMESTAMP_COLUMN} >= TIMESTAMP '${formatDateForSql(startDate)}'`);
  conditions.push(`${TIMESTAMP_COLUMN} <= TIMESTAMP '${formatDateForSql(endDate)}'`);

  return conditions.join(" AND ");
}

/**
 * Generate column reference with optional JSON extraction
 * For JSON columns, uses json_extract_scalar
 */
function generateColumnRef(column: string, jsonPath?: string): string {
  if (jsonPath && jsonPath.trim()) {
    // Use json_extract_scalar for JSON path extraction
    // jsonPath should be like '$.field' or '$.nested.field'
    const cleanPath = jsonPath.startsWith("$.") ? jsonPath : `$.${jsonPath}`;
    return `json_extract_scalar("${column}", '${cleanPath}')`;
  }
  return `"${column}"`;
}

/**
 * Generate the time filter expression for Athena
 * Uses partition-efficient filtering with (year, month, day, hour) tuple comparisons
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
    // For preset time ranges, calculate actual dates
    endDate = new Date();
    startDate = getStartDateFromPreset(timeRange.preset);
  }

  return generatePartitionTimeFilter(startDate, endDate);
}

/**
 * Check if a column type requires casting for numeric aggregations
 */
function requiresNumericCast(columnCategory: EnhancedColumn["category"] | undefined, columnType: string | undefined): boolean {
  // If it's already a number, no cast needed
  if (columnCategory === "number") return false;
  
  // Check the raw type for numeric types that might not be categorized correctly
  if (columnType) {
    const lowerType = columnType.toLowerCase();
    if (["bigint", "integer", "int", "double", "float", "decimal", "numeric", "long", "smallint", "tinyint"].some(t => lowerType.includes(t))) {
      return false;
    }
  }
  
  // All other types need casting
  return true;
}

/**
 * Generate SELECT clause for a metric with type-aware casting
 */
function generateMetricSelect(metric: Metric, columnInfo?: EnhancedColumn): string {
  const columnRef = metric.column === "*" ? "*" : generateColumnRef(metric.column, metric.jsonPath);
  const alias = metric.alias || generateMetricAlias(metric);
  
  // Determine if we need to cast to numeric
  const needsCast = metric.column !== "*" && 
    requiresNumericCast(columnInfo?.category, columnInfo?.type) && 
    (metric.jsonPath || columnInfo?.category === "string" || columnInfo?.category === "json");
  
  // Get aggregation info
  const aggOption = AGGREGATION_OPTIONS.find(opt => opt.value === metric.aggregation);
  const requiresNumeric = aggOption?.requiresNumeric ?? false;
  
  switch (metric.aggregation) {
    case "COUNT":
      return `COUNT(${columnRef}) AS "${alias}"`;
    case "COUNT_DISTINCT":
      return `COUNT(DISTINCT ${columnRef}) AS "${alias}"`;
    case "SUM":
      // Cast to DOUBLE if column is not numeric
      if (needsCast || (requiresNumeric && columnInfo?.category !== "number")) {
        return `SUM(TRY_CAST(${columnRef} AS DOUBLE)) AS "${alias}"`;
      }
      return `SUM(${columnRef}) AS "${alias}"`;
    case "AVG":
      // Cast to DOUBLE if column is not numeric
      if (needsCast || (requiresNumeric && columnInfo?.category !== "number")) {
        return `AVG(TRY_CAST(${columnRef} AS DOUBLE)) AS "${alias}"`;
      }
      return `AVG(${columnRef}) AS "${alias}"`;
    case "MIN":
      // MIN/MAX work on strings too (lexicographic), but if user expects numeric, cast it
      if (requiresNumeric && needsCast) {
        return `MIN(TRY_CAST(${columnRef} AS DOUBLE)) AS "${alias}"`;
      }
      return `MIN(${columnRef}) AS "${alias}"`;
    case "MAX":
      if (requiresNumeric && needsCast) {
        return `MAX(TRY_CAST(${columnRef} AS DOUBLE)) AS "${alias}"`;
      }
      return `MAX(${columnRef}) AS "${alias}"`;
    default:
      return `COUNT(${columnRef}) AS "${alias}"`;
  }
}

/**
 * Generate alias for a metric
 */
function generateMetricAlias(metric: Metric): string {
  const baseName = metric.jsonPath 
    ? `${metric.column}_${metric.jsonPath.replace(/\$\./g, "").replace(/\./g, "_")}`
    : metric.column;
  return `${metric.aggregation.toLowerCase()}_${baseName}`;
}

/**
 * Generate SELECT clause for a dimension
 */
function generateDimensionSelect(dimension: Dimension): string {
  const columnRef = generateColumnRef(dimension.column, dimension.jsonPath);
  const alias = dimension.alias || generateDimensionAlias(dimension);
  return `${columnRef} AS "${alias}"`;
}

/**
 * Generate alias for a dimension
 */
function generateDimensionAlias(dimension: Dimension): string {
  if (dimension.jsonPath) {
    return `${dimension.column}_${dimension.jsonPath.replace(/\$\./g, "").replace(/\./g, "_")}`;
  }
  return dimension.column;
}

/**
 * Generate SELECT clause for a selected column (simple select mode)
 */
function generateSelectedColumnSelect(col: SelectedColumn): string {
  const columnRef = generateColumnRef(col.column, col.jsonPath);
  if (col.alias) {
    return `${columnRef} AS "${col.alias}"`;
  }
  return columnRef;
}

/**
 * Generate ORDER BY clause for select mode
 */
function generateSelectModeOrderBy(sortBy: SortConfig | undefined, selectedColumns: SelectedColumn[]): string {
  if (sortBy) {
    return `ORDER BY "${sortBy.column}" ${sortBy.direction}`;
  }
  
  // Default: order by timestamp DESC for select queries
  return `ORDER BY "${TIMESTAMP_COLUMN}" DESC`;
}

/**
 * Generate filter condition
 */
function generateFilterCondition(filter: Filter): string {
  const column = generateColumnRef(filter.column, filter.jsonPath);
  const value = Array.isArray(filter.value) ? filter.value : [filter.value];
  
  const operatorMap: Record<FilterOperator, (col: string, val: string[]) => string> = {
    equals: (col, val) => `${col} = '${escapeValue(val[0])}'`,
    not_equals: (col, val) => `${col} != '${escapeValue(val[0])}'`,
    contains: (col, val) => `${col} LIKE '%${escapeValue(val[0])}%'`,
    not_contains: (col, val) => `${col} NOT LIKE '%${escapeValue(val[0])}%'`,
    starts_with: (col, val) => `${col} LIKE '${escapeValue(val[0])}%'`,
    ends_with: (col, val) => `${col} LIKE '%${escapeValue(val[0])}'`,
    greater_than: (col, val) => `${col} > '${escapeValue(val[0])}'`,
    less_than: (col, val) => `${col} < '${escapeValue(val[0])}'`,
    greater_than_or_equal: (col, val) => `${col} >= '${escapeValue(val[0])}'`,
    less_than_or_equal: (col, val) => `${col} <= '${escapeValue(val[0])}'`,
    is_null: (col) => `${col} IS NULL`,
    is_not_null: (col) => `${col} IS NOT NULL`,
    in: (col, val) => `${col} IN (${val.map((v) => `'${escapeValue(v)}'`).join(", ")})`,
    not_in: (col, val) => `${col} NOT IN (${val.map((v) => `'${escapeValue(v)}'`).join(", ")})`,
  };

  const generator = operatorMap[filter.operator];
  return generator ? generator(column, value) : "";
}

/**
 * Generate ORDER BY clause
 */
function generateOrderBy(sortBy: SortConfig | undefined, metrics: Metric[]): string {
  if (sortBy) {
    return `ORDER BY "${sortBy.column}" ${sortBy.direction}`;
  }
  
  // Default: order by first metric DESC
  if (metrics.length > 0) {
    const firstMetric = metrics[0];
    const alias = firstMetric.alias || generateMetricAlias(firstMetric);
    return `ORDER BY "${alias}" DESC`;
  }
  
  return "";
}

/**
 * Main query generation function
 * Generates a complete, valid Athena SQL query
 * Supports both aggregate mode (with GROUP BY) and select mode (simple column selection)
 */
export function generateQuery(
  state: QueryBuilderState,
  tableName: string,
  databaseName: string,
  columns?: EnhancedColumn[]
): string {
  const fullTableName = `${databaseName}.${tableName}`;
  
  // Create a map for quick column lookup
  const columnMap = new Map<string, EnhancedColumn>();
  if (columns) {
    columns.forEach(col => columnMap.set(col.name, col));
  }
  
  // Handle select mode (simple column selection without aggregation)
  if (state.queryMode === "select") {
    return generateSelectModeQuery(state, fullTableName);
  }
  
  // Aggregate mode: Don't generate query if no metrics and no dimensions are specified
  if (state.metrics.length === 0 && state.dimensions.length === 0) {
    return "";
  }
  
  // If only dimensions are specified (no metrics), generate a simple count with grouping
  if (state.metrics.length === 0) {
    return generateSimpleQuery(state, fullTableName);
  }

  // Build SELECT clause
  const selectParts: string[] = [];
  
  // Add dimensions first
  state.dimensions.forEach((dim) => {
    selectParts.push(generateDimensionSelect(dim));
  });
  
  // Add metrics with column type information
  state.metrics.forEach((metric) => {
    const columnInfo = metric.column === "*" ? undefined : columnMap.get(metric.column);
    selectParts.push(generateMetricSelect(metric, columnInfo));
  });

  const selectClause = selectParts.join(", ");

  // Build WHERE clause (always includes time filter)
  const whereConditions: string[] = [generateTimeFilter(state.timeRange)];
  
  // Add user filters
  state.filters.forEach((filter) => {
    const condition = generateFilterCondition(filter);
    if (condition) {
      whereConditions.push(condition);
    }
  });

  const whereClause = whereConditions.join(" AND ");

  // Build GROUP BY clause
  let groupByClause = "";
  if (state.dimensions.length > 0) {
    const groupByColumns = state.dimensions.map((dim) => generateColumnRef(dim.column, dim.jsonPath)).join(", ");
    groupByClause = `GROUP BY ${groupByColumns}`;
  }

  // Build ORDER BY clause
  const orderByClause = generateOrderBy(state.sortBy, state.metrics);

  // Build LIMIT clause
  const limitClause = `LIMIT ${state.limit}`;

  // Assemble the query (no newlines for Athena compatibility)
  const queryParts = [
    `SELECT ${selectClause}`,
    `FROM ${fullTableName}`,
    `WHERE ${whereClause}`,
  ];

  if (groupByClause) {
    queryParts.push(groupByClause);
  }

  if (orderByClause) {
    queryParts.push(orderByClause);
  }

  queryParts.push(limitClause);

  return queryParts.join(" ") + ";";
}

/**
 * Generate a SELECT mode query (simple column selection without aggregation)
 */
function generateSelectModeQuery(state: QueryBuilderState, fullTableName: string): string {
  // Build SELECT clause
  let selectClause: string;
  
  if (state.selectedColumns.length === 0) {
    // If no columns selected, select all
    selectClause = "*";
  } else {
    const selectParts = state.selectedColumns.map((col) => generateSelectedColumnSelect(col));
    selectClause = selectParts.join(", ");
  }

  // Build WHERE clause (always includes time filter)
  const whereConditions: string[] = [generateTimeFilter(state.timeRange)];
  
  // Add user filters
  state.filters.forEach((filter) => {
    const condition = generateFilterCondition(filter);
    if (condition) {
      whereConditions.push(condition);
    }
  });

  const whereClause = whereConditions.join(" AND ");

  // Build ORDER BY clause
  const orderByClause = generateSelectModeOrderBy(state.sortBy, state.selectedColumns);

  // Build LIMIT clause
  const limitClause = `LIMIT ${state.limit}`;

  // Assemble the query (no newlines for Athena compatibility)
  const queryParts = [
    `SELECT ${selectClause}`,
    `FROM ${fullTableName}`,
    `WHERE ${whereClause}`,
    orderByClause,
    limitClause,
  ];

  return queryParts.join(" ") + ";";
}

/**
 * Generate a simple query when no metrics are specified
 * Returns a count of rows within the time range
 */
function generateSimpleQuery(state: QueryBuilderState, fullTableName: string): string {
  // Build WHERE clause
  const whereConditions: string[] = [generateTimeFilter(state.timeRange)];
  
  state.filters.forEach((filter) => {
    const condition = generateFilterCondition(filter);
    if (condition) {
      whereConditions.push(condition);
    }
  });

  const whereClause = whereConditions.join(" AND ");

  // If dimensions are specified, group by them
  if (state.dimensions.length > 0) {
    const selectParts = state.dimensions.map((dim) => generateDimensionSelect(dim));
    selectParts.push('COUNT(*) AS "count"');
    
    const groupByColumns = state.dimensions.map((dim) => generateColumnRef(dim.column, dim.jsonPath)).join(", ");
    
    return [
      `SELECT ${selectParts.join(", ")}`,
      `FROM ${fullTableName}`,
      `WHERE ${whereClause}`,
      `GROUP BY ${groupByColumns}`,
      `ORDER BY "count" DESC`,
      `LIMIT ${state.limit};`,
    ].join(" ");
  }

  // Simple count query
  return [
    `SELECT COUNT(*) AS "total_count"`,
    `FROM ${fullTableName}`,
    `WHERE ${whereClause}`,
    `LIMIT 1;`,
  ].join(" ");
}

/**
 * Validate the query builder state
 * Returns an array of validation errors (empty if valid)
 */
export function validateQueryBuilderState(state: QueryBuilderState): string[] {
  const errors: string[] = [];

  // Time range is always required (but has a default, so this is mainly for custom)
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

  // Validate limit
  if (state.limit < 1 || state.limit > 10000) {
    errors.push("Limit must be between 1 and 10,000");
  }

  // Validate filters have values where required
  state.filters.forEach((filter, index) => {
    if (!filter.column) {
      errors.push(`Filter ${index + 1}: Column is required`);
    }
    
    const needsValue = !["is_null", "is_not_null"].includes(filter.operator);
    if (needsValue) {
      const hasValue = Array.isArray(filter.value) 
        ? filter.value.length > 0 && filter.value.some((v) => v.trim() !== "")
        : filter.value && filter.value.trim() !== "";
      
      if (!hasValue) {
        errors.push(`Filter ${index + 1}: Value is required`);
      }
    }
  });

  // Mode-specific validation
  if (state.queryMode === "select") {
    // Select mode: validate selected columns
    state.selectedColumns.forEach((col, index) => {
      if (!col.column) {
        errors.push(`Selected column ${index + 1}: Column is required`);
      }
    });
  } else {
    // Aggregate mode: validate metrics and dimensions
    state.metrics.forEach((metric, index) => {
      if (!metric.column) {
        errors.push(`Metric ${index + 1}: Column is required`);
      }
    });

    state.dimensions.forEach((dimension, index) => {
      if (!dimension.column) {
        errors.push(`Dimension ${index + 1}: Column is required`);
      }
    });
  }

  return errors;
}

/**
 * Generate a preview/description of the query in plain English
 */
export function generateQueryDescription(state: QueryBuilderState): string {
  const parts: string[] = [];

  if (state.queryMode === "select") {
    // Select mode description
    if (state.selectedColumns.length > 0) {
      const colNames = state.selectedColumns.map((c) => 
        c.jsonPath ? `${c.column}.${c.jsonPath.replace("$.", "")}` : c.column
      );
      if (colNames.length <= 3) {
        parts.push(`Select ${colNames.join(", ")}`);
      } else {
        parts.push(`Select ${colNames.length} columns`);
      }
    } else {
      parts.push("Select all columns");
    }
  } else {
    // Aggregate mode description
    if (state.metrics.length > 0) {
      const metricDescs = state.metrics.map((m) => {
        const colName = m.jsonPath ? `${m.column}.${m.jsonPath.replace("$.", "")}` : m.column;
        if (m.aggregation === "COUNT" && m.column === "*") {
          return "count of all rows";
        }
        if (m.aggregation === "COUNT_DISTINCT") {
          return `count of unique ${colName}`;
        }
        return `${m.aggregation.toLowerCase()} of ${colName}`;
      });
      parts.push(`Get ${metricDescs.join(", ")}`);
    } else {
      parts.push("Count rows");
    }

    // Dimensions
    if (state.dimensions.length > 0) {
      const dimNames = state.dimensions.map((d) => 
        d.jsonPath ? `${d.column}.${d.jsonPath.replace("$.", "")}` : d.column
      );
      parts.push(`grouped by ${dimNames.join(", ")}`);
    }
  }

  // Time range
  const timePreset = TIME_RANGE_PRESETS.find((p) => p.value === state.timeRange.preset);
  if (timePreset && state.timeRange.preset !== "custom") {
    parts.push(`for ${timePreset.label.toLowerCase()}`);
  } else if (state.timeRange.preset === "custom") {
    if (state.timeRange.startDate && state.timeRange.endDate) {
      const formatDate = (d: Date) => d.toLocaleDateString() + " " + d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
      parts.push(`from ${formatDate(state.timeRange.startDate)} to ${formatDate(state.timeRange.endDate)}`);
    } else {
      parts.push("for custom time range (select dates)");
    }
  }

  // Filters
  if (state.filters.length > 0) {
    parts.push(`with ${state.filters.length} filter(s)`);
  }

  // Limit
  parts.push(`(max ${state.limit} rows)`);

  return parts.join(" ");
}
