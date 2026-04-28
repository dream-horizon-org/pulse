import { TenantRole } from "../../constants/Roles";

export interface InternalTenant {
  tenantId: string;
  tenantName: string;
  tier?: string;
  userRole?: TenantRole;
}
