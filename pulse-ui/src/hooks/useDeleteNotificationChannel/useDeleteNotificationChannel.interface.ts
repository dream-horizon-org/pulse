import { ApiResponse } from "../../helpers/makeRequest";

export type DeleteNotificationChannelRequest = {
  channelId: number;
};

export type DeleteNotificationChannelResponse = boolean;

export type DeleteNotificationChannelOnSettled = (
  data: ApiResponse<DeleteNotificationChannelResponse> | undefined,
  error: unknown,
  variables: DeleteNotificationChannelRequest,
  context: unknown,
) => void;

export interface UseDeleteNotificationChannelOptions {
  onSettled?: DeleteNotificationChannelOnSettled;
}
