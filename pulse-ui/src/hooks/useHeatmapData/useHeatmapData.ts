import { useQuery, UseQueryResult } from "@tanstack/react-query";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useProjectContext } from "../../contexts";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import type { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";
import type {
  HeatmapDataResponse,
  HeatmapDataWireResponse,
} from "../../screens/ScreenDetail/Heatmap/heatmap.types";
import {
  fetchHeatmapDataGet,
  fetchHeatmapDataPost,
} from "../../screens/ScreenDetail/Heatmap/heatmapApi";
import { normalizeHeatmapWireResponse } from "../../screens/ScreenDetail/Heatmap/heatmapWireNormalize";

dayjs.extend(utc);

const HEATMAP_500_MAX_RETRIES = 3;

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

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

function mapWireResponse(
  res: ApiResponse<HeatmapDataWireResponse>,
): ApiResponse<HeatmapDataResponse> {
  return {
    ...res,
    data:
      res.data != null ? normalizeHeatmapWireResponse(res.data) : res.data,
  };
}

export interface UseHeatmapDataParams {
  screenName: string;
  startTime: string;
  endTime: string;
  app_version?: string;
  platform?: string;
  region?: string;
  breakpoint?: string;
  /** Use POST with project id when true (heavy filter body). Default false = GET. */
  usePost?: boolean;
  enabled?: boolean;
}

const formatTime = (time: string): string => {
  if (!time) return "";
  if (time.includes("T") || time.includes("Z")) {
    return dayjs.utc(time).toISOString();
  }
  return dayjs.utc(time, "YYYY-MM-DD HH:mm:ss").toISOString();
};

/**
 * Fetches heatmap layers + metadata. Uses GET by default.
 * Wire JSON is normalized once (rage_taps/dead_taps → rage/dead).
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
    region,
    breakpoint,
    usePost = false,
    enabled = true,
  } = params;

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
      region ?? "",
      breakpoint ?? "",
    ],
    queryFn: async () => {
      if (usePost) {
        if (!projectId) {
          throw new Error("projectId required for POST heatmap data");
        }
        return fetchHeatmapWith500Retry(async () => {
          const res = await fetchHeatmapDataPost(projectId, {
            screenName,
            timeRange: { start: formattedStart, end: formattedEnd },
            app_version,
            platform,
            region: region?.trim() || undefined,
            breakpoint: breakpoint?.trim() || undefined,
          });
          return mapWireResponse(res);
        });
      }
      return fetchHeatmapWith500Retry(async () => {
        const res = await fetchHeatmapDataGet({
          screenName,
          from: formattedStart,
          to: formattedEnd,
          app_version,
          platform,
          region: region?.trim() || undefined,
          breakpoint: breakpoint?.trim() || undefined,
        });
        return mapWireResponse(res);
      });
    },
    enabled: isProjectReady,
    staleTime: 10_000,
    refetchOnWindowFocus: false,
  });
};
