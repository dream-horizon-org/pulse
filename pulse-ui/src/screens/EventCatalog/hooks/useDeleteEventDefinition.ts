import { useMutation } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../../constants";
import { makeRequest } from "../../../helpers/makeRequest";

export const useDeleteEventDefinition = () => {
  const route = API_ROUTES.DELETE_EVENT_DEFINITION;

  return useMutation({
    mutationKey: [route.key],
    mutationFn: async (id: number) => {
      return makeRequest({
        url: `${API_BASE_URL}${route.apiPath}/${id}`,
        init: {
          method: route.method,
        },
      });
    },
  });
};
