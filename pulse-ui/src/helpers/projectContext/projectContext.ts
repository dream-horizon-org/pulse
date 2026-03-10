import { getCookies, setCookies, removeCookie } from '../cookies';
import { COOKIES_KEY } from '../../constants';

export interface ProjectInfo {
  projectId: string;
  projectName: string;
}

export const setProjectContext = (projectInfo: ProjectInfo) => {
  setCookies(COOKIES_KEY.PROJECT_ID, projectInfo.projectId);
  setCookies(COOKIES_KEY.PROJECT_NAME, projectInfo.projectName);
};

export const getProjectContext = (): ProjectInfo | null => {
  const projectId = getCookies(COOKIES_KEY.PROJECT_ID);
  const projectName = getCookies(COOKIES_KEY.PROJECT_NAME);
  
  if (!projectId || projectId === 'undefined' || !projectName || projectName === 'undefined') {
    return null;
  }
  
  return { projectId, projectName };
};

export const getProjectIdFromPath = (pathname: string): string | null => {
  const match = pathname.match(/^\/projects\/([^/]+)/);
  return match ? match[1] : null;
};

export const clearProjectContext = () => {
  removeCookie(COOKIES_KEY.PROJECT_ID);
  removeCookie(COOKIES_KEY.PROJECT_NAME);
};
