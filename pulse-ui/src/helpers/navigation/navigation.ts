import { getProjectContext } from '../projectContext';

export const getProjectRoute = (basePath: string): string => {
  const projectContext = getProjectContext();
  if (!projectContext) {
    throw new Error('No project context available');
  }
  return `/projects/${projectContext.projectId}${basePath}`;
};

// Helper to check if current route is project-scoped
export const isProjectScopedRoute = (pathname: string): boolean => {
  return pathname.startsWith('/projects/');
};

// Helper to check if current route is organization-scoped
export const isOrganizationRoute = (pathname: string): boolean => {
  return pathname.startsWith('/organization');
};
