import { useQuery, UseQueryResult } from "@tanstack/react-query";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useProjectContext } from "../../contexts";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import type { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";
import type {
  HeatmapDataResponse,
  HeatmapIncludeLayer,
} from "../../screens/ScreenDetail/Heatmap/heatmap.types";
import {
  fetchHeatmapDataGet,
  fetchHeatmapDataPost,
} from "../../screens/ScreenDetail/Heatmap/heatmapApi";

dayjs.extend(utc);

const HEATMAP_500_MAX_RETRIES = 3;

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Re-run request up to 3 extra times when the server returns HTTP 500 only. */
async function fetchHeatmapWith500Retry(
  fetchOnce: () => Promise<ApiResponse<HeatmapDataResponse>>,
): Promise<ApiResponse<HeatmapDataResponse>> {
  let response = await fetchOnce();
  for (
    let attempt = 0;
    attempt < HEATMAP_500_MAX_RETRIES && response.status === 500;
    attempt += 1
  ) {
    await delay(350 * (attempt + 1));
    response = await fetchOnce();
  }
  return response;
}

export interface UseHeatmapDataParams {
  screenName: string;
  startTime: string;
  endTime: string;
  app_version?: string;
  platform?: string;
  aspect_ratio?: string;
  cohort_id?: string;
  /** Comma-separated layer ids for GET `layers` (glow, frustration, observability). */
  layers?: string;
  /** Use POST with project id when true (heavy filter body). Default false = GET. */
  usePost?: boolean;
  enabled?: boolean;
}

const ALLOWED_LAYERS = new Set<HeatmapIncludeLayer>([
  "glow",
  "frustration",
  "observability",
]);

function parseIncludeLayers(
  layers?: string,
): HeatmapIncludeLayer[] | undefined {
  if (!layers?.trim()) return undefined;
  const parts = layers
    .split(",")
    .map((s) => s.trim())
    .filter((s): s is HeatmapIncludeLayer =>
      ALLOWED_LAYERS.has(s as HeatmapIncludeLayer),
    );
  return parts.length ? parts : undefined;
}

const formatTime = (time: string): string => {
  if (!time) return "";
  if (time.includes("T") || time.includes("Z")) {
    return dayjs.utc(time).toISOString();
  }
  return dayjs.utc(time, "YYYY-MM-DD HH:mm:ss").toISOString();
};

/**
 * Fetches heatmap layers + metadata. Uses GET by default (useGetDataQuery-style reads).
 */
export const useHeatmapData = (
  params: UseHeatmapDataParams,
): UseQueryResult<ApiResponse<HeatmapDataResponse>, Error> => {
  const { projectId } = useProjectContext();
  const {
    screenName,
    startTime,
    endTime,
    app_version,
    platform,
    aspect_ratio,
    cohort_id,
    layers,
    usePost = false,
    enabled = true,
  } = params;

  const includeLayers = parseIncludeLayers(layers);

  const formattedStart = formatTime(startTime);
  const formattedEnd = formatTime(endTime);

  const isProjectReady = useProjectQueryEnabled(
    enabled &&
      !!screenName &&
      !!formattedStart &&
      !!formattedEnd &&
      (!usePost || !!projectId),
  );

  return useQuery({
    queryKey: [
      "heatmap",
      "data",
      usePost ? "post" : "get",
      projectId ?? "",
      screenName,
      formattedStart,
      formattedEnd,
      app_version ?? "",
      platform ?? "",
      aspect_ratio ?? "",
      cohort_id ?? "",
      layers ?? "",
    ],
    queryFn: async () => {
      if (usePost) {
        if (!projectId) {
          throw new Error("projectId required for POST heatmap data");
        }
        return fetchHeatmapWith500Retry(() =>
          fetchHeatmapDataPost(projectId, {
            screenName,
            timeRange: { start: formattedStart, end: formattedEnd },
            app_version,
            platform,
            aspect_ratio,
            cohort_id,
            includeLayers,
          }),
        );
      }
      return fetchHeatmapWith500Retry(() =>
        fetchHeatmapDataGet({
          screenName,
          from: formattedStart,
          to: formattedEnd,
          app_version,
          platform,
          aspect_ratio,
          cohort_id,
          layers,
        }),
      );
    },
    enabled: isProjectReady,
    staleTime: 10_000,
    refetchOnWindowFocus: false,
  });
};
