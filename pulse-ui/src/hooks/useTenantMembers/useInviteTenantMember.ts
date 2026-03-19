import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { InviteTenantMemberParams, TenantMember } from "../../types/members";

export const useInviteTenantMember = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.INVITE_TENANT_MEMBER;
  const getRoute = API_ROUTES.GET_TENANT_MEMBERS;

  return useMutation<
    ApiResponse<TenantMember>,
    unknown,
    InviteTenantMemberParams
  >({
    mutationFn: (params: InviteTenantMemberParams) =>
      makeRequest<TenantMember>({
        url: `${API_BASE_URL}${route.apiPath.replace(":tenantId", params.tenantId)}`,
        init: {
          method: route.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            email: params.email,
            role: params.role,
          }),
        },
      }),
    onSuccess: (data: ApiResponse<TenantMember>, variables: InviteTenantMemberParams) => {
      if (data?.data && !data?.error) {
        queryClient.invalidateQueries({
          queryKey: [getRoute.key, variables.tenantId],
        });
      }
    },
  });
};
