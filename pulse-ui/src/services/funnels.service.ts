import { API_BASE_URL } from "../constants";
import { makeRequest } from "../helpers/makeRequest";
import { getQueryParamString } from "../helpers/queryParams";

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
  funnelType?: "ORDERED" | "UNORDERED";
  /** Overall conversion rate (%) for funnels with computed metrics. */
  overallConversionRate?: number;
  /** Change vs prior period (percentage points); positive = up. */
  conversionTrend?: number;
};

export type FunnelsJourneysListFilterOptions = {
  creators: string[];
  tags: string[];
};

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

/** Query params for GET /v1/funnels or GET /v1/journeys (resource implied by path). */
export type FunnelJourneyListQueryParams = {
  search?: string | null;
  status?: "ACTIVE" | "STOPPED" | "CREATING" | "UPDATING" | "COMPLETED" | null;
  /** Match if created by any of these users. */
  createdBy?: string[] | null;
  /** Match if item has any of these tags. */
  tags?: string[] | null;
  /** Funnel listing only (GET /v1/funnels). */
  funnelType?: "ORDERED" | "UNORDERED" | null;
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
  rollingType?: "RECURRING" | "ONCE";
  filters?: any[];
  steps?: any[];
  timeRange?: any;
  windowSeconds?: number;
  anchorEvent?: string;
  direction?: string;
  depth?: number;
  expiryDate?: string;
};

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
  if (params.funnelType) out.funnelType = params.funnelType;
  if (params.page != null && params.page > 0) out.page = String(params.page);
  if (params.pageSize != null && params.pageSize > 0) {
    out.pageSize = String(params.pageSize);
  }
  return out;
}

/** GET /v1/funnels */
export async function fetchFunnelsList(queryParams: FunnelJourneyListQueryParams) {
  const filtered = filterListParams(queryParams);
  const suffix =
    Object.keys(filtered).length > 0 ? getQueryParamString(filtered) : "";

  return makeRequest<FunnelsJourneysListResponse>({
    url: `${API_BASE_URL}${FUNNELS_BASE}${suffix}`,
    init: {
      method: "GET",
    },
  });
}

/** GET /v1/journeys */
export async function fetchJourneysList(queryParams: FunnelJourneyListQueryParams) {
  const filtered = filterListParams(queryParams);
  const suffix =
    Object.keys(filtered).length > 0 ? getQueryParamString(filtered) : "";

  return makeRequest<FunnelsJourneysListResponse>({
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
export async function createFunnel(payload: Record<string, unknown>) {
  return makeRequest<FunnelJourneyDetail>({
    url: `${API_BASE_URL}${FUNNELS_BASE}`,
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
export async function updateFunnel(funnelId: string, payload: unknown) {
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
