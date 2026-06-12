import {
  UseMutationResult,
  useMutation,
  useQueryClient,
} from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  CreateNotificationChannelParams,
  CreateNotificationChannelResponse,
  UseCreateNotificationChannelOptions,
} from "./useCreateNotificationChannel.interface";

export const useCreateNotificationChannel = (
  options: UseCreateNotificationChannelOptions = {},
): UseMutationResult<
  ApiResponse<CreateNotificationChannelResponse>,
  unknown,
  CreateNotificationChannelParams,
  unknown
> => {
  const queryClient = useQueryClient();
  const createNotificationChannel = API_ROUTES.CREATE_NOTIFICATION_CHANNEL_V2;

  return useMutation<
    ApiResponse<CreateNotificationChannelResponse>,
    unknown,
    CreateNotificationChannelParams
  >({
    mutationFn: (params: CreateNotificationChannelParams) => {
      return makeRequest<CreateNotificationChannelResponse>({
        url: `${API_BASE_URL}${createNotificationChannel.apiPath}`,
        init: {
          method: createNotificationChannel.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify(params),
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
