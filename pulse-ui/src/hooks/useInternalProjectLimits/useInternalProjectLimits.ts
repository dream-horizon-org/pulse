import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";
import { API_BASE_URL, API_METHODS } from "../../constants";
import type { ProjectUsageLimitDto, ProjectLimitHistoryDto, SetLimitsPayload, ResetLimitsPayload } from "./useInternalProjectLimits.interface";

function throwIfApiFailed(res: ApiResponse<unknown>): void {
  if (res.error) {
    const err = new Error(res.error.message) as Error & { status: number };
    err.status = res.status;
    throw err;
  }
  if (res.status < 200 || res.status >= 300) {
    const err = new Error(`Request failed (${res.status})`) as Error & { status: number };
    err.status = res.status || 500;
    throw err;
  }
}

export const projectLimitsKey = (projectId: string) => ["internal-project-limits", projectId] as const;
export const projectLimitHistoryKey = (projectId: string) => ["internal-project-limit-history", projectId] as const;

export const useProjectLimits = (projectId: string) =>
  useQuery<ProjectUsageLimitDto>({
    queryKey: projectLimitsKey(projectId),
    queryFn: async () => {
      const res = await makeRequest<ProjectUsageLimitDto>({
        url: `${API_BASE_URL}/internal/v1/projects/${encodeURIComponent(projectId)}/limits`,
        init: { method: API_METHODS.GET },
      });
      if (res.error) throw new Error(res.error.message);
      return res.data!;
    },
    enabled: !!projectId.trim(),
    staleTime: 30_000,
    retry: false,
  });

export const useSetProjectLimits = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ projectId, payload }: { projectId: string; payload: SetLimitsPayload }) => {
      const res = await makeRequest<ProjectUsageLimitDto>({
        url: `${API_BASE_URL}/internal/v1/projects/${encodeURIComponent(projectId)}/limits`,
        init: {
          method: API_METHODS.PUT,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        },
      });
      throwIfApiFailed(res);
      return res.data!;
    },
    onSuccess: (_data, variables) => qc.invalidateQueries({ queryKey: projectLimitsKey(variables.projectId) }),
  });
};

export const useResetProjectLimits = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ projectId, payload }: { projectId: string; payload: ResetLimitsPayload }) => {
      const res = await makeRequest<ProjectUsageLimitDto>({
        url: `${API_BASE_URL}/internal/v1/projects/${encodeURIComponent(projectId)}/limits/reset`,
        init: {
          method: API_METHODS.POST,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        },
      });
      throwIfApiFailed(res);
      return res.data!;
    },
    onSuccess: (_data, variables) => {
      qc.invalidateQueries({ queryKey: projectLimitsKey(variables.projectId) });
      qc.invalidateQueries({ queryKey: projectLimitHistoryKey(variables.projectId) });
    },
  });
};

export const useProjectLimitHistory = (projectId: string) =>
  useQuery<ProjectLimitHistoryDto>({
    queryKey: projectLimitHistoryKey(projectId),
    queryFn: async () => {
      const res = await makeRequest<ProjectLimitHistoryDto>({
        url: `${API_BASE_URL}/internal/v1/projects/${encodeURIComponent(projectId)}/limits/history`,
        init: { method: API_METHODS.GET },
      });
      if (res.error) throw new Error(res.error.message);
      return res.data!;
    },
    enabled: !!projectId.trim(),
    staleTime: 30_000,
    retry: false,
  });
