import { API_BASE_URL, API_ROUTES } from "../constants";
import { makeRequest } from "../helpers/makeRequest";
import { getQueryParamString } from "../helpers/queryParams";
import type { FunnelStep } from "../hooks/useGetFunnelData/useGetFunnelData.interface";
import type { FilterField, TimeRange } from "../hooks/useGetDataQuery/useGetDataQuery.interface";

/** Funnel schedule type: AUTO refreshes on a rolling window; ONCE is computed once. */
export enum FunnelType {
  AUTO = "AUTO",
  ONCE = "ONCE",
}

/** Whether funnel steps must be completed in order or in any order. */
export enum StepOrderType {
  ORDERED = "ordered",
  UNORDERED = "unordered",
}

/** Saved funnel or journey row returned by the listing API. */
export type FunnelJourneyListItem = {
  id: string;
  name: string;
  kind: "FUNNEL" | "JOURNEY";
  status: "ACTIVE" | "STOPPED" | "CREATING" | "UPDATING" | "COMPLETED";
  createdBy: string;
  lastUpdatedAt: string;
  tags: string[];
  /** Present when kind === "FUNNEL". */
  stepOrderType?: StepOrderType;
  /** Overall conversion rate (%) for funnels with computed metrics. */
  overallConversionRate?: number;
  /** Change vs prior period (percentage points); positive = up. */
  conversionTrend?: number;
};

export type FunnelsJourneysListFilterOptions = {
  creators: string[];
  tags: string[];
};

/** Filter options for GET /v1/funnels or GET /v1/journeys (same shape). */
export type FunnelListFilterOptions = FunnelsJourneysListFilterOptions;
export type JourneyListFilterOptions = FunnelsJourneysListFilterOptions;

export type FunnelsJourneysListResponse = {
  items: FunnelJourneyListItem[];
  filterOptions: FunnelsJourneysListFilterOptions;
  /** Total items matching filters (before pagination). Omitted by some backends. */
  totalCount?: number;
  /** Current page (1-based). */
  page?: number;
  pageSize?: number;
  /** Total pages for current filters and page size. */
  totalPages?: number;
};

/** Listing payload for GET /v1/funnels. */
export type FunnelListResponse = FunnelsJourneysListResponse;

/** Listing payload for GET /v1/journeys. */
export type JourneyListResponse = FunnelsJourneysListResponse;

/** Query params for GET /v1/funnels or GET /v1/journeys (resource implied by path). */
export type FunnelJourneyListQueryParams = {
  search?: string | null;
  status?: "ACTIVE" | "STOPPED" | "CREATING" | "UPDATING" | "COMPLETED" | null;
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

/** @deprecated Use FunnelJourneyListQueryParams */
export type FunnelsJourneysListQueryParams = FunnelJourneyListQueryParams & {
  kind?: "FUNNEL" | "JOURNEY" | null;
};

/** Single funnel or journey returned by detail APIs. */
export type FunnelJourneyDetail = FunnelJourneyListItem & {
  description: string;
  createdAt: string;
  funnelType?: FunnelType;
  filters?: FilterField[];
  steps?: FunnelStep[];
  timeRange?: TimeRange;
  windowSeconds?: number;
  anchorEvent?: string;
  direction?: string;
  depth?: number;
  expiryDate?: string;
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
  /**
   * Analysis time window.
   * For ONCE funnels this is the fixed start/end range.
   * For AUTO funnels this is the rolling window anchor (e.g. last 7 d).
   */
  timeRange: TimeRange;
  /** Maximum seconds a user has to complete the funnel after entering step 1. */
  windowSeconds: number;
  /** Audience filters applied when computing conversion. */
  filters?: FilterField[];
  /**
   * ISO-8601 datetime after which an AUTO funnel stops refreshing.
   * Ignored for ONCE funnels.
   */
  expiryDate?: string;
}

/**
 * Request body for PUT /v1/funnel/:id.
 * PUT is a full replace so it accepts the same shape as create.
 */
export type UpdateFunnelRequestBody = CreateFunnelRequestBody;

const FUNNELS_BASE = "/v1/funnels";
const JOURNEYS_BASE = "/v1/journeys";

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
    url: `${API_BASE_URL}${FUNNELS_BASE}${suffix}`,
    init: {
      method: "GET",
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
    url: `${API_BASE_URL}${JOURNEYS_BASE}${suffix}`,
    init: {
      method: "GET",
    },
  });
}

/** GET /v1/funnels/:funnelId */
export async function fetchFunnelById(funnelId: string) {
  const encoded = encodeURIComponent(funnelId);
  return makeRequest<FunnelJourneyDetail>({
    url: `${API_BASE_URL}${FUNNELS_BASE}/${encoded}`,
    init: {
      method: "GET",
    },
  });
}

/** GET /v1/journeys/:journeyId */
export async function fetchJourneyById(journeyId: string) {
  const encoded = encodeURIComponent(journeyId);
  return makeRequest<FunnelJourneyDetail>({
    url: `${API_BASE_URL}${JOURNEYS_BASE}/${encoded}`,
    init: {
      method: "GET",
    },
  });
}

/** POST /v1/funnels */
export async function createFunnel(payload: CreateFunnelRequestBody) {
  return makeRequest<FunnelJourneyDetail>({
    url: `${API_BASE_URL}${API_ROUTES.FUNNEL_CREATE.apiPath}`,
    init: {
      method: "POST",
      body: JSON.stringify(payload),
    },
  });
}

/** POST /v1/journeys */
export async function createJourney(payload: Record<string, unknown>) {
  return makeRequest<FunnelJourneyDetail>({
    url: `${API_BASE_URL}${JOURNEYS_BASE}`,
    init: {
      method: "POST",
      body: JSON.stringify(payload),
    },
  });
}

/** PUT /v1/funnels/:funnelId */
export async function updateFunnel(funnelId: string, payload: UpdateFunnelRequestBody) {
  const encoded = encodeURIComponent(funnelId);
  return makeRequest<FunnelJourneyDetail>({
    url: `${API_BASE_URL}${FUNNELS_BASE}/${encoded}`,
    init: {
      method: "PUT",
      body: JSON.stringify(payload),
    },
  });
}

/** PUT /v1/journeys/:journeyId */
export async function updateJourney(journeyId: string, payload: unknown) {
  const encoded = encodeURIComponent(journeyId);
  return makeRequest<FunnelJourneyDetail>({
    url: `${API_BASE_URL}${JOURNEYS_BASE}/${encoded}`,
    init: {
      method: "PUT",
      body: JSON.stringify(payload),
    },
  });
}
