import { useMutation, useQueryClient } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import {
  GetChannelMappingsResponse,
  UpdateChannelMappingRequest,
} from "./useChannelMappings.interface";

export const useUpdateChannelMapping = () => {
  const apiRoute = API_ROUTES.UPDATE_CHANNEL_MAPPING;
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async ({
      mappingId,
      ...payload
    }: UpdateChannelMappingRequest) =>
      makeRequest<GetChannelMappingsResponse[number]>({
        url: `${API_BASE_URL}${apiRoute.apiPath.replace("{mappingId}", String(mappingId))}`,
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
