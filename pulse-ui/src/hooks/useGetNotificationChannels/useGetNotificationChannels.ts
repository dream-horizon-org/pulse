import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { GetNotificationChannelsResponse } from "./useGetNotificationChannels.interface";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

export const useGetNotificationChannels = () => {
  const apiRoute = API_ROUTES.GET_NOTIFICATION_CHANNELS;
  const enabled = useProjectQueryEnabled();

  return useQuery({
    queryKey: [apiRoute.key],
    queryFn: async () => {
      return makeRequest<GetNotificationChannelsResponse>({
        url: `${API_BASE_URL}${apiRoute.apiPath}`,
        init: {
          method: apiRoute.method,
        },
      });
    },
    enabled,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
};
