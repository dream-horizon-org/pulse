import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { API_BASE_URL, API_ROUTES } from "../constants";
import { makeRequest } from "../helpers/makeRequest";
import { getQueryParamString } from "../helpers/queryParams";
import type {
  FunnelEventsResponse,
  FunnelFiltersResponse,
  FunnelFilterValuesResponse,
  FunnelGroupedRequestBody,
  FunnelGroupedResponse,
  FunnelStep,
  TagsResponse,
} from "../hooks/useGetFunnelData/useGetFunnelData.interface";
import type {
  FilterField,
  TimeRange,
} from "../hooks/useGetDataQuery/useGetDataQuery.interface";

dayjs.extend(utc);

/**
 * Normalise a time string to a UTC ISO-8601 string.
 * Accepts both ISO format ("2026-03-17T00:00:00Z") and
 * "YYYY-MM-DD HH:mm:ss" format used in some API responses.
 */
function formatTimeRange(timeRange: TimeRange): TimeRange {
  const fmt = (t: string): string =>
    t.includes("T") || t.includes("Z")
      ? dayjs.utc(t).toISOString()
      : dayjs.utc(t, "YYYY-MM-DD HH:mm:ss").toISOString();
  return { start: fmt(timeRange.start), end: fmt(timeRange.end) };
}

/** Funnel schedule type: AUTO refreshes on a rolling window; ONCE is computed once. */
export enum FunnelType {
  AUTO = "AUTO",
  ONCE = "ONCE",
}

export type FunnelFilter = {
  field: string;
  operator: "EQ" | "NE" | "IN" | "NOT_IN";
  value: string | string[] | number | number[] | boolean | boolean[];
};

/** Whether funnel steps must be completed in order or in any order. */
export enum StepOrderType {
  ORDERED = "ORDERED",
  UNORDERED = "UNORDERED",
}

/**
 * Analysis grouping key used by both ClickHouse and Spark funnel compute.
 *
 * UNIQUE_USERS groups events by materialized `UserId` (with the canonical
 * user.id → app.installation.id fallback applied at ingest) and is the default.
 * SESSIONS groups by `SessionId` — conversion then represents the fraction of
 * sessions (not users) that reached each step. A user with multiple sessions
 * contributes one bucket per session.
 */
export enum FunnelMode {
  UNIQUE_USERS = "UNIQUE_USERS",
  SESSIONS = "SESSIONS",
}

/** Computed status returned by the server for funnels and journeys. */
export type AnalysisStatus =
  | "ACTIVE"
  | "IN_PROGRESS"
  | "WARN"
  | "PENDING"
  | "FAILED"
  | "COMPLETED";

// ─── Funnel listing types ──────────────────────────────────────────────────────

/** Single funnel row returned by GET /v1/funnels. */
export type FunnelListItem = {
  id: string;
  name: string;
  status: AnalysisStatus;
  createdBy: string;
  /** Server-side `createdAt` from FunnelDefinitionResponse. */
  createdAt: string;
  /** Server-side `updatedAt` from FunnelDefinitionResponse. Kept for back-compat. */
  updatedAt?: string;
  tags: string[];
  funnelType?: FunnelType;
  stepOrderType?: StepOrderType;
  /** Overall conversion rate (%) for funnels with computed metrics. */
  overallConversionRate?: number;
  /** Change vs prior period (percentage points); positive = up. */
  conversionTrend?: number;
};

/** Filter option metadata returned alongside listing data. */
export type ListFilterOptions = {
  creators?: string[];
  tags?: string[];
};

/** Listing payload for GET /v1/funnels. */
export type FunnelListResponse = {
  items: FunnelListItem[];
  totalCount?: number;
  page?: number;
  pageSize?: number;
  totalPages?: number;
  filterOptions?: ListFilterOptions;
};

// ─── Journey listing types ─────────────────────────────────────────────────────

/** Single journey row returned by GET /v1/journeys. */
export type JourneyListItem = {
  id: string;
  name: string;
  status: AnalysisStatus;
  createdBy: string;
  /** Server-side `createdAt` from JourneyResponse. */
  createdAt: string;
  /** Server-side `updatedAt` from JourneyResponse. Kept for back-compat. */
  updatedAt?: string;
  tags: string[];
  journeyType?: FunnelType;
};

