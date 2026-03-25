import React, {
  createContext,
  useContext,
  useState,
  useCallback,
  ReactNode,
  useEffect,
} from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import { useTenantContext } from "./TenantContext";
import { ProjectRole } from "../constants/Roles";
import {
  ProjectInfo,
  ProjectContextType,
  StoredProjectData,
} from "./ProjectContext.interface";
import { API_BASE_URL, API_ROUTES } from "../constants";
import { makeRequest } from "../helpers/makeRequest";
import { ProjectDetailsResponse } from "../hooks/useGetProject";

const ProjectContext = createContext<ProjectContextType | undefined>(undefined);

const STORAGE_KEY = "pulse_project_context";

export function ProjectProvider({ children }: { children: ReactNode }) {
  const [projectId, setProjectId] = useState<string | null>(null);
  const [projectName, setProjectName] = useState<string | null>(null);
  const [userRole, setUserRole] = useState<ProjectRole | null>(null);
  const [isActive, setIsActive] = useState(false);
  const [isEventFlowStarted, setIsEventFlowStarted] = useState(false);
  const [isInitializing, setIsInitializing] = useState(false);

  const { tenantId } = useTenantContext();
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();

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
          setIsEventFlowStarted(data.isEventFlowStarted);
        } else {
          sessionStorage.removeItem(STORAGE_KEY);
        }
      } catch (error) {
        sessionStorage.removeItem(STORAGE_KEY);
      }
    }
  }, []);

  // Persist to sessionStorage whenever state changes
  useEffect(() => {
    if (projectId && userRole) {
      const data: StoredProjectData = {
        projectId,
        projectName: projectName || "",
        userRole,
        isActive,
        isEventFlowStarted,
        timestamp: Date.now(),
      };
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data));    }
  }, [projectId, projectName, userRole, isActive, isEventFlowStarted]);

  const setProject = useCallback((project: ProjectInfo) => {
    setProjectId(project.projectId);
    setProjectName(project.projectName);
    setUserRole(project.userRole);
    setIsActive(project.isActive);
    setIsEventFlowStarted(project.isEventFlowStarted);

    // Store last used project ID for auto-selection on next login
    sessionStorage.setItem("pulse_last_project_id", project.projectId);

    // CRITICAL: Write to sessionStorage IMMEDIATELY (synchronously)
    // This ensures X-Project-ID header is available before any API calls fire
    // The useEffect at line 64 will also write, but this guarantees it's available immediately
    const data: StoredProjectData = {
      projectId: project.projectId,
      projectName: project.projectName || "",
      userRole: project.userRole,
      isActive: project.isActive,
      isEventFlowStarted: project.isEventFlowStarted,
      timestamp: Date.now(),
    };
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(data));  }, []);

  const clearProject = useCallback(() => {
    setProjectId(null);
    setProjectName(null);
    setUserRole(null);
    setIsActive(false);
    setIsEventFlowStarted(false);
    sessionStorage.removeItem(STORAGE_KEY);
  }, []);

  const navigateToProject = useCallback(
    async (targetProjectId: string) => {
      setIsInitializing(true);
      try {
        // Remove any cached data for this project to force fresh fetch
        queryClient.removeQueries({ queryKey: ["project", targetProjectId] });

        const response = await queryClient.fetchQuery({
          queryKey: ["project", targetProjectId],
          queryFn: async () =>
            makeRequest<ProjectDetailsResponse>({
              url: `${API_BASE_URL}${API_ROUTES.GET_PROJECT.apiPath.replace(":projectId", targetProjectId)}`,
              init: {
                method: API_ROUTES.GET_PROJECT.method,
              },
            }),
          // Force fresh fetch - don't use stale cache
          staleTime: 0,
        });

        const projectData = response?.data;
        if (!projectData) {
          if (tenantId) {
            navigate(`/${tenantId}/projects`);
          }
          return;
        }
        // Add 300ms delay to ensure initialization modal is visible
        await new Promise((resolve) => setTimeout(resolve, 300));

        // Set complete project info from API response
        setProject({
          projectId: projectData.projectId,
          projectName: projectData.name,
          userRole: projectData.userRole as ProjectRole,
          isActive: projectData.isActive,
          isEventFlowStarted: projectData.isEventFlowStarted,
          description: projectData.description,
        });

        // Invalidate all project-related queries to ensure fresh data
        queryClient.invalidateQueries({ queryKey: ["project"] });

        // Determine what route we're currently on and where we should go
        const currentPath = location.pathname;
        const exactDashboardRoute = `/projects/${targetProjectId}`;
        const isOnExactDashboard = currentPath === exactDashboardRoute;
        const isOnProjectSubRoute = currentPath.startsWith(
          `/projects/${targetProjectId}/`,
        );
        if (isOnExactDashboard) {
          // CASE 1: On exact dashboard route - redirect to onboarding if needed
          if (!projectData.isEventFlowStarted) {            navigate(`/projects/${targetProjectId}/onboarding`);
          } else {          }
        } else if (isOnProjectSubRoute) {
          // CASE 2: On a sub-route (deep link) - preserve it          // Don't navigate - just set context and stay on current route
        } else {
          // CASE 3: NOT on a project route at all (e.g., projects listing page, other pages)
          // Navigate to appropriate destination
          if (!projectData.isEventFlowStarted) {            navigate(`/projects/${targetProjectId}/onboarding`);
          } else {            navigate(`/projects/${targetProjectId}`);
          }
        }
      } catch (err) {
        // Don't redirect on CancelledError - this happens when React Query cancels a pending request
        // because a newer request for the same data is made (e.g., from duplicate useEffect calls)
        const isCancelled =
          err instanceof Error && err.message === "CancelledError";
        if (isCancelled) {          return;
        }

        if (tenantId) {
          navigate(`/${tenantId}/projects`);
        }
      } finally {
        setIsInitializing(false);
      }
    },
    [queryClient, setProject, navigate, location.pathname, tenantId],
  );

  const value: ProjectContextType = {
    projectId,
    projectName,
    userRole,
    isActive,
    isEventFlowStarted,
    isInitializing,
    setProject,
    navigateToProject,
    clearProject,
  };

  return (
    <ProjectContext.Provider value={value}>{children}</ProjectContext.Provider>
  );
}

export function useProjectContext(): ProjectContextType {
  const context = useContext(ProjectContext);
  if (context === undefined) {
    throw new Error("useProjectContext must be used within a ProjectProvider");
  }
  return context;
}
