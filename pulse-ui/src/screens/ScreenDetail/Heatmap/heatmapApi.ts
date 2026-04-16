/**
 * Heatmap data access — pure functions using makeRequest.
 * Server returns { data, error }; wire payload in `data` is normalized in `useHeatmapData`.
 *
 * v1 heatmap reads are idempotent; consumers should use useQuery (see useHeatmapData).
 */

import { API_BASE_URL, API_ROUTES } from "../../../constants";
import { makeRequest } from "../../../helpers/makeRequest";
import type { ApiResponse } from "../../../helpers/makeRequest/makeRequest.interface";
import type {
  HeatmapDataQueryParams,
  HeatmapDataRequestBody,
  HeatmapDataWireResponse,
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
  if (params.region?.trim()) search.set("region", params.region.trim());
  if (params.breakpoint?.trim()) search.set("breakpoint", params.breakpoint.trim());
  return search.toString();
}

export async function fetchHeatmapDataGet(
  params: HeatmapDataQueryParams,
): Promise<ApiResponse<HeatmapDataWireResponse>> {
  const route = API_ROUTES.GET_HEATMAP_DATA;
  const qs = buildHeatmapDataQueryString(params);
  const url = `${API_BASE_URL}${route.apiPath}?${qs}`;
  return makeRequest<HeatmapDataWireResponse>({
    url,
    init: { method: route.method },
  });
}

export async function fetchHeatmapDataPost(
  projectId: string,
  body: HeatmapDataRequestBody,
): Promise<ApiResponse<HeatmapDataWireResponse>> {
  const route = API_ROUTES.POST_HEATMAP_DATA;
  const path = heatmapProjectPath(route.apiPath, projectId);
  const url = `${API_BASE_URL}${path}`;
  return makeRequest<HeatmapDataWireResponse>({
    url,
    init: {
      method: route.method,
      body: JSON.stringify(body),
    },
  });
}
