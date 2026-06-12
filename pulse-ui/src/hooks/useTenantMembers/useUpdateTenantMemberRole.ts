import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { UpdateTenantMemberRoleParams, TenantMember } from "../../types/members";

export const useUpdateTenantMemberRole = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.UPDATE_TENANT_MEMBER_ROLE;
  const getRoute = API_ROUTES.GET_TENANT_MEMBERS;

  return useMutation<
    ApiResponse<TenantMember>,
    unknown,
    UpdateTenantMemberRoleParams
  >({
    mutationFn: (params: UpdateTenantMemberRoleParams) =>
      makeRequest<TenantMember>({
        url: `${API_BASE_URL}${route.apiPath
          .replace(":tenantId", params.tenantId)
          .replace(":userId", params.userId)}`,
        init: {
          method: route.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            newRole: params.newRole,
          }),
        },
      }),
    onSuccess: (data: ApiResponse<TenantMember>, variables: UpdateTenantMemberRoleParams) => {
      if (data?.data && !data?.error) {
        queryClient.invalidateQueries({
          queryKey: [getRoute.key, variables.tenantId],
        });
      }
    },
  });
};
