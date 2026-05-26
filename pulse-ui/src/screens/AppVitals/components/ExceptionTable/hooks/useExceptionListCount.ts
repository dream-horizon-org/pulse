import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../../../../constants";
import { makeRequest } from "../../../../../helpers/makeRequest";
import type { DataQueryResponse } from "../../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import { useProjectQueryEnabled } from "../../../../../hooks/useProjectQueryEnabled";
import {
  buildExceptionCountRequestBody,
  tryFormatTimeToIso,
  withIsoTimeRange,
  type ExceptionListFilterParams,
} from "./exceptionListShared";

export function useExceptionListCount(params: ExceptionListFilterParams) {
  const { startTime, endTime } = params;
  const formattedStart = tryFormatTimeToIso(startTime);
  const formattedEnd = tryFormatTimeToIso(endTime);
  const hasValidTimeRange = formattedStart != null && formattedEnd != null;
  const isProjectReady = useProjectQueryEnabled(
    !!startTime && !!endTime && hasValidTimeRange,
  );

  const filterKey = JSON.stringify({
    startTime,
    endTime,
    appVersion: params.appVersion ?? "all",
    osVersion: params.osVersion ?? "all",
    device: params.device ?? "all",
    platform: params.platform ?? "all",
    networkProvider: params.networkProvider ?? "all",
    state: params.state ?? "all",
    screenName: params.screenName,
    exceptionType: params.exceptionType,
    searchQuery: params.searchQuery?.trim() ?? "",
  });

  const query = useQuery({
    queryKey: ["EXCEPTION_LIST_COUNT", filterKey],
    enabled: isProjectReady,
    staleTime: 10000,
    queryFn: async () => {
      const body = withIsoTimeRange(buildExceptionCountRequestBody(params));
      return makeRequest<DataQueryResponse>({
        url: `${API_BASE_URL}${API_ROUTES.DATA_QUERY.apiPath}`,
        init: {
          method: API_ROUTES.DATA_QUERY.method,
          body: JSON.stringify(body),
        },
      });
    },
  });

  const count = useMemo(() => {
    const responseData = query.data?.data;
    if (!responseData?.rows?.length || !responseData.fields?.length) {
      return 0;
    }
    const idx = responseData.fields.indexOf("issue_count");
    if (idx < 0) return 0;
    return Math.round(parseFloat(responseData.rows[0][idx]) || 0);
  }, [query.data]);

  return { count, isLoading: query.isPending };
}
