import { FilterGroup } from "./filterConfig";

// API Request Types
export interface GetSessionsRequest {
  dateRange?: {
    start: string;
    end: string;
  };
  environment?: "production" | "staging" | "development" | "all";
  project?: "ios" | "android" | "web" | "all";
  searchQuery?: string;
  filters?: {
    hasErrors?: boolean;
    rageClicks?: boolean;
    slowSessions?: boolean;
    mobile?: boolean;
    newUsers?: boolean;
  };
  advancedFilters?: FilterGroup;
  drillDown?: {
    type:
      | "conversion_loss"
      | "affected_users"
      | "sessions_with_issues"
      | "interaction"
      | "journey"
      | "cardinality"
      | "error_pattern"
      | "friction_hotspot";
    value: any;
  };
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortOrder?: "asc" | "desc";
}

// API Response Types
export interface SessionResponse {
  id: string;
  sessionId: string;
  startTime: string; // ISO 8601
  userId: string | null;
  isAnonymous: boolean;
  duration: number; // milliseconds
  pages: number;
  events: number;
  errors: number;
  device: string;
  browser: string;
  os: string;
  journey: string[];
  tags: SessionTagResponse[];
  environment: "production" | "staging" | "development";
  project: "ios" | "android" | "web";
  metadata?: Record<string, any>;

  // Investigation-focused fields (NEW)
  interactionQuality: number; // 0-10 scale
  issueSummary: IssueSummary;
  outcome: SessionOutcome;
}

export interface IssueSummary {
  hasIssues: boolean;
  failedInteractions: boolean;
  hasErrors: boolean;
  hasFrustration: boolean; // rage clicks, dead clicks
  isSlow: boolean;
  crashed: boolean;
  issueCount: number; // Total count of issues
}

export type SessionOutcome =
  | "completed" // User finished their goal
  | "incomplete" // Started but didn't finish
  | "drop_off" // Early exit
  | "crashed"; // App/session crashed

export interface SessionTagResponse {
  type: "rage" | "slow" | "new" | "js_error" | "network_fail" | "dead_click";
  count?: number;
  severity?: "low" | "medium" | "high";
}

export interface GetSessionsResponse {
  sessions: SessionResponse[];
  pagination: {
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
  };
  metrics: SessionReplayMetrics;
}

// Journey Analysis - Path analysis for Product teams
export interface JourneyPath {
  path: string[]; // e.g., ['Home', 'Product', 'Cart', 'Checkout']
  sessionCount: number;
  completionRate: number; // % of sessions that completed the journey
  avgDuration: number; // milliseconds
  dropOffPoint?: string; // Where most users drop off
  conversionRate?: number; // For conversion-focused journeys
  pathLength: number; // Number of screens/steps in the path
  isCompleted: boolean; // True if journey reached a successful outcome
  isBounce: boolean; // True if only 1 screen visited
}

// Segmentation - Non-opinionated session breakdowns only
export interface SegmentationMetrics {
  byPlatform: PlatformSegment[];
  byAppVersion?: AppVersionSegment[]; // Optional: App version breakdown
  byCountry?: CountrySegment[]; // Optional: Geographic breakdown (top 5)
  // Note: byUserType and byQuality removed - opinionated segmentations
}

export interface PlatformSegment {
  platform: "iOS" | "Android" | "Web";
  sessionCount: number;
  errorRate: number;
  topIssue?: string; // Most common error type
}

export interface AppVersionSegment {
  version: string; // e.g., "2.5.1"
  platform: "iOS" | "Android" | "Web";
  sessionCount: number;
  errorRate: number;
  releaseDate?: string; // ISO date
  isLatest: boolean;
}

export interface CountrySegment {
  countryCode: string; // ISO code
  countryName: string;
  sessionCount: number;
  errorRate: number;
}

// Performance Metrics - Core Web Vitals & RUM
export interface PerformanceMetrics {
  coreWebVitals: CoreWebVitals;
  pageLoadDistribution: PageLoadDistribution;
  networkHealth: NetworkHealth;
}

