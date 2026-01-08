import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL } from "../../constants";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import {
  GetJobStatusResponse,
  QueryErrorResponse,
} from "./useSubmitQuery.interface";

interface UseGetQueryJobStatusOptions {
  jobId: string;
  maxResults?: number;
  nextToken?: string | null;
  enabled?: boolean;
  refetchInterval?: number | false;
}

/**
 * Hook to get the status of a query job
 */
export const useGetQueryJobStatus = ({
  jobId,
  maxResults = 1000,
  nextToken,
  enabled = true,
  refetchInterval = false,
}: UseGetQueryJobStatusOptions) => {
  const params = new URLSearchParams();
  params.append("maxResults", maxResults.toString());
  if (nextToken) {
    params.append("nextToken", nextToken);
  }

  return useQuery<ApiResponse<GetJobStatusResponse>, QueryErrorResponse>({
    queryKey: ["QUERY_JOB_STATUS", jobId, maxResults, nextToken],
    queryFn: () =>
      makeRequest<GetJobStatusResponse>({
        url: `${API_BASE_URL}/query/job/${jobId}?${params.toString()}`,
        init: {
          method: "GET",
        },
      }),
    enabled: enabled && !!jobId,
    refetchInterval,
    refetchOnWindowFocus: false,
  });
};

