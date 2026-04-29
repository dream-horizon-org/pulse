import { ChannelEventMapping, SlackWorkspaceChannel } from "../../types";

export type GetChannelMappingsResponse = ChannelEventMapping[];

export type CreateChannelMappingRequest = {
  channelId: number;
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
