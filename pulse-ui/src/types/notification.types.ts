export type ChannelType = "SLACK" | "SLACK_WEBHOOK" | "EMAIL" | "TEAMS";

export type SlackChannelConfig = {
  type: "SLACK";
  accessToken: string;
  workspaceId: string;
  botName?: string;
  iconEmoji?: string;
};

export type SlackWebhookChannelConfig = {
  type: "SLACK_WEBHOOK";
  botName?: string;
  iconEmoji?: string;
};

export type EmailChannelConfig = {
  type: "EMAIL";
  /** Omitted on create: pulse-server fills from DEFAULT_ALERT_EMAIL_FROM_* / config. */
  fromAddress?: string;
  fromName?: string;
  replyToAddress?: string;
  configurationSetName?: string;
};

export type TeamsChannelConfig = {
  type: "TEAMS";
  webhookUrl?: string;
};

export type ChannelConfig =
  | SlackChannelConfig
  | SlackWebhookChannelConfig
  | EmailChannelConfig
  | TeamsChannelConfig;

export type NotificationChannel = {
  id: number;
  projectId: string;
  channelType: ChannelType;
  name: string;
  config: ChannelConfig;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
};

export type ChannelEventMapping = {
  id: number;
  projectId: string;
  channelId: number;
  channelType: ChannelType;
  channelName: string;
  eventName: string;
  recipient: string | null;
  recipientName: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
};

export type SlackWorkspaceChannel = {
  id: string;
  name: string;
  isPrivate: boolean;
  isMember: boolean;
};
