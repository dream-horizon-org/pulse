import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { ApiResponse } from "../../helpers/makeRequest";
import { ProjectDetailsResponse } from "./useGetProject.interface";

interface UseGetProjectParams {
  projectId: string | null;
  enabled?: boolean;
}

export const useGetProject = ({
  projectId,
  enabled = true,
}: UseGetProjectParams) => {
  const route = API_ROUTES.GET_PROJECT;

  return useQuery<ApiResponse<ProjectDetailsResponse>>({
    queryKey: ["project", projectId],
    queryFn: async () => {
      if (!projectId) {
        throw new Error("projectId is required");
      }
      return makeRequest<ProjectDetailsResponse>({
        url: `${API_BASE_URL}${route.apiPath.replace(":projectId", projectId)}`,
        init: {
          method: route.method,
        },
      });
    },
    enabled: enabled && !!projectId,
    staleTime: 30000,
    refetchOnMount: true,
  });
};
