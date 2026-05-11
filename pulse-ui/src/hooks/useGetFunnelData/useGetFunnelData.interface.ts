import { FilterField, TimeRange } from "../useGetDataQuery/useGetDataQuery.interface";

export interface FunnelStep {
  eventName: string;
  dataType?: "TRACES" | "LOGS";
  pulseType?: string;
}

export type FunnelMode = "UNIQUE_USERS" | "SESSIONS";

export interface FunnelRequestBody {
  steps: FunnelStep[];
  timeRange: TimeRange;
  filters?: FilterField[];
  groupBy?: string;
  mode: FunnelMode;
  windowSeconds?: number;
}

export interface FunnelStepResult {
  stepName: string;
  count: number;
  conversionRate: number;
  dropoffRate: number;
  medianStepSeconds?: number | null;
  /** Completed orders attributable to users who reached this step; null when revenue not configured. */
  orderCount?: number | null;
  /** Total revenue attributable to users who reached this step. */
  revenue?: number | null;
  /** Avg order value among completers attributable via this step. */
  avgOrderValue?: number | null;
  /**
   * Projected revenue lost from drop-off into this step. 0 for step 0 and steps after the
   * revenue step. Null for unordered funnels.
   */
  lostRevenue?: number | null;
}

export interface FunnelResultsTotals {
  steps: FunnelStepResult[];
  totalEnteredUsers: number;
  overallConversionRate: number;
  /** Total revenue across the funnel; null when revenue not configured. */
  totalRevenue?: number | null;
  /** Total order count; null when revenue not configured. */
  totalOrderCount?: number | null;
  /** Overall AOV = totalRevenue / totalOrderCount; null when revenue not configured. */
  overallAvgOrderValue?: number | null;
  /** ISO-4217 currency code; null when revenue not configured. */
  currency?: string | null;
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

// Funnel events list
export interface FunnelEventsResponse {
  events: string[];
}

// Funnel filter options — server returns only the list of filter key strings
export interface FunnelFiltersResponse {
  filters: string[];
}

// Values for a single filter key
export interface FunnelFilterValuesResponse {
  values: string[];
}

// Tags
export interface TagsResponse {
  tags: string[];
}