/** Listing payload for GET /v1/journeys. */
export type JourneyListResponse = {
  items: JourneyListItem[];
  totalCount?: number;
  page?: number;
  pageSize?: number;
  totalPages?: number;
  filterOptions?: ListFilterOptions;
};

/** Query params for GET /v1/funnels or GET /v1/journeys (resource implied by path). */
export type FunnelJourneyListQueryParams = {
  search?: string | null;
  status?: AnalysisStatus | null;
  /** Match if created by any of these users. */
  createdBy?: string[] | null;
  /** Match if item has any of these tags. */
  tags?: string[] | null;
  /** Funnel listing only (GET /v1/funnels). */
  stepOrderType?: StepOrderType | null;
  /** 1-based page index (default 1). */
  page?: number | null;
  /** Page size (default 10). */
  pageSize?: number | null;
};

// ─── Funnel detail types ───────────────────────────────────────────────────────

/** Single funnel returned by GET /v1/funnels/:id. */
export type FunnelDetail = {
  id: string;
  projectId?: string;
  name: string;
  description: string;
  status: AnalysisStatus;
  funnelType: FunnelType;
  stepOrderType: StepOrderType;
  steps: FunnelStep[];
  filters?: FilterField[];
  windowSeconds: number;
  mode?: FunnelMode;
  dateRangeDays?: number;
  startTime?: string;
  endTime?: string;
  expiry?: string;
  createdAt: string;
  updatedAt?: string;
  lastRunAt?: string;
  createdBy: string;
  tags: string[];
  funnelResults?: unknown;
  /** Latest overall conversion % — same value the listing surfaces. */
  overallConversionRate?: number;
  /** Change vs prior run (percentage points); positive = up. Same as listing. */
  conversionTrend?: number;
  /** @deprecated Kept for backwards compat; use startTime/endTime or dateRangeDays. */
  timeRange?: TimeRange;
  expiryDate?: string;
};

// ─── Journey detail types ──────────────────────────────────────────────────────

/** Single journey returned by GET /v1/journeys/:id. */
export type JourneyDetail = {
  id: string;
  projectId?: string;
  name: string;
  description: string;
  status: AnalysisStatus;
  anchorEvent: string;
  direction: string;
  depth: number;
  mode?: string;
  journeyType?: FunnelType;
  filters?: FilterField[];
  startTime?: string;
  endTime?: string;
  expiry?: string;
  dateRangeDays?: number;
  createdAt: string;
  updatedAt?: string;
  lastRunAt?: string;
  createdBy: string;
  tags: string[];
  journeyResults?: unknown;
  /** @deprecated */
  timeRange?: TimeRange;
  expiryDate?: string;
  rollingType?: string;
};

/** Request body for POST /v1/funnel (create) and PUT /v1/funnel/:id (full replace). */
export interface CreateFunnelRequestBody {
  /** Display name of the funnel. */
  name: string;
  /** Optional free-text description. */
  description?: string;
  /** Taxonomy tags for grouping / filtering. */
  tags?: string[];
  /**
   * Schedule type.
   * AUTO — recomputes on a rolling window every 24 h.
   * ONCE — computed once after creation; never auto-updated.
   */
  funnelType: FunnelType;
  /** Whether steps must be completed in strict order or in any order. */
  stepOrderType: StepOrderType;
  /** Ordered list of funnel steps (min 2). */
  steps: FunnelStep[];
  /** Maximum seconds a user has to complete the funnel after entering step 1. */
  windowSeconds: number;
  /**
   * Analysis grouping key. UNIQUE_USERS counts distinct users per step;
   * SESSIONS counts distinct sessions. Defaults to UNIQUE_USERS on create.
   */
  mode?: FunnelMode;
  /** Audience filters applied when computing conversion. */
  filters?: FunnelFilter[];
  /**
   * AUTO funnels only — rolling window size in days (e.g. 7 for "last 7 days").
   * Derived from the date-range preset selected in the UI.
   */
  dateRangeDays?: number;
  /**
   * AUTO funnels only — ISO-8601 datetime after which the funnel stops refreshing.
   * Required when funnelType is AUTO.
   */
  expiryDate?: string;
  /** ONCE funnels only — ISO-8601 start of the fixed analysis window. */
  startTime?: string;
  /** ONCE funnels only — ISO-8601 end of the fixed analysis window. */
  endTime?: string;
  /** @deprecated Use startTime/endTime (ONCE) or dateRangeDays (AUTO) instead. */
  timeRange?: TimeRange;
}

