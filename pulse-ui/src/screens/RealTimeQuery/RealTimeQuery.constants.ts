import {
  DataSource,
  FilterOperator,
  AggregationType,
  DateGranularity,
  ChartType,
  QueryState,
  FieldMetadata,
} from "./RealTimeQuery.interface";

export const DATA_SOURCES: DataSource[] = [
  {
    id: "events",
    label: "Events",
    description: "Clickstream and application events",
    icon: "activity",
  },
  {
    id: "users",
    label: "Users",
    description: "User profiles and attributes",
    icon: "users",
  },
  {
    id: "sessions",
    label: "Sessions",
    description: "User session data",
    icon: "clock",
  },
];

export const FILTER_OPERATORS: { value: FilterOperator; label: string; types: string[] }[] = [
  { value: "equals", label: "Equals", types: ["string", "number", "boolean"] },
  { value: "not_equals", label: "Not equals", types: ["string", "number", "boolean"] },
  { value: "contains", label: "Contains", types: ["string"] },
  { value: "not_contains", label: "Does not contain", types: ["string"] },
  { value: "starts_with", label: "Starts with", types: ["string"] },
  { value: "ends_with", label: "Ends with", types: ["string"] },
  { value: "greater_than", label: "Greater than", types: ["number", "date"] },
  { value: "less_than", label: "Less than", types: ["number", "date"] },
  { value: "greater_than_or_equal", label: "Greater than or equal", types: ["number", "date"] },
  { value: "less_than_or_equal", label: "Less than or equal", types: ["number", "date"] },
  { value: "in", label: "Is one of", types: ["string", "number"] },
  { value: "not_in", label: "Is not one of", types: ["string", "number"] },
  { value: "is_null", label: "Is empty", types: ["string", "number", "date", "boolean"] },
  { value: "is_not_null", label: "Is not empty", types: ["string", "number", "date", "boolean"] },
];

export const AGGREGATION_TYPES: { value: AggregationType; label: string; requiresField: boolean }[] = [
  { value: "count", label: "Count", requiresField: false },
  { value: "count_distinct", label: "Count Distinct", requiresField: true },
  { value: "sum", label: "Sum", requiresField: true },
  { value: "avg", label: "Average", requiresField: true },
  { value: "min", label: "Minimum", requiresField: true },
  { value: "max", label: "Maximum", requiresField: true },
  { value: "percentile_50", label: "Median (P50)", requiresField: true },
  { value: "percentile_90", label: "P90", requiresField: true },
  { value: "percentile_95", label: "P95", requiresField: true },
  { value: "percentile_99", label: "P99", requiresField: true },
];

export const DATE_GRANULARITIES: { value: DateGranularity; label: string }[] = [
  { value: "minute", label: "Minute" },
  { value: "hour", label: "Hour" },
  { value: "day", label: "Day" },
  { value: "week", label: "Week" },
  { value: "month", label: "Month" },
  { value: "quarter", label: "Quarter" },
  { value: "year", label: "Year" },
];

export const CHART_TYPES: { value: ChartType; label: string; icon: string }[] = [
  { value: "line", label: "Line Chart", icon: "chart-line" },
  { value: "bar", label: "Bar Chart", icon: "chart-bar" },
  { value: "area", label: "Area Chart", icon: "chart-area" },
  { value: "pie", label: "Pie Chart", icon: "chart-pie" },
  { value: "table", label: "Data Table", icon: "table" },
];

export const TIME_RANGE_PRESETS = [
  { value: "LAST_5_MINUTES", label: "Last 5 minutes" },
  { value: "LAST_15_MINUTES", label: "Last 15 minutes" },
  { value: "LAST_30_MINUTES", label: "Last 30 minutes" },
  { value: "LAST_1_HOUR", label: "Last 1 hour" },
  { value: "LAST_3_HOURS", label: "Last 3 hours" },
  { value: "LAST_6_HOURS", label: "Last 6 hours" },
  { value: "LAST_12_HOURS", label: "Last 12 hours" },
  { value: "LAST_24_HOURS", label: "Last 24 hours" },
  { value: "LAST_7_DAYS", label: "Last 7 days" },
  { value: "LAST_30_DAYS", label: "Last 30 days" },
  { value: "TODAY", label: "Today" },
  { value: "YESTERDAY", label: "Yesterday" },
  { value: "THIS_WEEK", label: "This week" },
  { value: "THIS_MONTH", label: "This month" },
];

