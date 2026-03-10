import { SlackOAuthResponseDto } from "../useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface";

export interface UseSlackCallbackParams {
  code: string;
  state: string; // projectId
  error?: string;
}

export interface UseSlackCallbackReturn {
  exchangeCode: (
    params: UseSlackCallbackParams,
  ) => Promise<SlackOAuthResponseDto | null>;
  isLoading: boolean;
  error: Error | null;
}