/**
 * Request body for PUT /v1/funnel/:id.
 * PUT is a full replace, but the backend update DTO names the rolling-window
 * deadline `expiry` (vs. `expiryDate` on create). Kept as a distinct type so
 * callers can't accidentally send the create-shaped field name on update.
 */
export interface UpdateFunnelRequestBody
  extends Omit<CreateFunnelRequestBody, "expiryDate"> {
  /**
   * AUTO funnels only — ISO-8601 datetime after which the funnel stops refreshing.
   * Matches `UpdateFunnelDefinitionRequest.expiry` on the backend.
   */
  expiry?: string;
}

/** Request body for POST /v1/journeys (create) and PUT /v1/journeys/:id (update). */
export interface CreateJourneyRequestBody {
  name: string;
  description?: string;
  tags?: string[];
  journeyType: FunnelType;
  direction: "START" | "END";
  anchorEvent: string;
  depth: number;
  filters?: FunnelFilter[];
  /** AUTO journeys — rolling window size in days. */
  dateRangeDays?: number;
  /** AUTO journeys — ISO-8601 datetime after which the journey stops refreshing. */
  expiry?: string;
  /** ONCE journeys — ISO-8601 start of the fixed analysis window. */
  startTime?: string;
  /** ONCE journeys — ISO-8601 end of the fixed analysis window. */
  endTime?: string;
}

const FUNNELS_BASE = "/v1/funnels";

function filterListParams(
  params: FunnelJourneyListQueryParams,
): Record<string, string> {
  const out: Record<string, string> = {};
  if (params.search) out.search = params.search;
  if (params.status) out.status = params.status;
  if (params.createdBy?.length) out.createdBy = params.createdBy.join(",");
  if (params.tags?.length) out.tags = params.tags.join(",");
  if (params.stepOrderType) out.stepOrderType = params.stepOrderType;
  if (params.page != null && params.page > 0) out.page = String(params.page);
  if (params.pageSize != null && params.pageSize > 0) {
    out.pageSize = String(params.pageSize);
  }
  return out;
}

/** GET /v1/funnels */
export async function fetchFunnelsList(
  queryParams: FunnelJourneyListQueryParams,
) {
  const filtered = filterListParams(queryParams);
  const suffix =
    Object.keys(filtered).length > 0 ? getQueryParamString(filtered) : "";

  return makeRequest<FunnelListResponse>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_DETAILS.apiPath}${suffix}`,
    init: {
      method: API_ROUTES.FUNNEL_DETAILS.method,
    },
  });
}

/** GET /v1/journeys */
export async function fetchJourneysList(
  queryParams: FunnelJourneyListQueryParams,
) {
  const filtered = filterListParams(queryParams);
  const suffix =
    Object.keys(filtered).length > 0 ? getQueryParamString(filtered) : "";

  return makeRequest<JourneyListResponse>({
    url: `${API_BASE_URL}${API_ROUTES.JOURNEY_LIST.apiPath}${suffix}`,
    init: {
      method: API_ROUTES.JOURNEY_LIST.method,
    },
  });
}

/** GET /v1/funnels/:funnelId */
export async function fetchFunnelById(funnelId: string) {
  const encoded = encodeURIComponent(funnelId);
  return makeRequest<FunnelDetail>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_DETAILS.apiPath}/${encoded}`,
    init: {
      method: API_ROUTES.FUNNEL_DETAILS.method,
    },
  });
}

/** GET /v1/journeys/:journeyId */
export async function fetchJourneyById(journeyId: string) {
  const encoded = encodeURIComponent(journeyId);
  return makeRequest<JourneyDetail>({
    url: `${API_BASE_URL}${API_ROUTES.JOURNEY_DETAILS.apiPath}/${encoded}`,
    init: {
      method: API_ROUTES.JOURNEY_DETAILS.method,
    },
  });
}

