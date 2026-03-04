import { useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useProjectContext, useTenantContext } from '../../contexts';
import { ROUTES } from '../../constants';

interface ProjectGuardProps {
  children: React.ReactNode;
}

/**
 * Guard component that ensures a project is selected before accessing protected routes
 * Also syncs project context from URL to context
 */
export function ProjectGuard({ children }: ProjectGuardProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const { projectId: contextProjectId, switchProject } = useProjectContext();
  const { projects } = useTenantContext();

  useEffect(() => {
    const excludedPaths = [
      ROUTES.LOGIN.basePath,
      ROUTES.ONBOARDING.basePath,
      ROUTES.PRICING.basePath,
      ROUTES.PROJECT_SELECTION.basePath,
      '/organization',
    ];

    const isExcludedPath = excludedPaths.some(path => 
      location.pathname.startsWith(path)
    );

    // Extract project ID from URL if on project-scoped route
    const projectIdMatch = location.pathname.match(/^\/projects\/([^/]+)/);
    const urlProjectId = projectIdMatch ? projectIdMatch[1] : null;

    // If we're on a project-scoped route
    if (urlProjectId && !isExcludedPath) {
      // Sync URL project ID with context if different
      if (!contextProjectId || contextProjectId !== urlProjectId) {
        console.log(`[ProjectGuard] Syncing project context from URL: ${urlProjectId}`);
        
        // Check if user has access to this project
        const hasAccess = projects.some(p => p.projectId === urlProjectId);
        if (hasAccess) {
          switchProject(urlProjectId);
        } else if (projects.length > 0) {
          // Project not found or no access - redirect to project selection
          console.log('[ProjectGuard] No access to project, redirecting to project selection');
          navigate(ROUTES.PROJECT_SELECTION.basePath);
        }
        // If projects array is empty, wait for it to load (handled by TenantContext)
      }
    } else if (!contextProjectId && !isExcludedPath && !urlProjectId) {
      // No project context and not on excluded route or project-scoped route
      console.log('[ProjectGuard] No project context, redirecting to project selection');
      navigate(ROUTES.PROJECT_SELECTION.basePath);
    }
  }, [contextProjectId, location.pathname, navigate, projects, switchProject]);

  return <>{children}</>;
}
