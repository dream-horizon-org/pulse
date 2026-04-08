/**
 * Heatmap data access — pure functions using makeRequest.
 * Server returns { data, error }; heatmap payload is in data.
 *
 * v1 heatmap reads are idempotent; consumers should use useQuery (see useHeatmapData).
 * useMutation is reserved for future state-changing endpoints (e.g. pin layout).
 */

import { API_BASE_URL, API_ROUTES } from "../../../constants";
import { makeRequest } from "../../../helpers/makeRequest";
import type { ApiResponse } from "../../../helpers/makeRequest/makeRequest.interface";
import type {
  HeatmapDataQueryParams,
  HeatmapDataRequestBody,
  HeatmapDataResponse,
} from "./heatmap.types";

export function heatmapProjectPath(
  apiPath: string,
  projectId: string,
): string {
  return apiPath.replace(":projectId", encodeURIComponent(projectId));
}

export function buildHeatmapDataQueryString(
  params: HeatmapDataQueryParams,
): string {
  const search = new URLSearchParams();
  search.set("screenName", params.screenName);
  if (params.from) search.set("from", params.from);
  if (params.to) search.set("to", params.to);
  if (params.app_version) search.set("app_version", params.app_version);
  if (params.platform) search.set("platform", params.platform);
  if (params.aspect_ratio) search.set("aspect_ratio", params.aspect_ratio);
  if (params.cohort_id) search.set("cohort_id", params.cohort_id);
  if (params.layers) search.set("layers", params.layers);
  return search.toString();
}

export async function fetchHeatmapDataGet(
  params: HeatmapDataQueryParams,
): Promise<ApiResponse<HeatmapDataResponse>> {
  const route = API_ROUTES.GET_HEATMAP_DATA;
  const qs = buildHeatmapDataQueryString(params);
  const url = `${API_BASE_URL}${route.apiPath}?${qs}`;
  return makeRequest<HeatmapDataResponse>({
    url,
    init: { method: route.method },
  });
}

export async function fetchHeatmapDataPost(
  projectId: string,
  body: HeatmapDataRequestBody,
): Promise<ApiResponse<HeatmapDataResponse>> {
  const route = API_ROUTES.POST_HEATMAP_DATA;
  const path = heatmapProjectPath(route.apiPath, projectId);
  const url = `${API_BASE_URL}${path}`;
  return makeRequest<HeatmapDataResponse>({
    url,
    init: {
      method: route.method,
      body: JSON.stringify(body),
    },
  });
}
