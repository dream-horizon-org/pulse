import { UseQueryResult } from "@tanstack/react-query";
import { ErrorInfo } from "../../utils/errorHandling";
import { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";

export interface UseQueryErrorParams<T> {
  queryResult: UseQueryResult<ApiResponse<T>, unknown>;
}

export interface QueryState {
  isLoading: boolean;
  isError: boolean;
  errorInfo: ErrorInfo | null;
  errorMessage: string;
  hasData: boolean;
}
