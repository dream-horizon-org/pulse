import { useQuery, UseQueryResult } from "@tanstack/react-query";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { SlackChannelListDto } from "../useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface";
import { UseSlackChannelsOptions, SlackChannelsResponse } from "./useSlackChannels.interface";

/**
 * Hook to get list of Slack channels for the connected workspace
 * Requires X-Project-Id header
 */
export const useSlackChannels = (
  projectId?: string,
  options: Partial<UseSlackChannelsOptions> = {}
): UseQueryResult<SlackChannelsResponse, unknown> => {
  return useQuery({
    queryKey: [API_ROUTES.SLACK_CHANNELS.key, projectId],
    queryFn: async () => {
      const response = await makeRequest<SlackChannelListDto[]>({
        url: `${API_BASE_URL}${API_ROUTES.SLACK_CHANNELS.apiPath}`,
        init: {
          method: API_ROUTES.SLACK_CHANNELS.method,
          headers: {
            "Content-Type": "application/json",
            ...(projectId && { "X-Project-Id": projectId }),
          },
        },
      });

      if (response.error) {
        throw new Error(response.error.message || "Failed to fetch Slack channels");
      }

      return response.data || [];
    },
    enabled: !!projectId,
    ...options,
  });
};