export interface CoreWebVitals {
  lcp: WebVitalMetric; // Largest Contentful Paint
  fid: WebVitalMetric; // First Input Delay
  cls: WebVitalMetric; // Cumulative Layout Shift
}

export interface WebVitalMetric {
  p50: number;
  p75: number;
  p95: number;
  rating: "good" | "needs-improvement" | "poor";
  sessionsGood: number;
  sessionsNeedsImprovement: number;
  sessionsPoor: number;
}

export interface PageLoadDistribution {
  under1s: number; // percentage
  between1and3s: number;
  between3and5s: number;
  over5s: number;
  avgLoadTime: number; // milliseconds
}

export interface NetworkHealth {
  apiSuccessRate: number; // percentage
  avgApiLatency: number; // milliseconds
  failedRequests: number;
  totalRequests: number;
  slowRequests: number; // requests > 3s
}

// Time-based Patterns
export interface TimeBasedPatterns {
  peakErrorHours: HourlyPattern[];
  performanceByHour: HourlyPerformance[];
  errorTrend: TrendDataPoint[];
}

export interface HourlyPattern {
  hour: number; // 0-23
  errorCount: number;
  sessionCount: number;
  errorRate: number; // percentage
}

export interface HourlyPerformance {
  hour: number; // 0-23
  avgResponseTime: number; // milliseconds
  rating: "excellent" | "good" | "fair" | "poor";
}

export interface TrendDataPoint {
  date: string; // ISO date
  errorCount: number;
  sessionCount: number;
  errorRate: number;
}

// Geographic & Browser Distribution
export interface GeographicMetrics {
  topRegions: RegionMetric[];
  browserBreakdown: BrowserMetric[];
  deviceTypeBreakdown: DeviceTypeMetric[];
}

export interface RegionMetric {
  country: string;
  countryCode: string;
  sessionCount: number;
  errorRate: number;
  avgPerformance: number; // Apdex-like score
  topIssue?: string;
}

export interface BrowserMetric {
  browser: string; // 'Chrome', 'Safari', 'Firefox', etc.
  version?: string;
  sessionCount: number;
  errorRate: number;
  compatibilityIssues: number;
  topIssue?: string;
}

export interface DeviceTypeMetric {
  deviceType: "mobile" | "tablet" | "desktop";
  sessionCount: number;
  errorRate: number;
  avgPerformance: number;
}

// Investigation-focused metrics for Session Replay
export interface SessionReplayMetrics {
  totalSessions: number;

  // Critical Interaction Performance (PRIMARY - aligned with Pulse)
  criticalInteractions: CriticalInteractionPerformance[];
  estimatedImpact: BusinessImpact;

  // Issue Intelligence (for UX/Design)
  topIssueHotspots: IssueHotspot[]; // Max 3, sorted by severity × hit rate

  // Error Patterns (for Tech) - clusters identical errors to avoid watching duplicate sessions
  topErrorPatterns: ErrorPattern[]; // Max 3, sorted by severity × affected sessions

  // Journey Analysis (for Product - path analysis)
  topJourneys?: JourneyPath[]; // Top 10 unique paths by frequency

  // Segmentation (for Product - cardinality analysis)
  segmentation?: SegmentationMetrics;

  // Performance Metrics (for RUM/Tech)
  performance?: PerformanceMetrics;

  // Time-based Patterns (for monitoring trends)
  timePatterns?: TimeBasedPatterns;

  // Geographic & Browser Distribution
  geographic?: GeographicMetrics;

  // Context & Trends
  comparison: ComparisonMetrics;

