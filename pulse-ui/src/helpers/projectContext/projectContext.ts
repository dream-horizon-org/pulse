import { ProjectInfo } from './projectContext.interface';

/**
 * Get project context from sessionStorage (single source of truth).
 * Project context is managed by ProjectContext React Context.
 */
export const getProjectContext = (): ProjectInfo | null => {
  try {
    const stored = sessionStorage.getItem('pulse_project_context');
    if (stored) {
      const data = JSON.parse(stored);
      if (data.projectId && data.projectId !== 'undefined' && 
          data.projectName && data.projectName !== 'undefined') {
        return {
          projectId: data.projectId,
          projectName: data.projectName
        };
      }
    }
  } catch (error) {
    console.error('[projectContext] Failed to parse project context from sessionStorage:', error);
  }
  
  return null;
};

export const getProjectIdFromPath = (pathname: string): string | null => {
  const match = pathname.match(/^\/projects\/([^/]+)/);
  return match ? match[1] : null;
};
