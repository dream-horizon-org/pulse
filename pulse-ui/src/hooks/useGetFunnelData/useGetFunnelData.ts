import { useQueries, useQuery } from "@tanstack/react-query";
import {
  GetFunnelDataParams,
  GetFunnelGroupedParams,
  GetFunnelSessionsParams,
  GetFunnelTrendParams,
  GetJourneyParams,
} from "./useGetFunnelData.interface";
import {
  analyzeFunnel,
  exploreJourney,
  fetchFunnelEvents,
  fetchFunnelFilterValues,
  fetchFunnelFilters,
  fetchFunnelGrouped,
  fetchFunnelSessions,
  fetchFunnelTrend,
  fetchTags,
} from "../../services/funnels.service";

export const useGetFunnelData = ({
  requestBody,
  enabled = true,
}: GetFunnelDataParams) => {
  return useQuery({
    queryKey: [
      "FUNNEL_CREATE",
      JSON.stringify(requestBody.steps),
      requestBody.timeRange.start,
      requestBody.timeRange.end,
      JSON.stringify(requestBody.filters),
      requestBody.groupBy,
      requestBody.mode,
      requestBody.windowSeconds,
    ],
    queryFn: () => analyzeFunnel(requestBody),
    refetchOnWindowFocus: false,
    enabled:
      enabled &&
      requestBody.steps.length >= 2 &&
      requestBody.steps.every((s) => s.eventName.trim() !== ""),
    staleTime: 10000,
  });
};

export const useGetFunnelSessions = ({
  requestBody,
  enabled = true,
}: GetFunnelSessionsParams) => {
  return useQuery({
    queryKey: [
      "FUNNEL_SESSIONS",
      JSON.stringify(requestBody.steps),
      requestBody.timeRange.start,
      requestBody.timeRange.end,
      requestBody.stepLevel,
      requestBody.issueType,
      requestBody.mode,
    ],
    queryFn: () => fetchFunnelSessions(requestBody),
    refetchOnWindowFocus: false,
    enabled: enabled && requestBody.stepLevel >= 1,
    staleTime: 10000,
  });
};

export const useGetFunnelTrend = ({
  requestBody,
  enabled = true,
}: GetFunnelTrendParams) => {
  return useQuery({
    queryKey: [
      "FUNNEL_TREND",
      JSON.stringify(requestBody.steps),
      requestBody.timeRange.start,
      requestBody.timeRange.end,
      requestBody.mode,
    ],
    queryFn: () => fetchFunnelTrend(requestBody),
    refetchOnWindowFocus: false,
    enabled:
      enabled &&
      requestBody.steps.length >= 2 &&
      requestBody.steps.every((s) => s.eventName.trim() !== ""),
    staleTime: 10000,
  });
};

export const useGetFunnelGrouped = ({
  requestBody,
  enabled = true,
}: GetFunnelGroupedParams) => {
  return useQuery({
    queryKey: [
      "FUNNEL_GROUPED",
      JSON.stringify(requestBody.steps),
      requestBody.timeRange.start,
      requestBody.timeRange.end,
      requestBody.groupBy,
      requestBody.mode,
    ],
    queryFn: () => fetchFunnelGrouped(requestBody),
    refetchOnWindowFocus: false,
    enabled: enabled && !!requestBody.groupBy && requestBody.groupBy !== "none",
    staleTime: 10000,
  });
};

export const useGetJourneyData = ({
  requestBody,
  enabled = true,
}: GetJourneyParams) => {
  return useQuery({
    queryKey: [
      "JOURNEY_EXPLORE",
      requestBody.direction,
      requestBody.anchorEvent,
      requestBody.depth,
      requestBody.timeRange.start,
      requestBody.timeRange.end,
      JSON.stringify(requestBody.filters),
    ],
    queryFn: () => exploreJourney(requestBody),
    refetchOnWindowFocus: false,
    enabled: enabled && !!requestBody.anchorEvent,
    staleTime: 10000,
  });
};

export const useGetFunnelEvents = () => {
  return useQuery({
    queryKey: ["FUNNEL_EVENTS"],
    queryFn: () => fetchFunnelEvents(),
    refetchOnWindowFocus: false,
    staleTime: 300000,
  });
};

export const useGetFunnelFilters = () => {
  return useQuery({
    queryKey: ["FUNNEL_FILTERS"],
    queryFn: () => fetchFunnelFilters(),
    refetchOnWindowFocus: false,
    staleTime: 300000,
  });
};

/**
 * Fetches values for each provided filter key in parallel.
 * Only fires when `enabled` is true (e.g. when the user reaches step 4).
 * Returns an ordered array of query results matching the `filterKeys` array.
 */
export const useGetAllFilterValues = (
  filterKeys: string[],
  enabled: boolean,
) => {
  return useQueries({
    queries: filterKeys.map((key) => ({
      queryKey: ["FUNNEL_FILTER_VALUES", key],
      queryFn: () => fetchFunnelFilterValues(key),
      enabled,
      staleTime: 300000,
      refetchOnWindowFocus: false,
    })),
  });
};

export const useGetTags = () => {
  return useQuery({
    queryKey: ["GET_TAGS"],
    queryFn: () => fetchTags(),
    refetchOnWindowFocus: false,
    staleTime: 300000,
  });
};
