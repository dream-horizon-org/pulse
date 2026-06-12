import { UseQueryResult } from "@tanstack/react-query";
import { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";
import { DataQueryResponse } from "./useGetDataQuery.interface";

export type DataQueryResult = Pick<
  UseQueryResult<ApiResponse<DataQueryResponse>>,
  "data" | "error" | "isLoading" | "isFetching"
>;

export interface DataQueryStatus {
  loading: boolean;
  failed: boolean;
}

export function getDataQueryStatus(query: DataQueryResult): DataQueryStatus {
  const failed = !!query.data?.error || !!query.error;
  const loading = (query.isLoading || query.isFetching) && !failed;

  return { loading, failed };
}