/** POST /v1/funnels */
export async function createFunnel(payload: CreateFunnelRequestBody) {
  return makeRequest<FunnelDetail>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_CREATE.apiPath}`,
    init: {
      method: "POST",
      body: JSON.stringify(payload),
    },
  });
}

// -------- Funnel drop-off attribution (OTel correlation) --------

export type FunnelDropoffCauseKind =
  | "crash"
  | "anr"
  | "non_fatal"
  | "http_5xx"
  | "http_4xx"
  | "frozen_frame";

export type FunnelDropoffCause = {
  causeKind: FunnelDropoffCauseKind | string;
  causeKey: string;
  causeLabel: string;
  dropoffCohort: number;
  dropoffAffected: number;
  converterCohort: number;
  converterAffected: number;
  lift: number;
  dropoffRate: number;
  exampleSessionIds: string[];
};

export type FunnelDropoffResponse = {
  funnelId: number;
  stepIndex: number;
  stepName: string;
  mode: "UNIQUE_USERS" | "SESSIONS" | string;
  dropoffCohort: number;
  converterCohort: number;
  causes: FunnelDropoffCause[];
};

export type FunnelDropoffEvidence = {
  sessionId: string;
  userId: string;
  lastReachedAt: string;
  traceId: string;
  screen: string;
  appVersion: string;
  platform: string;
};

export type FunnelDropoffEvidenceResponse = {
  examples: FunnelDropoffEvidence[];
};

/** GET /v1/funnels/:funnelId/dropoffs/:stepIndex */
export async function fetchFunnelDropoff(
  funnelId: string,
  stepIndex: number,
  runTime?: string,
) {
  const qs = runTime ? `?runTime=${encodeURIComponent(runTime)}` : "";
  return makeRequest<FunnelDropoffResponse>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_DROPOFF.apiPath}/${encodeURIComponent(
      funnelId,
    )}/dropoffs/${stepIndex}${qs}`,
    init: {
      method: API_ROUTES.FUNNEL_DROPOFF.method,
    },
  });
}

/** GET /v1/funnels/:funnelId/dropoffs/:stepIndex/evidence */
export async function fetchFunnelDropoffEvidence(
  funnelId: string,
  stepIndex: number,
  sessionIds: string[],
  runTime?: string,
) {
  const parts: string[] = [];
  if (sessionIds?.length) {
    parts.push(`sessionIds=${encodeURIComponent(sessionIds.join(","))}`);
  }
  if (runTime) {
    parts.push(`runTime=${encodeURIComponent(runTime)}`);
  }
  const qs = parts.length ? `?${parts.join("&")}` : "";
  return makeRequest<FunnelDropoffEvidenceResponse>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_DROPOFF_EVIDENCE.apiPath}/${encodeURIComponent(
      funnelId,
    )}/dropoffs/${stepIndex}/evidence${qs}`,
    init: {
      method: API_ROUTES.FUNNEL_DROPOFF_EVIDENCE.method,
    },
  });
}

/** POST /v1/journeys */
export async function createJourney(payload: CreateJourneyRequestBody) {
  return makeRequest<JourneyDetail>({
    url: `${API_BASE_URL}${API_ROUTES.JOURNEY_CREATE.apiPath}`,
    init: {
      method: API_ROUTES.JOURNEY_CREATE.method,
      body: JSON.stringify(payload),
    },
  });
}

/** PUT /v1/funnels/:funnelId */
export async function updateFunnel(
  funnelId: string,
  payload: UpdateFunnelRequestBody,
) {
  const encoded = encodeURIComponent(funnelId);
  return makeRequest<FunnelDetail>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_CREATE.apiPath}/${encoded}`,
    init: {
      method: "PUT",
      body: JSON.stringify(payload),
    },
  });
}

/**
 * POST /v1/funnels/:funnelId/stop — stop auto-refresh on an AUTO funnel.
 * Backend flips funnel_type to ONCE; the funnel becomes COMPLETED in the listing.
 * Idempotent — safe to call on an already-stopped funnel.
 */
