import { ApiResponse } from "../../helpers/makeRequest";
import { ChannelConfig, NotificationChannel, ChannelType } from "../../types";

export type CreateNotificationChannelParams = {
  projectId?: string;
  channelType: ChannelType;
  name: string;
  config: ChannelConfig;
  eventNames?: string[];
};

export type CreateNotificationChannelResponse = NotificationChannel;

export type CreateNotificationChannelOnSettled = (
  data: ApiResponse<CreateNotificationChannelResponse> | undefined,
  error: unknown,
  variables: CreateNotificationChannelParams,
  context: unknown,
) => void;

export interface UseCreateNotificationChannelOptions {
  onSettled?: CreateNotificationChannelOnSettled;
}
