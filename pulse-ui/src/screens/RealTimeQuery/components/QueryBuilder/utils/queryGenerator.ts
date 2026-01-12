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
  Metric,
  Dimension,
  Filter,
  FilterOperator,
  TIME_RANGE_PRESETS,
  SortConfig,
} from "../QueryBuilder.interface";

// Default timestamp column name
const TIMESTAMP_COLUMN = "timestamp";

/**
 * Escape a string value for SQL
 */
function escapeValue(value: string): string {
  // Escape single quotes by doubling them
  return value.replace(/'/g, "''");
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
 */
function generateTimeFilter(timeRange: TimeRange): string {
  if (timeRange.preset === "custom") {
    if (timeRange.startDate && timeRange.endDate) {
      const startStr = timeRange.startDate.toISOString();
      const endStr = timeRange.endDate.toISOString();
      return `${TIMESTAMP_COLUMN} >= timestamp '${startStr}' AND ${TIMESTAMP_COLUMN} <= timestamp '${endStr}'`;
    }
    // Fallback to last 24 hours if custom dates not set
    return `${TIMESTAMP_COLUMN} >= date_add('hour', -24, current_timestamp)`;
  }

  const preset = TIME_RANGE_PRESETS.find((p) => p.value === timeRange.preset);
  if (preset) {
    return `${TIMESTAMP_COLUMN} >= ${preset.athenaExpression}`;
  }

  // Default fallback
  return `${TIMESTAMP_COLUMN} >= date_add('hour', -24, current_timestamp)`;
}

/**
 * Generate SELECT clause for a metric
 */
function generateMetricSelect(metric: Metric): string {
  const columnRef = metric.column === "*" ? "*" : generateColumnRef(metric.column, metric.jsonPath);
  const alias = metric.alias || generateMetricAlias(metric);
  
  switch (metric.aggregation) {
    case "COUNT":
      return `COUNT(${columnRef}) AS "${alias}"`;
    case "COUNT_DISTINCT":
      return `COUNT(DISTINCT ${columnRef}) AS "${alias}"`;
    case "SUM":
      // For JSON fields, we need to cast to double
      const sumCol = metric.jsonPath ? `CAST(${columnRef} AS DOUBLE)` : columnRef;
      return `SUM(${sumCol}) AS "${alias}"`;
    case "AVG":
      const avgCol = metric.jsonPath ? `CAST(${columnRef} AS DOUBLE)` : columnRef;
      return `AVG(${avgCol}) AS "${alias}"`;
    case "MIN":
      return `MIN(${columnRef}) AS "${alias}"`;
    case "MAX":
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
 */
export function generateQuery(
  state: QueryBuilderState,
  tableName: string,
  databaseName: string
): string {
  const fullTableName = `${databaseName}.${tableName}`;
  
  // Don't generate query if no metrics and no dimensions are specified
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
  
  // Add metrics
  state.metrics.forEach((metric) => {
    selectParts.push(generateMetricSelect(metric));
  });

  const selectClause = selectParts.join(",\n    ");

  // Build WHERE clause (always includes time filter)
  const whereConditions: string[] = [generateTimeFilter(state.timeRange)];
  
  // Add user filters
  state.filters.forEach((filter) => {
    const condition = generateFilterCondition(filter);
    if (condition) {
      whereConditions.push(condition);
    }
  });

  const whereClause = whereConditions.join("\n    AND ");

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

  // Assemble the query
  const queryParts = [
    `SELECT`,
    `    ${selectClause}`,
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

  return queryParts.join("\n") + ";";
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

  const whereClause = whereConditions.join("\n    AND ");

  // If dimensions are specified, group by them
  if (state.dimensions.length > 0) {
    const selectParts = state.dimensions.map((dim) => generateDimensionSelect(dim));
    selectParts.push('COUNT(*) AS "count"');
    
    const groupByColumns = state.dimensions.map((dim) => generateColumnRef(dim.column, dim.jsonPath)).join(", ");
    
    return [
      `SELECT`,
      `    ${selectParts.join(",\n    ")}`,
      `FROM ${fullTableName}`,
      `WHERE ${whereClause}`,
      `GROUP BY ${groupByColumns}`,
      `ORDER BY "count" DESC`,
      `LIMIT ${state.limit};`,
    ].join("\n");
  }

  // Simple count query
  return [
    `SELECT COUNT(*) AS "total_count"`,
    `FROM ${fullTableName}`,
    `WHERE ${whereClause}`,
    `LIMIT 1;`,
  ].join("\n");
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

  // Validate metrics have columns
  state.metrics.forEach((metric, index) => {
    if (!metric.column) {
      errors.push(`Metric ${index + 1}: Column is required`);
    }
  });

  // Validate dimensions have columns
  state.dimensions.forEach((dimension, index) => {
    if (!dimension.column) {
      errors.push(`Dimension ${index + 1}: Column is required`);
    }
  });

  return errors;
}

/**
 * Generate a preview/description of the query in plain English
 */
export function generateQueryDescription(state: QueryBuilderState): string {
  const parts: string[] = [];

  // Metrics
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

  // Time range
  const timePreset = TIME_RANGE_PRESETS.find((p) => p.value === state.timeRange.preset);
  if (timePreset && state.timeRange.preset !== "custom") {
    parts.push(`for ${timePreset.label.toLowerCase()}`);
  } else if (state.timeRange.preset === "custom") {
    parts.push("for custom time range");
  }

  // Filters
  if (state.filters.length > 0) {
    parts.push(`with ${state.filters.length} filter(s)`);
  }

  // Limit
  parts.push(`(max ${state.limit} rows)`);

  return parts.join(" ");
}
