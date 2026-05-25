import { useMemo } from "react";
import { useInfiniteQuery, type InfiniteData } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../../../../constants";
import { makeRequest } from "../../../../../helpers/makeRequest";
import type { ApiResponse } from "../../../../../helpers/makeRequest/makeRequest.interface";
import type { DataQueryResponse } from "../../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import { useProjectQueryEnabled } from "../../../../../hooks/useProjectQueryEnabled";
import {
  classifyError,
  getErrorMessage,
} from "../../../../../utils/errorHandling";
import { useExceptionTimestamps } from "./useExceptionTimestamps";
import {
  buildExceptionListRequestBody,
  flattenExceptionListPages,
  extractGroupIdsFromResponse,
  getEventNameForTimestamps,
  mapExceptionRowsToIssues,
  tryFormatTimeToIso,
  withIsoTimeRange,
  type ExceptionListFilterParams,
  type ExceptionIssue,
} from "./exceptionListShared";
import { EXCEPTION_LIST_PAGE_SIZE } from "../exceptionList.constants";

type ExceptionListPage = ApiResponse<DataQueryResponse>;
type ExceptionListInfiniteData = InfiniteData<ExceptionListPage, number>;

export function useExceptionListInfiniteData(
  params: ExceptionListFilterParams,
) {
  const {
    startTime,
    endTime,
    appVersion = "all",
    osVersion = "all",
    device = "all",
    screenName,
    exceptionType,
  } = params;

  const formattedStart = tryFormatTimeToIso(startTime);
  const formattedEnd = tryFormatTimeToIso(endTime);
  const hasValidTimeRange = formattedStart != null && formattedEnd != null;
  const isProjectReady = useProjectQueryEnabled(
    !!startTime && !!endTime && hasValidTimeRange,
  );

  const filterKey = JSON.stringify({
    startTime,
    endTime,
    appVersion,
    osVersion,
    device,
    platform: params.platform ?? "all",
    networkProvider: params.networkProvider ?? "all",
    state: params.state ?? "all",
    screenName,
    exceptionType,
  });

  const infinite = useInfiniteQuery<ExceptionListPage, Error>({
    queryKey: ["EXCEPTION_LIST_INFINITE", filterKey, EXCEPTION_LIST_PAGE_SIZE],
    initialPageParam: 0,
    enabled: isProjectReady,
    queryFn: async ({ pageParam }): Promise<ExceptionListPage> => {
      const offset = pageParam as number;
      const body = withIsoTimeRange(
        buildExceptionListRequestBody(params, offset),
      );
      return makeRequest<DataQueryResponse>({
        url: `${API_BASE_URL}${API_ROUTES.DATA_QUERY.apiPath}`,
        init: {
          method: API_ROUTES.DATA_QUERY.method,
          body: JSON.stringify(body),
        },
      });
    },
    getNextPageParam: (lastPage, _allPages, lastPageParam) => {
      if (lastPage.error || !lastPage.data?.rows) return undefined;
      const n = lastPage.data.rows.length;
      if (n < EXCEPTION_LIST_PAGE_SIZE) return undefined;
      return (lastPageParam as number) + EXCEPTION_LIST_PAGE_SIZE;
    },
    staleTime: 10000,
  });

  const infiniteData = infinite.data as ExceptionListInfiniteData | undefined;
  const { fields, rows } = useMemo(
    () => flattenExceptionListPages(infiniteData?.pages ?? []),
    [infiniteData?.pages],
  );

  const groupIds = useMemo(() => {
    const ids = new Set<string>();
    for (const page of infiniteData?.pages ?? []) {
      for (const id of extractGroupIdsFromResponse(page.data)) {
        ids.add(id);
      }
    }
    return Array.from(ids).sort();
  }, [infiniteData?.pages]);

  const { timestampsMap } = useExceptionTimestamps({
    groupIds,
    appVersion,
    osVersion,
    device,
    screenName,
    eventName: getEventNameForTimestamps(exceptionType),
  });

  const exceptions: ExceptionIssue[] = useMemo(
    () => mapExceptionRowsToIssues(rows, fields, exceptionType, timestampsMap),
    [rows, fields, exceptionType, timestampsMap],
  );

  const queryState = useMemo(() => {
    const firstPage = infiniteData?.pages[0];
    const isLoading = infinite.isPending && !infiniteData?.pages?.length;
    let isError = false;
    let errorMessage: string | undefined;
    if (firstPage?.error) {
      isError = true;
      errorMessage = getErrorMessage(
        classifyError(firstPage, firstPage.status),
      );
    } else if (infinite.isError && infinite.error) {
      isError = true;
      errorMessage = getErrorMessage(classifyError(infinite.error, undefined));
    }
    return {
      isLoading,
      isError,
      errorMessage,
      isLoadingMore: infinite.isFetchingNextPage,
    };
  }, [
    infinite.isPending,
    infinite.isError,
    infinite.error,
    infinite.isFetchingNextPage,
    infiniteData?.pages,
  ]);

  return {
    exceptions,
    queryState,
    hasMore: !!infinite.hasNextPage,
    fetchNextPage: () => {
      void infinite.fetchNextPage();
    },
    isFetching: infinite.isFetching,
  };
}
