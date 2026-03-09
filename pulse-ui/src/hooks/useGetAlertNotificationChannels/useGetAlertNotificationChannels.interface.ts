/**
 * Notification Service Type Definitions
 * Matches backend API contract: /v1/notifications/*
 */

// =============================================================================
// Channel Types & Enums
// =============================================================================

export type ChannelType = 'SLACK' | 'SLACK_WEBHOOK' | 'EMAIL' | 'TEAMS' | 'ALL';

export type NotificationStatus = 
  | 'PENDING' 
  | 'QUEUED' 
  | 'PROCESSING' 
  | 'SENT' 
  | 'DELIVERED' 
  | 'FAILED' 
  | 'RETRYING' 
  | 'SKIPPED' 
  | 'PERMANENT_FAILURE' 
  | 'BOUNCED' 
  | 'COMPLAINED';

// =============================================================================
// Channel Configuration (Polymorphic)
// =============================================================================

export type SlackChannelConfig = {
  type: 'SLACK';
  accessToken: string;
  workspaceId: string;
  botName: string;
  iconEmoji: string;
};

export type SlackWebhookChannelConfig = {
  type: 'SLACK_WEBHOOK';
  botName: string;
  iconEmoji: string;
};

export type EmailChannelConfig = {
  type: 'EMAIL';
  fromAddress: string;
  fromName: string;
  replyToAddress: string;
  configurationSetName: string;
};

export type TeamsChannelConfig = {
  type: 'TEAMS';
  workflowUrl: string;
  defaultTitle: string;
};

export type ChannelConfig = 
  | SlackChannelConfig 
  | SlackWebhookChannelConfig 
  | EmailChannelConfig 
  | TeamsChannelConfig;

// =============================================================================
// Notification Channel DTOs
// =============================================================================

export type NotificationChannelDto = {
  id: number;
  projectId: string;
  channelType: ChannelType;
  name: string;
  config: ChannelConfig;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
};

export type CreateChannelRequestDto = {
  projectId?: string;
  channelType: ChannelType;
  name: string;
  config: ChannelConfig;
  eventNames?: string[];
};

export type UpdateChannelRequestDto = {
  name?: string;
  config?: ChannelConfig;
  isActive?: boolean;
};

// =============================================================================
// Slack OAuth Integration DTOs
// =============================================================================

export type SlackOAuthResponseDto = {
  success: boolean;
  workspaceId: string | null;
  workspaceName: string | null;
  channelId: number | null;
  message: string;
  installUrl: string | null;
};

export type SlackChannelListDto = {
  id: string;
  name: string;
  isPrivate: boolean;
  isMember: boolean;
};

// =============================================================================
// Legacy Type Aliases (for backward compatibility)
// =============================================================================

/**
 * @deprecated Use ChannelType instead
 */
export type NotificationChannelType = 'slack' | 'email';

/**
 * @deprecated Use NotificationChannelDto instead
 */
export type AlertNotificationChannelItem = {
  notification_channel_id: number;
  name: string;
  type: NotificationChannelType;
  config: string;
  is_active: boolean;
};

/**
 * @deprecated Use NotificationChannelDto[] instead
 */
export type GetAlertNotificationChannelsResponse = AlertNotificationChannelItem[];

/**
 * @deprecated Use CreateChannelRequestDto instead
 */
export type CreateNotificationChannelRequest = {
  name: string;
  type: NotificationChannelType;
  config: string;
};
