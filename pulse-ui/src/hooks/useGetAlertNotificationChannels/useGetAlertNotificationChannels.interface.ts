/**
 * Matches backend: AlertNotificationChannelResponseDto.java
 */
export type NotificationChannelType = 'slack' | 'email';

export type AlertNotificationChannelItem = {
  notification_channel_id: number;
  name: string;
  type: NotificationChannelType;
  config: string; // webhook URL for slack, email address for email
  is_active: boolean;
};

export type GetAlertNotificationChannelsResponse = AlertNotificationChannelItem[];

/**
 * Matches backend: CreateAlertNotificationChannelRequestDto.java
 */
export type CreateNotificationChannelRequest = {
  name: string;
  type: NotificationChannelType;
  config: string;
};
