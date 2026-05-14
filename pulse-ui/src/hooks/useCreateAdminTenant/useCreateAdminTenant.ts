import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";

export interface CreateAdminTenantParams {
  tenantName: string;
  projectName: string;
  description?: string;
}

export interface CreateAdminTenantResponse {
  tenantId: string;
  projectId: string;
  apiKey: string;
}

export const useCreateAdminTenant = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.POST_ADMIN_CREATE_TENANT;

  return useMutation<ApiResponse<CreateAdminTenantResponse>, unknown, CreateAdminTenantParams>({
    mutationFn: (params: CreateAdminTenantParams) =>
      makeRequest<CreateAdminTenantResponse>({
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
