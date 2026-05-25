import { API_BASE_URL, API_ROUTES } from "../constants";
import { makeRequest } from "../helpers/makeRequest";
import type { RevenueEventConfig } from "../screens/EventCatalog/RevenueEvent.types";

export type RevenueEventListResponse = {
  revenueEvents: RevenueEventConfig[];
};

export type CreateRevenueEventRequestBody = {
  eventName: string;
  valueAttribute: string;
  currency: string;
  currencyAttribute?: string;
  conversionWindowHours: number;
};

export type UpdateRevenueEventRequestBody = CreateRevenueEventRequestBody;

function mapRevenueEvent(raw: RevenueEventConfig): RevenueEventConfig {
  return {
    id: raw.id,
    eventName: raw.eventName,
    valueAttribute: raw.valueAttribute,
    currency: raw.currency ?? "",
    currencyAttribute: raw.currencyAttribute,
    conversionWindowHours: raw.conversionWindowHours,
    configuredAt:
      typeof raw.configuredAt === "string"
        ? raw.configuredAt
        : new Date(raw.configuredAt).toISOString(),
  };
}

/** GET /v1/revenue-events */
export async function listRevenueEvents() {
  const response = await makeRequest<RevenueEventListResponse>({
    url: `${API_BASE_URL}${API_ROUTES.REVENUE_EVENT_LIST.apiPath}`,
    init: { method: API_ROUTES.REVENUE_EVENT_LIST.method },
  });
  if (response.data?.revenueEvents) {
    response.data.revenueEvents = response.data.revenueEvents.map(mapRevenueEvent);
  }
  return response;
}

/** POST /v1/revenue-events */
export async function createRevenueEvent(payload: CreateRevenueEventRequestBody) {
  const response = await makeRequest<RevenueEventConfig>({
    url: `${API_BASE_URL}${API_ROUTES.REVENUE_EVENT_CREATE.apiPath}`,
    init: {
      method: API_ROUTES.REVENUE_EVENT_CREATE.method,
      body: JSON.stringify(payload),
    },
  });
  if (response.data) {
    response.data = mapRevenueEvent(response.data);
  }
  return response;
}

/** PUT /v1/revenue-events/:id */
export async function updateRevenueEvent(
  id: string,
  payload: UpdateRevenueEventRequestBody,
) {
  const encoded = encodeURIComponent(id);
  return makeRequest<string>({
    url: `${API_BASE_URL}${API_ROUTES.REVENUE_EVENT_UPDATE.apiPath}/${encoded}`,
    init: {
      method: API_ROUTES.REVENUE_EVENT_UPDATE.method,
      body: JSON.stringify(payload),
    },
  });
}

/** DELETE /v1/revenue-events/:id */
export async function deleteRevenueEvent(id: string) {
  const encoded = encodeURIComponent(id);
  return makeRequest<string>({
    url: `${API_BASE_URL}${API_ROUTES.REVENUE_EVENT_DELETE.apiPath}/${encoded}`,
    init: { method: API_ROUTES.REVENUE_EVENT_DELETE.method },
  });
}
