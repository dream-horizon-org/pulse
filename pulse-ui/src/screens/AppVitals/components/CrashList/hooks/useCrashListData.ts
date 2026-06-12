import { useExceptionListData } from "../../ExceptionTable/hooks";
import type { CrashIssue } from "../../../AppVitals.interface";

interface UseCrashListDataParams {
  startTime: string;
  endTime: string;
  appVersion?: string;
  osVersion?: string;
  device?: string;
  screenName?: string;
}

export function useCrashListData(params: UseCrashListDataParams) {
  const { exceptions, queryState } = useExceptionListData({
    ...params,
    exceptionType: "crash",
  });

  return {
    crashes: exceptions as CrashIssue[],
    queryState,
  };
}
