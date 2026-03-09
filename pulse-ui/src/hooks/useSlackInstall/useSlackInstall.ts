import { useState } from "react";
import { makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { UseSlackInstallOptions, UseSlackInstallReturn } from "./useSlackInstall.interface";

/**
 * Hook to get Slack OAuth installation URL
 * Requires X-Project-Id header
 */
export const useSlackInstall = (options: UseSlackInstallOptions = {}): UseSlackInstallReturn => {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  const getInstallUrl = async (): Promise<string | null> => {
    setIsLoading(true);
    setError(null);

    try {
      const response = await makeRequest<string>({
        url: `${API_BASE_URL}${API_ROUTES.SLACK_INSTALL.apiPath}`,
        init: {
          method: API_ROUTES.SLACK_INSTALL.method,
          headers: {
            "Content-Type": "application/json",
            ...(options.projectId && { "X-Project-Id": options.projectId }),
          },
        },
      });

      if (response.error) {
        throw new Error(response.error.message || "Failed to get Slack install URL");
      }

      return response.data ?? null;
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : "Unknown error occurred";
      setError(new Error(errorMessage));
      return null;
    } finally {
      setIsLoading(false);
    }
  };

  return {
    getInstallUrl,
    isLoading,
    error,
  };
};
