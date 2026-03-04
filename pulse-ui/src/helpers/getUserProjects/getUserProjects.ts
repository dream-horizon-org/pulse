import { makeRequest, ApiResponse } from '../makeRequest';
import { API_BASE_URL } from '../../constants';

export interface ProjectSummary {
  projectId: string;
  name: string;
  description?: string;
  isActive: boolean;
  role: string;
}

export interface UserProjectsResponse {
  projects: ProjectSummary[];
  redirectTo?: string;
}

export const getUserProjects = async (): Promise<ApiResponse<UserProjectsResponse>> => {
  return makeRequest<UserProjectsResponse>({
    url: `${API_BASE_URL}/v1/users/me/projects`,
    init: {
      method: 'GET',
    },
  });
};
