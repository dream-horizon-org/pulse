export type AlertNotificationChannelItem = {
  id: number;
  name: string;
  webhook_url: string;
};

export type GetAlertNotificationChannelsResponse = AlertNotificationChannelItem[];


