export interface CreateTenantParams {
  name: string;
  description?: string;
}

export interface TenantResponse {
  tenantId: string;
  name: string;
  description?: string;
  isActive?: boolean;
}
