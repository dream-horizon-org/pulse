import { useMemo } from "react";
import {
  classifyError,
  getErrorMessage,
} from "../../utils/errorHandling";
import { UseQueryErrorParams, QueryState } from "./useQueryError.interface";

/**
 * Hook to extract and classify error states from React Query results
 */
export function useQueryError<T>({
  queryResult,
}: UseQueryErrorParams<T>): QueryState {
  const { data, error, isLoading, isError } = queryResult;

  const errorInfo = useMemo(() => {
    if (!isError || !error) return null;

    // Check if error is in the data response
    if (data?.error) {
      return classifyError(data, data.status);
    }

    // Check React Query error
    return classifyError(error, data?.status);
  }, [isError, error, data]);

  const errorMessage = useMemo(() => {
    if (!errorInfo) return "";
    return getErrorMessage(errorInfo);
  }, [errorInfo]);

  const hasData = useMemo(() => {
    return !!data?.data && !data?.error;
  }, [data]);

  return {
    isLoading,
    isError: isError || !!data?.error,
    errorInfo,
    errorMessage,
    hasData,
  };
}
