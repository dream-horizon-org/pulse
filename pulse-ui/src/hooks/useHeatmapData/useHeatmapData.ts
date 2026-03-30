import { useQuery, UseQueryResult } from "@tanstack/react-query";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import { useProjectContext } from "../../contexts";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import type { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";
import type { HeatmapDataResponse } from "../../screens/ScreenDetail/Heatmap/heatmap.types";
import {
  fetchHeatmapDataGet,
  fetchHeatmapDataPost,
} from "../../screens/ScreenDetail/Heatmap/heatmapApi";

dayjs.extend(utc);

export interface UseHeatmapDataParams {
  screenName: string;
  startTime: string;
  endTime: string;
  app_version?: string;
  platform?: string;
  aspect_ratio?: string;
  cohort_id?: string;
  /** Mock: from Screen URL `rcaHeatmapSignal` — aligns heatmap with RCA narrative. */
  rcaHeatmapSignal?: string | null;
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
    rcaHeatmapSignal,
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
      aspect_ratio ?? "",
      cohort_id ?? "",
      rcaHeatmapSignal?.trim() ?? "",
    ],
    queryFn: async () => {
      const rca =
        rcaHeatmapSignal?.trim() !== ""
          ? rcaHeatmapSignal?.trim()
          : undefined;
      if (usePost) {
        if (!projectId) {
          throw new Error("projectId required for POST heatmap data");
        }
        return fetchHeatmapDataPost(projectId, {
          screenName,
          timeRange: { start: formattedStart, end: formattedEnd },
          app_version,
          platform,
          aspect_ratio,
          cohort_id,
          ...(rca ? { rcaHeatmapSignal: rca } : {}),
        });
      }
      return fetchHeatmapDataGet({
        screenName,
        from: formattedStart,
        to: formattedEnd,
        app_version,
        platform,
        aspect_ratio,
        cohort_id,
        ...(rca ? { rcaHeatmapSignal: rca } : {}),
      });
    },
    enabled: isProjectReady,
    staleTime: 10_000,
    refetchOnWindowFocus: false,
  });
};
