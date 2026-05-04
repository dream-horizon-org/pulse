import { TenantRole } from "../../constants/Roles";

export interface InternalTenant {
  tenantId: string;
  tenantName: string;
  tier?: string;
  userRole?: TenantRole;
}

export type TenantListRestRow = {
  tenantId: string;
  name: string;
  tier?: string;
  tenantRole?: string;
};

export type TenantListRestEnvelope = {
  tenants: TenantListRestRow[];
  totalCount?: number;
};
