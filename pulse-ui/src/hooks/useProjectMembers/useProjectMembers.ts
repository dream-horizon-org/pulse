import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { ProjectMemberListResponse } from "../../types/members";

export const useProjectMembers = (projectId: string) => {
  const route = API_ROUTES.GET_PROJECT_MEMBERS;
  
  return useQuery({
    queryKey: [route.key, projectId],
    queryFn: () =>
      makeRequest<ProjectMemberListResponse>({
        url: `${API_BASE_URL}${route.apiPath.replace(":projectId", projectId)}`,
        init: {
          method: route.method,
        },
      }),
    enabled: !!projectId,
    refetchOnWindowFocus: false,
  });
};
