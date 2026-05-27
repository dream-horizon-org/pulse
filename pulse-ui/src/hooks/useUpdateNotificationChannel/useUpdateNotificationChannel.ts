import {
  UseMutationResult,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  UpdateNotificationChannelRequest,
  UpdateNotificationChannelResponse,
  UseUpdateNotificationChannelOptions,
} from "./useUpdateNotificationChannel.interface";

export const useUpdateNotificationChannel = (
  options: UseUpdateNotificationChannelOptions = {},
): UseMutationResult<
  ApiResponse<UpdateNotificationChannelResponse>,
  unknown,
  UpdateNotificationChannelRequest,
  unknown
> => {
  const queryClient = useQueryClient();
  const updateNotificationChannel = API_ROUTES.UPDATE_NOTIFICATION_CHANNEL_V2;

  return useMutation<
    ApiResponse<UpdateNotificationChannelResponse>,
    unknown,
    UpdateNotificationChannelRequest
  >({
    mutationFn: (params: UpdateNotificationChannelRequest) => {
      return makeRequest<UpdateNotificationChannelResponse>({
        url: `${API_BASE_URL}${updateNotificationChannel.apiPath.replace("{channelId}", String(params.channelId))}`,
        init: {
          method: updateNotificationChannel.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            name: params.name,
            config: params.config,
            isActive: params.isActive,
          }),
        },
      });
    },
    onSettled: (data, error, variables, context) => {
      if (data?.data && !data?.error) {
        queryClient.invalidateQueries({
          queryKey: [API_ROUTES.GET_NOTIFICATION_CHANNELS.key],
        });
      }
      options.onSettled?.(data, error, variables, context);
    },
  });
};
