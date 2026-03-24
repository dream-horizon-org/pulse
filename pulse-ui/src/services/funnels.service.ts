import { API_BASE_URL } from "../constants";
import { makeRequest } from "../helpers/makeRequest";
import { getQueryParamString } from "../helpers/queryParams";

/** Saved funnel or journey row returned by the listing API. */
export type FunnelJourneyListItem = {
  id: string;
  name: string;
  kind: "FUNNEL" | "JOURNEY";
  status: "ACTIVE" | "STOPPED" | "CREATING";
  createdBy: string;
  lastUpdatedAt: string;
  tags: string[];
  /** Present when kind === "FUNNEL". */
  funnelType?: "ORDERED" | "UNORDERED";
};

export type FunnelsJourneysListFilterOptions = {
  creators: string[];
  tags: string[];
};

export type FunnelsJourneysListResponse = {
  items: FunnelJourneyListItem[];
  filterOptions: FunnelsJourneysListFilterOptions;
};

export type FunnelsJourneysListQueryParams = {
  /** When set, only return funnels or only journeys. */
  kind?: "FUNNEL" | "JOURNEY" | null;
  search?: string | null;
  status?: "ACTIVE" | "STOPPED" | "CREATING" | null;
  /** Match if created by any of these users. */
  createdBy?: string[] | null;
  /** Match if item has any of these tags. */
  tags?: string[] | null;
  funnelType?: "ORDERED" | "UNORDERED" | null;
};

/** Single funnel or journey returned by the detail API. */
export type FunnelJourneyDetail = FunnelJourneyListItem & {
  description: string;
  createdAt: string;
};

// TODO: update when backend is ready (path, query param names, and response shape may change).
const FUNNELS_JOURNEYS_LIST_PATH = "/v1/funnels-journeys";

function filterNonNullParams(
  params: FunnelsJourneysListQueryParams,
): Record<string, string> {
  const out: Record<string, string> = {};
  if (params.kind) out.kind = params.kind;
  if (params.search) out.search = params.search;
  if (params.status) out.status = params.status;
  if (params.createdBy?.length) out.createdBy = params.createdBy.join(",");
  if (params.tags?.length) out.tags = params.tags.join(",");
  if (params.funnelType) out.funnelType = params.funnelType;
  return out;
}

/**
 * Fetches saved funnels and journeys for the current project (project id is sent via request headers).
 * TODO: update when backend is ready.
 */
export async function fetchFunnelsJourneysList(
  queryParams: FunnelsJourneysListQueryParams,
) {
  const filtered = filterNonNullParams(queryParams);
  const suffix =
    Object.keys(filtered).length > 0 ? getQueryParamString(filtered) : "";

  return makeRequest<FunnelsJourneysListResponse>({
    url: `${API_BASE_URL}${FUNNELS_JOURNEYS_LIST_PATH}${suffix}`,
    init: {
      method: "GET",
    },
  });
}

/**
 * Fetches one saved funnel or journey by id (project id is sent via headers).
 * TODO: update when backend is ready.
 */
export async function fetchFunnelJourneyById(id: string) {
  const encoded = encodeURIComponent(id);
  return makeRequest<FunnelJourneyDetail>({
    url: `${API_BASE_URL}${FUNNELS_JOURNEYS_LIST_PATH}/${encoded}`,
    init: {
      method: "GET",
    },
  });
}

/**
 * Creates a new funnel or journey.
 * TODO: update when backend is ready.
 */
export async function createFunnelJourney(payload: any) {
  return makeRequest<FunnelJourneyDetail>({
    url: `${API_BASE_URL}${FUNNELS_JOURNEYS_LIST_PATH}`,
    init: {
      method: "POST",
      body: JSON.stringify(payload),
    },
  });
}
