import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { GET_ERROR_ATTRIBUTION_ROUTE } from "../../constants/API";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest";
import { getApiBaseUrl } from "../../utils";
import type {
  ErrorAttributionRequestContext,
  ErrorAttributionResponse,
  ErrorAttributionSignal,
  UseGetErrorAttributionParams,
} from "./useGetErrorAttribution.interface";

export function errorAttributionQueryKey(
  interactionName: string,
  start: string,
  end: string,
  projectId: string,
  drillDownKey: string,
) {
  return [
    GET_ERROR_ATTRIBUTION_ROUTE.key,
    interactionName,
    start,
    end,
    projectId,
    drillDownKey,
  ] as const;
}

function drillDownKeyFromSignals(
  signals: ErrorAttributionSignal[] | null | undefined,
): string {
  if (signals == null || signals.length === 0) return "__none__";
  return [...signals].sort().join(",");
}

function buildAttributionUrl(
  interactionName: string,
  start: string,
  end: string,
  refresh: boolean,
  drillDownSignals: ErrorAttributionSignal[] | null | undefined,
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
  if (drillDownSignals != null && drillDownSignals.length > 0) {
    params.set("drillDown", drillDownSignals.join(","));
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
  drillDownSignals,
  enabled = true,
}: UseGetErrorAttributionParams) {
  const trimmedProjectId = projectId != null ? String(projectId).trim() : "";
  const ddKey = drillDownKeyFromSignals(drillDownSignals ?? null);

  return useQuery({
    queryKey: errorAttributionQueryKey(
      interactionName ?? "",
      start,
      end,
      trimmedProjectId,
      ddKey,
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
      const url = buildAttributionUrl(
        interactionName,
        start,
        end,
        false,
        drillDownSignals,
      );
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
      drillDownSignals,
    }: ErrorAttributionRequestContext) => {
      const trimmedProjectId = String(projectId).trim();
      const url = buildAttributionUrl(
        interactionName,
        start,
        end,
        true,
        drillDownSignals,
      );
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
          drillDownKeyFromSignals(variables.drillDownSignals ?? null),
        ),
      });
    },
  });
}
