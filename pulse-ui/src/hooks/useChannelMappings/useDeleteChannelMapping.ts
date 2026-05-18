import { useMutation, useQueryClient } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";

export const useDeleteChannelMapping = () => {
  const apiRoute = API_ROUTES.DELETE_CHANNEL_MAPPING;
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (mappingId: number) =>
      makeRequest<boolean>({
        url: `${API_BASE_URL}${apiRoute.apiPath.replace("{mappingId}", String(mappingId))}`,
        init: {
          method: apiRoute.method,
        },
      }),
    onSettled: () => {
      queryClient.invalidateQueries({
        queryKey: [API_ROUTES.GET_CHANNEL_MAPPINGS.key],
      });
    },
  });
};
