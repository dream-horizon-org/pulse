import { AlertNotificationChannelItem } from "../useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface";

export interface UseGetNotificationChannelByIdParams {
  notificationChannelId: number | null;
}

export type GetNotificationChannelByIdResponse = AlertNotificationChannelItem;
