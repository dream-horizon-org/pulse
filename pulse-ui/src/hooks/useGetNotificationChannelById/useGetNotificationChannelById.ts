import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  UseGetNotificationChannelByIdParams,
  GetNotificationChannelByIdResponse,
} from "./useGetNotificationChannelById.interface";

export const useGetNotificationChannelById = ({
  notificationChannelId,
}: UseGetNotificationChannelByIdParams) => {
  const apiRoute = API_ROUTES.GET_NOTIFICATION_CHANNEL_BY_ID;

  return useQuery({
    queryKey: [apiRoute.key, notificationChannelId],
    queryFn: async () => {
      const url = `${API_BASE_URL}${apiRoute.apiPath.replace(
        "{notificationChannelId}",
        String(notificationChannelId)
      )}`;
      return makeRequest<GetNotificationChannelByIdResponse>({
        url,
        init: {
          method: apiRoute.method,
        },
      });
    },
    enabled: notificationChannelId !== null && notificationChannelId > 0,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
};
