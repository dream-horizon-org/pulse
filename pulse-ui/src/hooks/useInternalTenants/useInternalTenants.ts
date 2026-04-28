import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { InternalTenant } from "./useInternalTenants.interface";
import { TENANT_ROLES, TenantRole } from "../../constants/Roles";

type TenantListRestRow = {
  tenantId: string;
  name: string;
  tier?: string;
  tenantRole?: string;
};

type TenantListRestEnvelope = {
  tenants: TenantListRestRow[];
  totalCount?: number;
};

export const useInternalTenants = () =>
  useQuery<InternalTenant[]>({
    queryKey: ["internal-tenants"],
    queryFn: async () => {
      const response = await makeRequest<TenantListRestEnvelope>({
        url: `${API_BASE_URL}${API_ROUTES.INTERNAL_TENANTS.apiPath}?activeOnly=true`,
        init: {
          method: API_ROUTES.INTERNAL_TENANTS.method,
        },
      });
      if (response.error) {
        throw new Error(response.error.message);
      }
      const rows = response.data?.tenants ?? [];
      return rows.map((t) => ({
        tenantId: t.tenantId,
        tenantName: t.name,
        tier: t.tier,
        userRole:
          t.tenantRole === TENANT_ROLES.ADMIN ||
          t.tenantRole === TENANT_ROLES.MEMBER
            ? (t.tenantRole as TenantRole)
            : undefined,
      }));
    },
    staleTime: 60_000,
    retry: false,
  });
