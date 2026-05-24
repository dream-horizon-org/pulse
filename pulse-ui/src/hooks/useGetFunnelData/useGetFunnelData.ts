import { useQueries, useQuery } from "@tanstack/react-query";
import { GetFunnelGroupedParams } from "./useGetFunnelData.interface";
import {
  fetchFunnelEvents,
  fetchFunnelScreens,
  fetchFunnelFilters,
  fetchFunnelFilterValues,
  fetchFunnelGrouped,
  fetchTags,
} from "../../services/funnels.service";

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

export const useGetFunnelEvents = () => {
  return useQuery({
    queryKey: ["FUNNEL_EVENTS"],
    queryFn: () => fetchFunnelEvents(),
    refetchOnWindowFocus: false,
    staleTime: 300000,
  });
};

export const useGetFunnelScreens = () => {
  return useQuery({
    queryKey: ["FUNNEL_SCREENS"],
    queryFn: () => fetchFunnelScreens(),
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
    queryKey: ["FUNNEL_TAGS"],
    queryFn: () => fetchTags(),
    refetchOnWindowFocus: false,
    staleTime: 300000,
  });
};
