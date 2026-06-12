import { useExceptionListData } from "../../ExceptionTable/hooks";
import type { NonFatalIssue } from "../../../AppVitals.interface";

interface UseNonFatalListDataParams {
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  screenName?: string;
}

export function useNonFatalListData(params: UseNonFatalListDataParams) {
  const { exceptions, queryState } = useExceptionListData({
    ...params,
    exceptionType: "nonfatal",
  });

  return {
    nonFatals: exceptions as NonFatalIssue[],
    queryState,
  };
}
