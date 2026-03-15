import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { GetAlertNotificationChannelsResponse } from "./useGetAlertNotificationChannels.interface";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

export const useGetAlertNotificationChannels = () => {
  const getAlertNotificationChannels =
    API_ROUTES.GET_ALERT_NOTIFICATION_CHANNELS;
  const enabled = useProjectQueryEnabled();

  return useQuery({
    queryKey: [getAlertNotificationChannels.key],
    queryFn: async () => {
      return makeRequest<GetAlertNotificationChannelsResponse>({
        url: `${API_BASE_URL}${getAlertNotificationChannels.apiPath}`,
        init: {
          method: getAlertNotificationChannels.method,
        },
      });
    },
    enabled,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
};
