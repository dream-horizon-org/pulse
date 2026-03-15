import { FilterField, TimeRange } from "../useGetDataQuery/useGetDataQuery.interface";

export interface FunnelStep {
  eventName: string;
  dataType: "TRACES" | "LOGS";
  pulseType?: string;
  stepFilters?: {
    field: string;
    operator: string;
    value: (string | number)[];
  }[];
}

export interface FunnelRequestBody {
  steps: FunnelStep[];
  timeRange: TimeRange;
  filters?: FilterField[];
  groupBy?: string;
  mode: "UNIQUE_USERS" | "SESSIONS";
  windowSeconds?: number;
}

export interface FunnelStepResult {
  stepName: string;
  count: number;
  conversionRate: number;
  dropoffRate: number;
}

export interface FunnelResponse {
  steps: FunnelStepResult[];
  totalEnteredUsers: number;
  overallConversionRate: number;
  groupedResults?: Record<string, FunnelStepResult[]>;
}

export interface GetFunnelDataParams {
  requestBody: FunnelRequestBody;
  enabled?: boolean;
}

// Health: crash/ANR/non-fatal per step
export interface FunnelStepHealth {
  stepLevel: number;
  stepName: string;
  totalUsers: number;
  crashUsers: number;
  anrUsers: number;
  nonFatalUsers: number;
  crashRate: number;
  anrRate: number;
  nonFatalRate: number;
}

export interface FunnelHealthResponse {
  steps: FunnelStepHealth[];
  totalCrashUsers: number;
  totalAnrUsers: number;
  totalNonFatalUsers: number;
}

export interface GetFunnelHealthParams {
  requestBody: FunnelRequestBody;
  enabled?: boolean;
}

// Sessions drill-down
export interface FunnelSessionDetail {
  sessionId: string;
  userId: string;
  eventName: string;
  exceptionType: string;
  exceptionMessage: string;
  title: string;
  screenName: string;
  timestamp: string;
  groupId: string;
  platform: string;
  appVersion: string;
  deviceModel: string;
}

export interface FunnelSessionsResponse {
  stepLevel: number;
  stepName: string;
  totalAffectedSessions: number;
  sessions: FunnelSessionDetail[];
}

export interface FunnelSessionsRequestBody {
  steps: FunnelStep[];
  timeRange: TimeRange;
  filters?: FilterField[];
  mode: "UNIQUE_USERS" | "SESSIONS";
  windowSeconds?: number;
  stepLevel: number;
  issueType?: "ALL" | "CRASH" | "ANR" | "NON_FATAL";
  limit?: number;
}

export interface GetFunnelSessionsParams {
  requestBody: FunnelSessionsRequestBody;
  enabled?: boolean;
}

// Conversion trend
export interface FunnelTrendResponse {
  totalConversionRate: number;
  conversionTrend: number;
  medianTimes: (number | null)[];
}

export interface GetFunnelTrendParams {
  requestBody: FunnelRequestBody;
  enabled?: boolean;
}

// Grouped funnel
export interface FunnelGroupedStepResult {
  stepName: string;
  count: number;
  conversionRate: number;
  dropoffRate: number;
  medianTimeToStep: number | null;
}

export interface FunnelGroupedRow {
  groupValue: string;
  steps: FunnelGroupedStepResult[];
}

export interface FunnelGroupedResponse {
  groups: FunnelGroupedRow[];
}

export interface FunnelGroupedRequestBody extends FunnelRequestBody {
  groupBy: string;
}

export interface GetFunnelGroupedParams {
  requestBody: FunnelGroupedRequestBody;
  enabled?: boolean;
}

// Journey explorer
export interface JourneyNode {
  name: string;
}

export interface JourneyLink {
  source: string;
  target: string;
  value: number;
}

export interface JourneyResponse {
  nodes: JourneyNode[];
  links: JourneyLink[];
}

export interface JourneyRequestBody {
  direction: "forward" | "reverse";
  anchorEvent: string;
  depth: number;
  timeRange: TimeRange;
  filters?: FilterField[];
}

export interface GetJourneyParams {
  requestBody: JourneyRequestBody;
  enabled?: boolean;
}

// Funnel events list
export interface FunnelEventsResponse {
  events: string[];
}

// Funnel filter options
export interface FunnelFiltersResponse {
  filters: Record<string, string[]>;
}
