import { useMutation, useQueryClient } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest, ApiResponse } from "../../helpers/makeRequest";

interface DismissParams {
  id: number;
}

export const useDismissSuggestion = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.DISMISS_SUGGESTED_INTERACTION;

  return useMutation<ApiResponse<Record<string, never>>, unknown, DismissParams>({
    mutationFn: ({ id }: DismissParams) => {
      return makeRequest<Record<string, never>>({
        url: `${API_BASE_URL}${route.apiPath}/${id}/dismiss`,
        init: {
          method: route.method,
        },
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: [API_ROUTES.GET_SUGGESTED_INTERACTIONS.key],
      });
    },
  });
};
