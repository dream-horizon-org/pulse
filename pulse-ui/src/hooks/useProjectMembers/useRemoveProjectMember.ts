import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { RemoveProjectMemberParams } from "../../types/members";

export const useRemoveProjectMember = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.REMOVE_PROJECT_MEMBER;
  const getRoute = API_ROUTES.GET_PROJECT_MEMBERS;

  return useMutation<ApiResponse<void>, unknown, RemoveProjectMemberParams>({
    mutationFn: (params: RemoveProjectMemberParams) =>
      makeRequest<void>({
        url: `${API_BASE_URL}${route.apiPath
          .replace(":projectId", params.projectId)
          .replace(":userId", params.userId)}`,
        init: {
          method: route.method,
        },
      }),
    onSuccess: (data: ApiResponse<void>, variables: RemoveProjectMemberParams) => {
      if (data?.data !== undefined && !data?.error) {
        queryClient.invalidateQueries({
          queryKey: [getRoute.key, variables.projectId],
        });
      }
    },
  });
};
