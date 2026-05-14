import {
  ChannelEventMapping,
  ChannelType,
  SlackWorkspaceChannel,
} from "../../types";

export type GetChannelMappingsResponse = ChannelEventMapping[];

/**
 * Either pass `channelId` directly, or pass `channelType` to route to the
 * platform-managed default channel for that type (e.g. EMAIL, SLACK_WEBHOOK).
 */
export type CreateChannelMappingRequest = {
  channelId?: number;
  channelType?: ChannelType;
  eventName: string;
  recipient?: string;
  recipientName?: string;
};

export type UpdateChannelMappingRequest = {
  mappingId: number;
  recipient?: string;
  recipientName?: string;
  isActive?: boolean;
};

export type CreateChannelMappingsBatchRequest = {
  mappings: CreateChannelMappingRequest[];
};

export type GetSlackChannelsResponse = SlackWorkspaceChannel[];
