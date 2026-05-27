import { useMutation, useQueryClient } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import type { ApiResponse } from "../../helpers/makeRequest/makeRequest.interface";
import { API_BASE_URL, API_METHODS } from "../../constants";

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

export const useUpdateTenantTier = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ tenantId, tierId }: { tenantId: string; tierId: number }) => {
      const res = await makeRequest({
        url: `${API_BASE_URL}/internal/v1/tenants/${encodeURIComponent(tenantId)}/tier`,
        init: {
          method: API_METHODS.PUT,
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ tierId }),
        },
      });
      throwIfApiFailed(res);
      return res;
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["internal-tenants"] });
    },
  });
};
