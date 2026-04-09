import { useMutation, useQueryClient } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest, ApiResponse } from "../../helpers/makeRequest";
import { showNotification } from "../../helpers/showNotification";
import {
  IconCircleCheckFilled,
  IconSquareRoundedX,
} from "@tabler/icons-react";

interface ActivateParams {
  id: number;
}

export const useActivateSuggestion = () => {
  const queryClient = useQueryClient();
  const route = API_ROUTES.ACTIVATE_SUGGESTED_INTERACTION;

  return useMutation<ApiResponse<Record<string, never>>, Error, ActivateParams>({
    mutationFn: async ({ id }: ActivateParams) => {
      const response = await makeRequest<Record<string, never>>({
        url: `${API_BASE_URL}${route.apiPath}/${id}/activate`,
        init: {
          method: route.method,
        },
      });

      // makeRequest resolves even on non-2xx — throw to trigger onError
      if (response.error) {
        throw new Error(response.error.message || "Failed to activate suggestion");
      }

      return response;
    },
    onSuccess: () => {
      showNotification(
        "Success!",
        "Interaction is now being tracked.",
        <IconCircleCheckFilled size={16} />,
        "green",
      );
      queryClient.invalidateQueries({
        queryKey: [API_ROUTES.GET_SUGGESTED_INTERACTIONS.key],
      });
      queryClient.invalidateQueries({
        queryKey: [API_ROUTES.GET_INTERACTIONS.key],
      });
    },
    onError: () => {
      showNotification(
        "Duplicate Interaction",
        "An interaction with the same event sequence already exists.",
        <IconSquareRoundedX size={16} />,
        "red",
      );
      // Suggestion is auto-dismissed on duplicate, refresh the list
      queryClient.invalidateQueries({
        queryKey: [API_ROUTES.GET_SUGGESTED_INTERACTIONS.key],
      });
    },
  });
};
