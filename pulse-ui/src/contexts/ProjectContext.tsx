import React, { createContext, useContext, useState, useCallback, ReactNode, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTenantContext } from './TenantContext';

interface ProjectInfo {
  projectId: string;
  projectName: string;
  userRole: 'admin' | 'editor' | 'viewer';
  isActive?: boolean;
  plan?: 'free' | 'enterprise';  // @deprecated - Tier is now tenant-level, use TenantContext.tier instead
}

interface ProjectContextType {
  // State
  projectId: string | null;
  projectName: string | null;
  userRole: 'admin' | 'editor' | 'viewer' | null;
  plan: 'free' | 'enterprise' | null;  // @deprecated - Tier is now tenant-level, use TenantContext.tier instead
  isActive: boolean;
  
  // Methods
  setProject: (project: ProjectInfo) => void;
  switchProject: (projectId: string) => Promise<void>;
  clearProject: () => void;
}

const ProjectContext = createContext<ProjectContextType | undefined>(undefined);

const STORAGE_KEY = 'pulse_project_context';

interface StoredProjectData {
  projectId: string;
  projectName: string;
  userRole: 'admin' | 'editor' | 'viewer';
  isActive: boolean;
  plan?: 'free' | 'enterprise';
  timestamp: number;
}

export function ProjectProvider({ children }: { children: ReactNode }) {
  const [projectId, setProjectId] = useState<string | null>(null);
  const [projectName, setProjectName] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<'admin' | 'editor' | 'viewer' | null>(null);
  const [plan, setPlan] = useState<'free' | 'enterprise' | null>(null);
  const [isActive, setIsActive] = useState(false);
  
  const { projects } = useTenantContext();
  const navigate = useNavigate();

  // Hydrate from sessionStorage on mount
  useEffect(() => {
    const stored = sessionStorage.getItem(STORAGE_KEY);
    if (stored) {
      try {
        const data: StoredProjectData = JSON.parse(stored);
        // Check if data is less than 1 hour old
        const ONE_HOUR = 60 * 60 * 1000;
        if (Date.now() - data.timestamp < ONE_HOUR) {
          setProjectId(data.projectId);
          setProjectName(data.projectName);
          setUserRole(data.userRole);
          setIsActive(data.isActive);
          setPlan(data.plan || 'free');
          console.log('[ProjectContext] Hydrated from sessionStorage:', data.projectId);
        } else {
          sessionStorage.removeItem(STORAGE_KEY);
        }
      } catch (error) {
        console.error('[ProjectContext] Failed to parse stored data:', error);
        sessionStorage.removeItem(STORAGE_KEY);
      }
    }
  }, []);

  // Persist to sessionStorage whenever state changes
  useEffect(() => {
    if (projectId && userRole) {
      const data: StoredProjectData = {
        projectId,
        projectName: projectName || '',
        userRole,
        isActive,
        plan: plan || 'free',
        timestamp: Date.now(),
      };
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    }
  }, [projectId, projectName, userRole, isActive, plan]);

  const setProject = useCallback((project: ProjectInfo) => {
    console.log('[ProjectContext] Setting project:', project.projectId);
    setProjectId(project.projectId);
    setProjectName(project.projectName);
    setUserRole(project.userRole);
    setIsActive(project.isActive ?? true);
    setPlan(project.plan || 'free');
    
    // Store last used project ID for auto-selection on next login
    sessionStorage.setItem('pulse_last_project_id', project.projectId);
  }, []);

  const switchProject = useCallback(async (newProjectId: string) => {
    console.log('[ProjectContext] Switching to project:', newProjectId);
    
    // Find project in tenant's project list
    const project = projects.find(p => p.projectId === newProjectId);
    
    if (!project) {
      console.error('[ProjectContext] Project not found:', newProjectId);
      // Navigate to project selection if project not found
      navigate('/project-selection');
      return;
    }

    // Update project context
    setProject({
      projectId: project.projectId,
      projectName: project.name,
      userRole: project.role as 'admin' | 'editor' | 'viewer',
      isActive: project.isActive,
      plan: 'free', // TODO: Get from project details API
    });

    // Navigate to project dashboard
    navigate(`/projects/${project.projectId}`);
  }, [projects, navigate, setProject]);

  const clearProject = useCallback(() => {
    console.log('[ProjectContext] Clearing project context');
    setProjectId(null);
    setProjectName(null);
    setUserRole(null);
    setPlan(null);
    setIsActive(false);
    sessionStorage.removeItem(STORAGE_KEY);
  }, []);

  const value: ProjectContextType = {
    projectId,
    projectName,
    userRole,
    plan,
    isActive,
    setProject,
    switchProject,
    clearProject,
  };

  return (
    <ProjectContext.Provider value={value}>
      {children}
    </ProjectContext.Provider>
  );
}

export function useProjectContext(): ProjectContextType {
  const context = useContext(ProjectContext);
  if (context === undefined) {
    throw new Error('useProjectContext must be used within a ProjectProvider');
  }
  return context;
}