  // Legacy metrics (kept for backward compatibility during migration)
  /** @deprecated Use criticalInteractions instead */
  sessionsWithIssues?: number;
  /** @deprecated Use criticalInteractions instead */
  issueRate?: number;
  /** @deprecated Use criticalInteractions instead */
  cleanSessions?: number;
  /** @deprecated Use criticalInteractions instead */
  cleanRate?: number;
  /** @deprecated Removed - not defensible. Use concrete metrics instead */
  avgInteractionQuality?: number;
  /** @deprecated Use comparison.sessionsWithIssues.change instead */
  qualityTrend?: number;
  /** @deprecated Use errorTaxonomy instead */
  issueBreakdown?: {
    failedInteractions: number;
    errorsAndCrashes: number;
    frustrationSignals: number;
    dropOffs: number;
  };
}

// Critical Interaction Performance - aligned with Pulse's existing system
export interface CriticalInteractionPerformance {
  // Interaction identity (from Pulse's critical_interactions table)
  interactionId: number; // FK to critical_interactions
  interactionName: string; // e.g., "tap_pay_button", "api_create_payment"
  displayName: string; // e.g., "Payment Button Tap", "Create Payment API"
  description?: string;

  // Performance Metrics (SAME as Pulse's InteractionCard)
  apdexScore: number; // 0-1 scale (Pulse standard)
  errorRate: number; // Percentage of failed interactions (0-100)
  p50Latency: number; // Median latency in ms
  p95Latency?: number; // 95th percentile latency in ms
  p99Latency?: number; // 99th percentile latency in ms
  poorUserPercentage: number; // % of users with "Poor" experience (0-100)

  // Health Status (SAME as Pulse's InteractionCard)
  healthStatus: "Excellent" | "Good" | "Fair" | "Poor"; // Based on Apdex thresholds

  // Attempt/Success breakdown
  totalAttempts: number;
  successfulAttempts: number;
  failedAttempts: number;
  uniqueUsers: number;

  // Thresholds (from Pulse's server config)
  lowThreshold: number; // ms
  highThreshold: number; // ms

  // Session context
  sessionsWithThisInteraction: number; // How many sessions have this interaction

  // Optional business impact (if configured)
  estimatedLoss?: {
    type: "revenue" | "conversion" | "users";
    amount: number;
    unit: string; // "$", "%", "users"
    period: "hour" | "day" | "week" | "month";
  };

  severity: "critical" | "high" | "medium" | "low";
}

export interface BusinessImpact {
  totalRevenueAtRisk: number;
  revenueAtRiskPeriod: "day" | "week" | "month"; // Time period for revenue estimate
  affectedUsers: number;
  totalUsers: number; // Total users in period for context
  affectedUsersPercentage: number; // Percentage of total users
  conversionImpact: number; // percentage points lost (absolute, not relative)
  conversionBaseline: number; // Historical baseline for comparison
  supportTicketCorrelation?: {
    // Optional - only if reliable correlation exists
    count: number;
    confidence: "high" | "medium" | "low";
    totalTickets: number; // Total tickets in period
  };
}

// Issue Hotspots - tells UX where users struggle
export interface IssueHotspot {
  location: string; // screen/page name
  issueType:
    | "rage_click"
    | "dead_click"
    | "slow_interaction"
    | "form_abandon"
    | "error";
  affectedSessions: number;
  totalSessionsAtLocation: number; // Total sessions that visited this location
  hitRate: number; // Percentage of sessions at this location that hit the issue
  uniqueUsers: number; // Unique users affected
  avgStruggleTime: number; // seconds (median recommended over mean)
  medianStruggleTime: number; // seconds (less affected by outliers)
  struggleTimeRange: { min: number; max: number }; // Show distribution
  specificElement?: string; // e.g., "Submit Payment button"
  elementIdentifier?: string; // data-testid or id for dev debugging
  severity: "critical" | "high" | "medium" | "low";
}

