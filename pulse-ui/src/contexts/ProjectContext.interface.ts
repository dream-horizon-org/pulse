import { ProjectRole } from '../constants/Roles';
import { TierType } from '../constants/Tiers';

export interface ProjectInfo {
  projectId: string;
  projectName: string;
  userRole: ProjectRole;
  isActive?: boolean;
  /** @deprecated Tier is now tenant-level. Use TenantContext.tier instead. This field is ignored. */
  plan?: TierType;
}

export interface ProjectContextType {
  // State
  projectId: string | null;
  projectName: string | null;
  userRole: ProjectRole | null;
  /** @deprecated Tier is now tenant-level. Use TenantContext.tier instead. Always returns 'free' for backward compatibility. */
  plan: TierType | null;
  isActive: boolean;
  
  // Methods
  setProject: (project: ProjectInfo) => void;
  switchProject: (projectId: string) => Promise<void>;
  clearProject: () => void;
}

export interface StoredProjectData {
  projectId: string;
  projectName: string;
  userRole: ProjectRole;
  isActive: boolean;
  plan?: TierType;
  timestamp: number;
}
