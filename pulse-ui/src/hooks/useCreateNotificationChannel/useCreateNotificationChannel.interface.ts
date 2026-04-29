import { ApiResponse } from "../../helpers/makeRequest";
import { ChannelConfig, ChannelType } from "../../types";

export type CreateNotificationChannelParams = {
  projectId?: string;
  channelType: ChannelType;
  name: string;
  config: ChannelConfig;
  eventNames?: string[];
};

/** Backend returns created channel id (Long), not full channel object. */
export type CreateNotificationChannelResponse = number;

export type CreateNotificationChannelOnSettled = (
  data: ApiResponse<CreateNotificationChannelResponse> | undefined,
  error: unknown,
  variables: CreateNotificationChannelParams,
  context: unknown,
) => void;

export interface UseCreateNotificationChannelOptions {
  onSettled?: CreateNotificationChannelOnSettled;
}
