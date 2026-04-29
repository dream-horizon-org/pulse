import { useMutation, useQueryClient } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import {
  CreateChannelMappingRequest,
  GetChannelMappingsResponse,
} from "./useChannelMappings.interface";

export const useCreateChannelMapping = () => {
  const apiRoute = API_ROUTES.CREATE_CHANNEL_MAPPING;
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload: CreateChannelMappingRequest) =>
      makeRequest<GetChannelMappingsResponse[number]>({
        url: `${API_BASE_URL}${apiRoute.apiPath}`,
        init: {
          method: apiRoute.method,
          body: JSON.stringify(payload),
        },
      }),
    onSettled: () => {
      queryClient.invalidateQueries({
        queryKey: [API_ROUTES.GET_CHANNEL_MAPPINGS.key],
      });
    },
  });
};
