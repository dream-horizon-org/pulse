import { SlackChannelListDto } from "../useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface";
import { UseQueryOptions } from "@tanstack/react-query";

export type UseSlackChannelsOptions = UseQueryOptions<
  SlackChannelListDto[],
  unknown,
  SlackChannelListDto[],
  [string, string | undefined]
>;

export type SlackChannelsResponse = SlackChannelListDto[];