export const DEFAULT_QUERY_STATE: QueryState = {
  dataSource: "events",
  metrics: [
    {
      id: "metric-1",
      aggregation: "count",
      field: null,
      alias: "Total Events",
    },
  ],
  filters: {
    id: "filter-group-1",
    logic: "AND",
    conditions: [],
  },
  groupBy: [],
  timeRange: {
    type: "preset",
    preset: "LAST_24_HOURS",
  },
  visualization: {
    chartType: "line",
    showLegend: true,
    stacked: false,
  },
  limit: 1000,
};

// Sample fields for each data source (will be fetched from API in production)
export const EVENTS_FIELDS: FieldMetadata[] = [
  { name: "eventName", type: "string", description: "Name of the event" },
  { name: "eventTimestamp", type: "date", description: "When the event occurred" },
  { name: "userId", type: "string", description: "Unique user identifier" },
  { name: "sessionId", type: "string", description: "Session identifier" },
  { name: "platform", type: "string", description: "Platform (Android/iOS)" },
  { name: "app_version", type: "string", description: "Application version" },
  { name: "os_version", type: "string", description: "Operating system version" },
  { name: "device", type: "string", description: "Device model" },
  { name: "network_provider", type: "string", description: "Network provider" },
  { name: "state", type: "string", description: "User's state/region" },
  { name: "country", type: "string", description: "User's country" },
  { name: "duration", type: "number", description: "Event duration in milliseconds" },
  { name: "props", type: "object", description: "Custom event properties" },
];

export const USERS_FIELDS: FieldMetadata[] = [
  { name: "userId", type: "string", description: "Unique user identifier" },
  { name: "firstSeen", type: "date", description: "First activity timestamp" },
  { name: "lastSeen", type: "date", description: "Last activity timestamp" },
  { name: "platform", type: "string", description: "Primary platform" },
  { name: "country", type: "string", description: "User's country" },
  { name: "totalSessions", type: "number", description: "Total session count" },
  { name: "totalEvents", type: "number", description: "Total event count" },
];

export const SESSIONS_FIELDS: FieldMetadata[] = [
  { name: "sessionId", type: "string", description: "Unique session identifier" },
  { name: "userId", type: "string", description: "User identifier" },
  { name: "startTime", type: "date", description: "Session start time" },
  { name: "endTime", type: "date", description: "Session end time" },
  { name: "duration", type: "number", description: "Session duration in seconds" },
  { name: "platform", type: "string", description: "Platform (Android/iOS)" },
  { name: "app_version", type: "string", description: "Application version" },
  { name: "eventCount", type: "number", description: "Number of events in session" },
  { name: "hasError", type: "boolean", description: "Whether session had errors" },
];

export const REALTIME_QUERY_TEXTS = {
  PAGE_TITLE: "Real-time Querying",
  PAGE_SUBTITLE: "Build custom queries and visualize your data without writing SQL",
  DATA_SOURCE_TITLE: "Data Source",
  METRICS_TITLE: "Metrics",
  FILTERS_TITLE: "Filters",
  GROUP_BY_TITLE: "Group By",
  TIME_RANGE_TITLE: "Time Range",
  VISUALIZATION_TITLE: "Visualization",
  RESULTS_TITLE: "Results",
  RUN_QUERY: "Run Query",
  CANCEL_QUERY: "Cancel",
  SAVE_QUERY: "Save Query",
  QUERY_HISTORY: "History",
  TEMPLATES: "Templates",
  ADD_METRIC: "Add Metric",
  ADD_FILTER: "Add Filter",
  ADD_DIMENSION: "Add Dimension",
  NO_RESULTS: "No results found. Try adjusting your query.",
  LOADING_RESULTS: "Running query...",
  QUERY_SUCCESS: "Query completed successfully",
  QUERY_ERROR: "Query failed. Please check your parameters.",
  ESTIMATED_ROWS: "Estimated rows",
  DATA_FRESHNESS: "Data freshness",
  EXECUTION_TIME: "Execution time",
};

