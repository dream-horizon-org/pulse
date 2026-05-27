import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";
import { API_BASE_URL, API_METHODS, API_ROUTES } from "../../constants";
import type { TierRestResponse, TierListEnvelope, CreateTierPayload, UpdateTierPayload } from "./useInternalTiers.interface";

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

export const TIERS_QUERY_KEY = ["internal-tiers"] as const;

export const useInternalTiers = () =>
  useQuery<TierRestResponse[]>({
    queryKey: TIERS_QUERY_KEY,
    queryFn: async () => {
      const res = await makeRequest<TierListEnvelope>({
        url: `${API_BASE_URL}${API_ROUTES.INTERNAL_GET_TIERS.apiPath}`,
        init: { method: API_METHODS.GET },
      });
      if (res.error) throw new Error(res.error.message);
      return res.data?.tiers ?? [];
    },
    staleTime: 60_000,
    retry: false,
  });

export const useCreateTier = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (payload: CreateTierPayload) => {
      const res = await makeRequest<TierRestResponse>({
        url: `${API_BASE_URL}${API_ROUTES.INTERNAL_CREATE_TIER.apiPath}`,
        init: {
          method: API_METHODS.POST,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        },
      });
      throwIfApiFailed(res);
      return res.data!;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: TIERS_QUERY_KEY }),
  });
};

export const useUpdateTier = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ tierId, payload }: { tierId: number; payload: UpdateTierPayload }) => {
      const res = await makeRequest<TierRestResponse>({
        url: `${API_BASE_URL}/internal/v1/tiers/${tierId}`,
        init: {
          method: API_METHODS.PUT,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(payload),
        },
      });
      throwIfApiFailed(res);
      return res.data!;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: TIERS_QUERY_KEY }),
  });
};

export const useDeactivateTier = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (tierId: number) => {
      const res = await makeRequest({
        url: `${API_BASE_URL}/internal/v1/tiers/${tierId}/deactivate`,
        init: { method: API_METHODS.PUT },
      });
      throwIfApiFailed(res);
      return res;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: TIERS_QUERY_KEY }),
  });
};

export const useActivateTier = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (tierId: number) => {
      const res = await makeRequest({
        url: `${API_BASE_URL}/internal/v1/tiers/${tierId}/activate`,
        init: { method: API_METHODS.PUT },
      });
      throwIfApiFailed(res);
      return res;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: TIERS_QUERY_KEY }),
  });
};
