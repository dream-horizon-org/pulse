import { useMemo } from "react";
import { useSlackChannels } from "../useSlackChannels";
import { useGetAlertNotificationChannels } from "../useGetAlertNotificationChannels";
import { AlertNotificationChannelItem } from "../useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface";
import { UseSlackWorkspaceStatusReturn } from "./useSlackWorkspaceStatus.interface";

function isSlackOAuthChannel(channel: AlertNotificationChannelItem): boolean {
  return channel.type === "slack" && !channel.config.startsWith("http");
}

/**
 * Hook to check if Slack OAuth workspace is connected for a project
 * Uses useSlackChannels (channels from workspace) and notification channels list
 * to detect connection status
 */
export const useSlackWorkspaceStatus = (
  projectId?: string,
): UseSlackWorkspaceStatusReturn => {
  const {
    data: slackChannels,
    isLoading: slackChannelsLoading,
    refetch: refetchSlackChannels,
  } = useSlackChannels(projectId);
  const {
    data: channelsData,
    isLoading: channelsLoading,
    refetch: refetchChannels,
  } = useGetAlertNotificationChannels();

  const channels = channelsData?.data ?? [];
  const hasSlackOAuthChannel = channels.some(isSlackOAuthChannel);
  const hasSlackChannels = slackChannels && slackChannels.length > 0;

  const isConnected = hasSlackChannels || hasSlackOAuthChannel;
  const isLoading = slackChannelsLoading || channelsLoading;

  const refetch = useMemo(
    () => () => {
      refetchSlackChannels();
      refetchChannels();
    },
    [refetchSlackChannels, refetchChannels],
  );

  return {
    isConnected,
    isLoading,
    refetch,
  };
};
