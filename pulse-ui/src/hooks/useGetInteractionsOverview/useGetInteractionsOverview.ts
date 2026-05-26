import { useMutation } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import { POST_INTERACTIONS_OVERVIEW_ROUTE } from "../../constants/API";

export interface InteractionsOverviewRequest {
  regenerate?: boolean;
}

export interface InteractionsOverviewResponse {
  summary: string;
  cached: boolean;
  cachedAt: string | null;
}

export function useGetInteractionsOverview() {
  return useMutation({
    mutationFn: async (
      params: InteractionsOverviewRequest,
    ): Promise<ApiResponse<InteractionsOverviewResponse>> => {
      const url = `${getApiBaseUrl()}${POST_INTERACTIONS_OVERVIEW_ROUTE.apiPath}`;
      return makeRequest<InteractionsOverviewResponse>({
        url,
        init: {
          method: POST_INTERACTIONS_OVERVIEW_ROUTE.method,
          body: JSON.stringify({ regenerate: params.regenerate ?? false }),
        },
        unwrapped: true,
      });
    },
  });
}
