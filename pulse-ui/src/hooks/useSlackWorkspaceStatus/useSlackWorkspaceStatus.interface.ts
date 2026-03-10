export type UseSlackWorkspaceStatusReturn = {
  /** True if Slack OAuth workspace is connected (channels available or OAuth channel exists) */
  isConnected: boolean;
  /** True while loading workspace status */
  isLoading: boolean;
  /** Refetch workspace status and channels */
  refetch: () => void;
};