// Error Pattern - clusters identical errors across sessions
// Goal: User sees "32 sessions have this error" → watches 1, not 32
export interface ErrorPattern {
  errorSignature: string; // Unique identifier for this error type
  displayName: string; // Human-readable error (e.g., "POST /api/payment → 504 Gateway Timeout")
  errorType: "network" | "javascript" | "console" | "crash"; // What we can detect from session data
  count: number; // Total occurrences across all sessions
  affectedSessions: number; // How many sessions have this error
  uniqueUsers: number; // Unique users who hit this error
  firstSeen: string; // ISO timestamp
  lastSeen: string; // ISO timestamp
  platformBreakdown: Array<{
    platform: string; // "iOS", "Android", "Web"
    count: number; // Errors on this platform
  }>;
  sampleSessionId: string; // ID of ONE representative session to watch
  severity: "critical" | "high" | "medium" | "low";
}

// Comparison & Trends - provides context
export interface ComparisonMetrics {
  currentPeriod: {
    start: string; // ISO timestamp
    end: string; // ISO timestamp
    label: string; // "Feb 12-19, 2026" or "Last 7 days"
  };
  comparisonPeriod: {
    start: string; // ISO timestamp
    end: string; // ISO timestamp
    label: string; // "Feb 5-12, 2026" or "Previous 7 days"
  };
  totalSessions: {
    current: number;
    previous: number;
    change: number; // Absolute change
    changePercent: number; // Percentage change
  };
  sessionsWithIssues: {
    current: number;
    currentPercent: number; // % of total sessions
    previous: number;
    previousPercent: number;
    change: number; // Change in percentage points
    trend: "improving" | "declining" | "stable";
  };
  topDegradedFlows: Array<{
    flowName: string;
    changeInSuccessRate: number; // In percentage points
  }>;
  topImprovedFlows: Array<{
    flowName: string;
    changeInSuccessRate: number; // In percentage points
  }>;
}

export interface GetSessionDetailRequest {
  sessionId: string;
  /** Optional. Comma-separated: traces, logs, exceptions, events. If omitted, returns core only. */
  include?: Array<"traces" | "logs" | "exceptions" | "events">;
}

export interface GetSessionDetailResponse {
  session: SessionResponse;
  events: SessionEventResponse[];
  timeline: TimelineEntry[];
}

export type SessionDetailInclude = "traces" | "logs" | "exceptions" | "events";

export interface SessionDetailInteraction {
  interactionName: string;
  status: "success" | "failed";
  successCount: number;
  failureCount: number;
  durationMs: number;
  apdexScore: number;
}

export interface SessionDetailNetworkRequest {
  timestamp: number;
  durationNs: number;
  method: string;
  url: string;
  status: string;
  target: string;
  traceId: string;
  spanId: string;
}

export interface SessionDetailEvent {
  traceId: string;
  spanId: string;
  timestamp: string;
  eventType: "click" | "navigation" | "api_call" | "error" | "interaction";
  description: string;
  durationNs: number;
}

export interface SessionDetailException {
  traceId: string;
  spanId: string;
  pulseType: string;
  timestamp: number;
  title: string;
  exceptionStackTrace: string;
}

export interface SessionDetailApiResponse {
  sessionId: string;
  userId: string;
  isAnonymous: boolean;
  startTime: string;
  endTime: string;
  duration: number;
  platform: "iOS" | "Android" | "Web";
  device: string;
  osVersion: string;
  appVersion: string;
  geography: string;
  quality: number;
  journey: string[];
  interactions: SessionDetailInteraction[];
  networkRequests: SessionDetailNetworkRequest[];
  events?: SessionDetailEvent[];
  exceptions?: SessionDetailException[];
}

export interface SessionEventResponse {
  id: string;
  timestamp: string;
  type: "click" | "input" | "navigation" | "error" | "network" | "custom";
  target?: string;
  value?: any;
  metadata?: Record<string, any>;
}

export interface TimelineEntry {
  timestamp: string;
  type: "page_view" | "interaction" | "error" | "performance";
  description: string;
  metadata?: Record<string, any>;
}

// Bulk Action Types
export interface BulkTagRequest {
  sessionIds: string[];
  tags: string[];
}

