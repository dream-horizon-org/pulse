import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";
import { GetChannelMappingsResponse } from "./useChannelMappings.interface";

type UseGetChannelMappingsParams = {
  eventName?: string;
};

export const useGetChannelMappings = ({
  eventName,
}: UseGetChannelMappingsParams = {}) => {
  const apiRoute = API_ROUTES.GET_CHANNEL_MAPPINGS;
  const enabled = useProjectQueryEnabled();

  return useQuery({
    queryKey: [apiRoute.key, eventName ?? "all"],
    queryFn: async () => {
      const response = await makeRequest<GetChannelMappingsResponse>({
        url: `${API_BASE_URL}${apiRoute.apiPath}`,
        init: {
          method: apiRoute.method,
        },
      });
      if (!eventName || !response.data) {
        return response;
      }
      return {
        ...response,
        data: response.data.filter(
          (mapping) => mapping.eventName === eventName,
        ),
      };
    },
    enabled,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
};
