import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { TenantMemberListResponse } from "../../types/members";

export const useTenantMembers = (tenantId: string) => {
  const route = API_ROUTES.GET_TENANT_MEMBERS;
  
  return useQuery({
    queryKey: [route.key, tenantId],
    queryFn: () =>
      makeRequest<TenantMemberListResponse>({
        url: `${API_BASE_URL}${route.apiPath.replace(":tenantId", tenantId)}`,
        init: {
          method: route.method,
        },
      }),
    enabled: !!tenantId,
    refetchOnWindowFocus: false,
  });
};
