/**
 * Matches backend: AlertNotificationChannelResponseDto.java
 */
export type AlertNotificationChannelItem = {
  notification_channel_id: number;
  name: string;
  notification_webhook_url: string;
};

export type GetAlertNotificationChannelsResponse = AlertNotificationChannelItem[];
