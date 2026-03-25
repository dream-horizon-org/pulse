import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { CreateProjectParams, ProjectResponse } from "./useCreateProject.interface";

export const useCreateProject = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.CREATE_PROJECT;

  return useMutation<ApiResponse<ProjectResponse>, unknown, CreateProjectParams>({
    mutationFn: (params: CreateProjectParams) =>
      makeRequest<ProjectResponse>({
        url: `${API_BASE_URL}${route.apiPath}`,
        init: {
          method: route.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(params),
        },
      }),
    onSuccess: (data: ApiResponse<ProjectResponse>) => {
      if (data?.data && !data?.error) {
        // Invalidate user projects list
        queryClient.invalidateQueries({
          queryKey: [API_ROUTES.GET_USER_PROJECTS.key],
        });
      }
    },
  });
};