export async function stopFunnel(funnelId: string) {
  const encoded = encodeURIComponent(funnelId);
  return makeRequest<string>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_STOP.apiPath}/${encoded}/stop`,
    init: {
      method: API_ROUTES.FUNNEL_STOP.method,
    },
  });
}

/**
 * DELETE /v1/funnels/:funnelId — cascading delete of a funnel.
 * Backend removes: funnel row, tag mappings, analytics_jobs rows for this funnel,
 * and any associated otel.funnel_results rows in ClickHouse (best-effort).
 */
export async function deleteFunnel(funnelId: string) {
  const encoded = encodeURIComponent(funnelId);
  return makeRequest<string>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_DELETE.apiPath}/${encoded}`,
    init: {
      method: API_ROUTES.FUNNEL_DELETE.method,
    },
  });
}

/**
 * DELETE /v1/journeys/:journeyId — cascading delete of a journey.
 * Mirrors {@link deleteFunnel}.
 */
export async function deleteJourney(journeyId: string) {
  const encoded = encodeURIComponent(journeyId);
  return makeRequest<string>({
    url: `${API_BASE_URL}${API_ROUTES.JOURNEY_DELETE.apiPath}/${encoded}`,
    init: {
      method: API_ROUTES.JOURNEY_DELETE.method,
    },
  });
}

/**
 * POST /v1/journeys/:journeyId/stop — stop auto-refresh on an AUTO journey.
 * Mirrors {@link stopFunnel}. Idempotent.
 */
export async function stopJourney(journeyId: string) {
  const encoded = encodeURIComponent(journeyId);
  return makeRequest<string>({
    url: `${API_BASE_URL}${API_ROUTES.JOURNEY_STOP.apiPath}/${encoded}/stop`,
    init: {
      method: API_ROUTES.JOURNEY_STOP.method,
    },
  });
}

/** PUT /v1/journeys/:journeyId */
export async function updateJourney(
  journeyId: string,
  payload: CreateJourneyRequestBody,
) {
  const encoded = encodeURIComponent(journeyId);
  return makeRequest<JourneyDetail>({
    url: `${API_BASE_URL}${API_ROUTES.JOURNEY_CREATE.apiPath}/${encoded}`,
    init: {
      method: "PUT",
      body: JSON.stringify(payload),
    },
  });
}

/** POST /v1/funnels/grouped — fetch funnel results broken down by a grouping dimension. */
export async function fetchFunnelGrouped(body: FunnelGroupedRequestBody) {
  const payload = { ...body, timeRange: formatTimeRange(body.timeRange) };
  return makeRequest<FunnelGroupedResponse>({
    url: `${API_BASE_URL}${FUNNELS_BASE}/grouped`,
    init: { method: "POST", body: JSON.stringify(payload) },
  });
}

// ─── Funnel metadata (lookup / options endpoints) ────────────────────────────

/** GET /v1/funnels/eventsList — fetch all available event names for funnel step selection. */
export async function fetchFunnelEvents() {
  return makeRequest<FunnelEventsResponse>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_EVENTS.apiPath}`,
    init: { method: API_ROUTES.FUNNEL_EVENTS.method },
  });
}

/** GET /v1/funnels/filters — fetch the list of available filter key strings for the project. */
export async function fetchFunnelFilters() {
  return makeRequest<FunnelFiltersResponse>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_FILTERS.apiPath}`,
    init: { method: API_ROUTES.FUNNEL_FILTERS.method },
  });
}

/** GET /v1/funnels/filters/:filterKey/values — fetch all possible values for one filter key. */
export async function fetchFunnelFilterValues(filterKey: string) {
  const encoded = encodeURIComponent(filterKey);
  return makeRequest<FunnelFilterValuesResponse>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_FILTERS.apiPath}/${encoded}/values`,
    init: { method: "GET" },
  });
}

/** GET /v1/funnels/tags — fetch all tags that have been applied to saved funnels. */
export async function fetchTags() {
  return makeRequest<TagsResponse>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_TAGS.apiPath}`,
    init: { method: API_ROUTES.FUNNEL_TAGS.method },
  });
}