export interface BulkDeleteRequest {
  sessionIds: string[];
}

export interface ExportSessionsRequest {
  sessionIds: string[];
  format: "csv" | "json";
}

export interface ExportSessionsResponse {
  downloadUrl: string;
  expiresAt: string;
}

// Configuration API Types

export interface GetFilterSchemaRequest {
  projectId?: string;
}

export interface FilterFieldDefinitionAPI {
  key: string;
  label: string;
  category: string;
  type: "string" | "number" | "boolean" | "date" | "enum";
  operators: string[];
  enumValues?: Array<{ value: string; label: string }>;
  description?: string;
  unit?: string;
}

export interface FilterCategoryAPI {
  key: string;
  label: string;
  description?: string;
  fields: FilterFieldDefinitionAPI[];
}

export interface GetFilterSchemaResponse {
  platform: "web" | "ios" | "android";
  categories: FilterCategoryAPI[];
  operatorLabels: Record<string, string>;
}

export interface DateRangeOption {
  value: string;
  label: string;
}

export interface GetDateRangeConfigResponse {
  options: DateRangeOption[];
  defaultValue: string;
  customRangeEnabled: boolean;
}

export interface QuickFilterAPI {
  id: string;
  label: string;
  description: string;
  icon: string;
  count?: number;
  filterCondition: Record<string, any>;
}

export interface GetQuickFiltersResponse {
  filters: QuickFilterAPI[];
}

export type TimeRange = {
  from: string; // ISO-8601 datetime (inclusive)
  to: string; // ISO-8601 datetime (exclusive)
};

export type PageRequest = {
  limit?: number; // 1..100 (default: 10)
  cursor?: string; // opaque cursor from previous response
};

export type FilterValue = string | number | boolean | Array<string | number>;

export type AdvancedFilterGroup = {
  op: "AND" | "OR";
  children: SessionListingFilterCondition[];
};

export type SessionListingFilterCondition = {
  field: string; // FilterField enum name
  operator: string; // Operator enum name
  value?: FilterValue;
};

export type FiltersRequest = {
  quick?: string[];
  advanced?: AdvancedFilterGroup;
};

export type SortField =
  | "START_TIME"
  | "DURATION"
  | "QUALITY_SCORE"
  | "NETWORK_ERRORS"
  | "CRASHES"
  | "ANRS"
  | "SLOW_INTERACTIONS"
  | "SPAN_COUNT";

export type SortDirection = "ASC" | "DESC";

export type SessionListingRequest = {
  timeRange: TimeRange;
  page?: PageRequest;
  filters?: FiltersRequest;
  query?: string;
  sortBy?: SortField;
  sortDirection?: SortDirection;
};

export type PageResponse = {
  limit: number;
  nextCursor?: string | null;
  hasMore: boolean;
};

export type IssueItem = {
  type: string;
  label: string;
  count: number;
};

export type ImpactedScreens = {
  crashes?: string[];
  anrs?: string[];
  nonFatals?: string[];
};

export type SessionItem = {
  sessionId: string;
  startTime: string;
  durationMs: number;
  user: string | null;
  qualityScore: number | null;
  issues: IssueItem[];
  platform: string;
  spanCount: number;
  journey: string[];
  impactedScreens: ImpactedScreens | null;
};

export type SessionListingResponse = {
  sessions: SessionItem[];
  page: PageResponse;
};

export type QuickFilterItem = {
  key: string;
  displayName: string;
  description: string;
};

export type OperatorItem = {
  key: string;
  label: string;
  valueType: "single" | "array" | "range" | "none";
};

export type FieldItem = {
  key: string;
  displayName: string;
  dataType: "string" | "integer" | "float";
  allowedOperators: OperatorItem[];
};

export type CategoryItem = {
  categoryKey: string;
  displayName: string;
  fields: FieldItem[];
};

export type FilterConfigResponse = {
  quick: QuickFilterItem[];
  advanced: CategoryItem[];
};
