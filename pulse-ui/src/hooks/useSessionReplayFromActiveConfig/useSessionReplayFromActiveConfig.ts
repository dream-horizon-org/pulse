import { useMemo } from "react";
import { useGetActiveSdkConfig } from "../useSdkConfig";
import { SESSION_REPLAY_FEATURE_NAME } from "../../screens/SamplingConfig/SamplingConfig.constants";

export interface UseSessionReplayFromActiveConfigParams {
  enabled?: boolean;
  projectId?: string | null;
}

export function useSessionReplayFromActiveConfig({
  enabled = true,
  projectId,
}: UseSessionReplayFromActiveConfigParams = {}) {
  const hasProject = Boolean(projectId);
  const { data, isLoading, isFetching, error, dataUpdatedAt } =
    useGetActiveSdkConfig({
      enabled: enabled && hasProject,
      projectId,
    });

  const isSessionReplayEnabled = useMemo(() => {
    const features = data?.data?.features;
    if (!features?.length) return false;
    const replay = features.find(
      (f) => f.featureName === SESSION_REPLAY_FEATURE_NAME,
    );
    return replay != null && replay.sessionSampleRate === 1;
  }, [data, dataUpdatedAt]);

  return {
    isSessionReplayEnabled,
    isLoading: Boolean(enabled && hasProject && isLoading),
    isFetching,
    error,
  };
}
