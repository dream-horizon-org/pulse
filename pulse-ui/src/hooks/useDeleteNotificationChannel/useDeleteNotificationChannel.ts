import {
  UseMutationResult,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  DeleteNotificationChannelRequest,
  DeleteNotificationChannelResponse,
  UseDeleteNotificationChannelOptions,
} from "./useDeleteNotificationChannel.interface";

export const useDeleteNotificationChannel = (
  options: UseDeleteNotificationChannelOptions = {},
): UseMutationResult<
  ApiResponse<DeleteNotificationChannelResponse>,
  unknown,
  DeleteNotificationChannelRequest,
  unknown
> => {
  const queryClient = useQueryClient();
  const deleteNotificationChannel = API_ROUTES.DELETE_NOTIFICATION_CHANNEL_V2;

  return useMutation<
    ApiResponse<DeleteNotificationChannelResponse>,
    unknown,
    DeleteNotificationChannelRequest
  >({
    mutationFn: (params: DeleteNotificationChannelRequest) => {
      return makeRequest<DeleteNotificationChannelResponse>({
        url: `${API_BASE_URL}${deleteNotificationChannel.apiPath.replace("{channelId}", String(params.channelId))}`,
        init: {
          method: deleteNotificationChannel.method,
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
