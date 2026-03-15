import { ProjectRole } from "../constants/Roles";

export interface ProjectInfo {
  projectId: string;
  projectName: string;
  userRole: ProjectRole;
  isActive: boolean;
  isEventFlowStarted: boolean;
  description?: string;
}

export interface ProjectContextType {
  // State
  projectId: string | null;
  projectName: string | null;
  userRole: ProjectRole | null;
  isActive: boolean;
  isEventFlowStarted: boolean;
  isInitializing: boolean;

  // Methods
  setProject: (project: ProjectInfo) => void;
  navigateToProject: (projectId: string) => Promise<void>;
  clearProject: () => void;
}

export interface StoredProjectData {
  projectId: string;
  projectName: string;
  userRole: ProjectRole;
  isActive: boolean;
  isEventFlowStarted: boolean;
  description?: string;
  timestamp: number;
}
