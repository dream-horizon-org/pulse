import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { RemoveTenantMemberParams } from "../../types/members";

export const useRemoveTenantMember = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.REMOVE_TENANT_MEMBER;
  const getRoute = API_ROUTES.GET_TENANT_MEMBERS;

  return useMutation<ApiResponse<void>, unknown, RemoveTenantMemberParams>({
    mutationFn: (params: RemoveTenantMemberParams) =>
      makeRequest<void>({
        url: `${API_BASE_URL}${route.apiPath
          .replace(":tenantId", params.tenantId)
          .replace(":userId", params.userId)}`,
        init: {
          method: route.method,
        },
      }),
    onSuccess: (data: ApiResponse<void>, variables: RemoveTenantMemberParams) => {
      if (data?.data !== undefined && !data?.error) {
        queryClient.invalidateQueries({
          queryKey: [getRoute.key, variables.tenantId],
        });
      }
    },
  });
};
