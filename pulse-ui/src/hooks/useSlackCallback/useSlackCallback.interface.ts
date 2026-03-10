import { SlackOAuthResponseDto } from "../useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface";

export interface UseSlackCallbackParams {
  code: string;
  state: string; // projectId
}

export interface UseSlackCallbackReturn {
  completeCallback: (
    params: UseSlackCallbackParams,
  ) => Promise<SlackOAuthResponseDto | null>;
  isLoading: boolean;
  error: Error | null;
}
