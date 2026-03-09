export type UseSlackInstallOptions = {
  projectId?: string;
};

export type UseSlackInstallReturn = {
  getInstallUrl: () => Promise<string | null>;
  isLoading: boolean;
  error: Error | null;
};
