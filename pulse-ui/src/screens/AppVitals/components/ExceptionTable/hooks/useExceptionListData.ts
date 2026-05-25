import { useMemo } from "react";
import { useGetDataQuery } from "../../../../../hooks";
import { useQueryError } from "../../../../../hooks/useQueryError";
import type { DataQueryResponse } from "../../../../../hooks/useGetDataQuery/useGetDataQuery.interface";
import { useExceptionTimestamps } from "./useExceptionTimestamps";
import {
  buildExceptionListRequestBody,
  extractGroupIdsFromResponse,
  getEventNameForTimestamps,
  mapExceptionRowsToIssues,
  type ExceptionListFilterParams,
  type ExceptionIssue,
  type ExceptionType,
} from "./exceptionListShared";

export type { ExceptionType };

interface UseExceptionListDataParams extends ExceptionListFilterParams {}

export function useExceptionListData(params: UseExceptionListDataParams) {
  const {
    appVersion = "all",
    osVersion = "all",
    device = "all",
    screenName,
    exceptionType,
  } = params;

  const queryResult = useGetDataQuery({
    requestBody: buildExceptionListRequestBody(params, 0, 10),
    enabled: !!params.startTime && !!params.endTime,
  });

  const { data } = queryResult;
  const queryState = useQueryError<DataQueryResponse>({ queryResult });

  const groupIds = useMemo(
    () => extractGroupIdsFromResponse(data?.data),
    [data],
  );

  const { timestampsMap } = useExceptionTimestamps({
    groupIds,
    appVersion,
    osVersion,
    device,
    screenName,
    eventName: getEventNameForTimestamps(exceptionType),
  });

  const exceptions: ExceptionIssue[] = useMemo(() => {
    const responseData = data?.data;
    if (!responseData?.rows?.length || !responseData.fields?.length) {
      return [];
    }
    return mapExceptionRowsToIssues(
      responseData.rows,
      responseData.fields,
      exceptionType,
      timestampsMap,
    );
  }, [data, timestampsMap, exceptionType]);

  return {
    exceptions,
    queryState,
  };
}
