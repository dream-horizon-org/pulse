import { ApiResponse } from "../../helpers/makeRequest";
import { ChannelConfig, NotificationChannel } from "../../types";

export type UpdateNotificationChannelRequest = {
  channelId: number;
  name?: string;
  config?: ChannelConfig;
  isActive?: boolean;
};

export type UpdateNotificationChannelResponse = NotificationChannel;

export type UpdateNotificationChannelOnSettled = (
  data: ApiResponse<UpdateNotificationChannelResponse> | undefined,
  error: unknown,
  variables: UpdateNotificationChannelRequest,
  context: unknown,
) => void;

export interface UseUpdateNotificationChannelOptions {
  onSettled?: UpdateNotificationChannelOnSettled;
}
