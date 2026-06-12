import { useExceptionListData } from "../../ExceptionTable/hooks";
import type { ANRIssue } from "../../../AppVitals.interface";

interface UseANRListDataParams {
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  screenName?: string;
}

export function useANRListData(params: UseANRListDataParams) {
  const { exceptions, queryState } = useExceptionListData({
    ...params,
    exceptionType: "anr",
  });

  return {
    anrs: exceptions as ANRIssue[],
    queryState,
  };
}
