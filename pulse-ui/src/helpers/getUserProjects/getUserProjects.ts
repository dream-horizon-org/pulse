import { makeRequest, ApiResponse } from '../makeRequest';
import { API_BASE_URL } from '../../constants';
import { UserProjectsResponse } from './getUserProjects.interface';

export const getUserProjects = async (): Promise<ApiResponse<UserProjectsResponse>> => {
  return makeRequest<UserProjectsResponse>({
    url: `${API_BASE_URL}/v1/users/me/projects`,
    init: {
      method: 'GET',
    },
  });
};
