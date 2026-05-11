import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { CreateTenantParams, TenantResponse } from "./useCreateTenant.interface";

export const useCreateTenant = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.POST_CREATE_TENANT;

  return useMutation<ApiResponse<TenantResponse>, unknown, CreateTenantParams>({
    mutationFn: (params: CreateTenantParams) =>
      makeRequest<TenantResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(params),
        },
      }),
    onSuccess: (data) => {
      if (data?.data && !data?.error) {
        queryClient.invalidateQueries({ queryKey: ["internal-tenants"] });
      }
    },
  });
};
