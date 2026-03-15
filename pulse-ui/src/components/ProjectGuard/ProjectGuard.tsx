import { useEffect } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { useProjectContext, useTenantContext } from "../../contexts";
import { ROUTES } from "../../constants";

interface ProjectGuardProps {
  children: React.ReactNode;
}

/**
 * Guard component that ensures a project is selected before accessing protected routes.
 * When URL contains a projectId that doesn't match context, calls navigateToProject
 * to fetch project details and update context.
 */
export function ProjectGuard({ children }: ProjectGuardProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    projectId: contextProjectId,
    navigateToProject,
    isInitializing,
  } = useProjectContext();
  const { projects, tenantId } = useTenantContext();

  useEffect(() => {
    if (!tenantId || isInitializing) {
      return;
    }

    const excludedPaths = [
      ROUTES.LOGIN.basePath,
      ROUTES.ONBOARDING.basePath,
      ROUTES.PRICING.basePath,
    ];

    const isOrganizationPath = /^\/[^/]+\/(projects|members)/.test(
      location.pathname,
    );
    const isOnboardingPath = /^\/projects\/[^/]+\/onboarding/.test(
      location.pathname,
    );
    const isExcludedPath = excludedPaths.some((path) =>
      location.pathname.startsWith(path),
    );

    // Don't interfere with onboarding pages
    if (isOnboardingPath) {
      return;
    }

    // Extract projectId from URL
    const projectIdMatch = location.pathname.match(/^\/projects\/([^/]+)/);
    const urlProjectId = projectIdMatch ? projectIdMatch[1] : null;

    if (urlProjectId && !isExcludedPath) {
      // Check if context needs to be synced
      if (!contextProjectId || contextProjectId !== urlProjectId) {
        const hasAccess = projects.some((p) => p.projectId === urlProjectId);

        if (hasAccess) {
          // Context is out of sync - fetch and navigate
          navigateToProject(urlProjectId);
        } else if (projects.length > 0 && tenantId) {
          // No access - redirect to projects list
          navigate(`/${tenantId}/projects`);
        }
      }
    } else if (
      !contextProjectId &&
      !isExcludedPath &&
      !urlProjectId &&
      !isOrganizationPath
    ) {
      // No project in URL and no project in context - redirect to projects list
      if (tenantId) {
        navigate(`/${tenantId}/projects`);
      }
    }
  }, [
    contextProjectId,
    location.pathname,
    navigate,
    projects,
    tenantId,
    navigateToProject,
    isInitializing,
  ]);

  return <>{children}</>;
}
