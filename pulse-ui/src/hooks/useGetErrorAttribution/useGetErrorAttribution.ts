import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { GET_ERROR_ATTRIBUTION_ROUTE } from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import type {
  ErrorAttributionRequestContext,
  ErrorAttributionResponse,
  UseGetErrorAttributionParams,
} from "./useGetErrorAttribution.interface";

export function errorAttributionQueryKey(
  interactionName: string,
  start: string,
  end: string,
  projectId: string,
) {
  return [
    GET_ERROR_ATTRIBUTION_ROUTE.key,
    interactionName,
    start,
    end,
    projectId,
  ] as const;
}

function buildAttributionUrl(
  interactionName: string,
  start: string,
  end: string,
  refresh: boolean,
): string {
  const apiBaseUrl = getApiBaseUrl();
  const path = GET_ERROR_ATTRIBUTION_ROUTE.getPath(interactionName);
  const params = new URLSearchParams({
    start,
    end,
  });
  if (refresh) {
    params.set("refresh", "true");
  }
  return `${apiBaseUrl}${path}?${params.toString()}`;
}

function projectHeaders(projectId: string): Record<string, string> {
  const trimmed = String(projectId).trim();
  if (trimmed === "") return {};
  return { "X-Project-ID": trimmed };
}

export function useGetErrorAttribution({
  interactionName,
  start,
  end,
  projectId,
  enabled = true,
}: UseGetErrorAttributionParams) {
  const trimmedProjectId = projectId != null ? String(projectId).trim() : "";

  return useQuery({
    queryKey: errorAttributionQueryKey(
      interactionName ?? "",
      start,
      end,
      trimmedProjectId,
    ),
    queryFn: async ({
      signal,
    }): Promise<ApiResponse<ErrorAttributionResponse>> => {
      if (!interactionName || trimmedProjectId === "") {
        return {
          data: null,
          error: {
            code: "400",
            message: "Interaction name and project are required",
            cause: "",
          },
          status: 400,
        };
      }
      const url = buildAttributionUrl(interactionName, start, end, false);
      return makeRequest<ErrorAttributionResponse>({
        url,
        init: {
          method: "GET",
          signal,
          headers: projectHeaders(trimmedProjectId),
        },
      });
    },
    enabled:
      enabled &&
      !!interactionName &&
      trimmedProjectId !== "" &&
      !!start &&
      !!end,
    retry: false,
  });
}

export function useRefreshErrorAttribution() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      interactionName,
      start,
      end,
      projectId,
    }: ErrorAttributionRequestContext) => {
      const trimmedProjectId = String(projectId).trim();
      const url = buildAttributionUrl(interactionName, start, end, true);
      return makeRequest<ErrorAttributionResponse>({
        url,
        init: {
          method: "GET",
          headers: projectHeaders(trimmedProjectId),
        },
      });
    },
    onSuccess: (_res, variables) => {
      queryClient.invalidateQueries({
        queryKey: errorAttributionQueryKey(
          variables.interactionName,
          variables.start,
          variables.end,
          variables.projectId,
        ),
      });
    },
  });
}
