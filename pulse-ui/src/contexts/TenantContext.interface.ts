import { ProjectSummary } from '../helpers/getUserProjects/getUserProjects.interface';
import { TenantRole } from '../constants/Roles';
import { TierType } from '../constants/Tiers';

export interface TenantInfo {
  tenantId: string;
  tenantName: string;
  userRole: TenantRole;
  tier: TierType;
}

export interface TenantContextType {
  // State
  tenantId: string | null;
  tenantName: string | null;
  userRole: TenantRole | null;
  tier: TierType | null;
  projects: ProjectSummary[];
  isLoading: boolean;
  hasLoadedProjects: boolean;
  
  // Methods
  setTenantInfo: (tenant: TenantInfo) => void;
  refreshProjects: () => Promise<void>;
  addProject: (project: ProjectSummary) => void;
  clearTenant: () => void;
}

export interface StoredTenantData {
  tenantId: string;
  tenantName: string;
  userRole: TenantRole;
  tier: TierType;
  projects: ProjectSummary[];
  timestamp: number;
}
