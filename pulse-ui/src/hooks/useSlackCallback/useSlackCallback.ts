import { useState } from "react";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { SlackOAuthResponseDto } from "../useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface";
import {
  UseSlackCallbackParams,
  UseSlackCallbackReturn,
} from "./useSlackCallback.interface";

/**
 * Hook to complete Slack OAuth callback
 * Exchanges authorization code for tokens and creates channel
 */
export const useSlackCallback = (): UseSlackCallbackReturn => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const completeCallback = async ({
    code,
    state,
  }: UseSlackCallbackParams): Promise<SlackOAuthResponseDto | null> => {
    setIsLoading(true);
    setError(null);

    try {
      const url = `${API_BASE_URL}${API_ROUTES.SLACK_CALLBACK.apiPath}?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`;

      const response = await makeRequest<SlackOAuthResponseDto>({
        url,
        init: {
          method: API_ROUTES.SLACK_CALLBACK.method,
          headers: {
            "Content-Type": "application/json",
            "X-Project-Id": state,
          },
        },
      });

      if (response.error) {
        throw new Error(
          response.error.message || "Failed to complete Slack OAuth",
        );
      }

      return response.data ?? null;
    } catch (err) {
      const errorMessage =
        err instanceof Error ? err.message : "Unknown error occurred";
      setError(new Error(errorMessage));
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  return {
    completeCallback,
    isLoading,
    error,
  };
};
