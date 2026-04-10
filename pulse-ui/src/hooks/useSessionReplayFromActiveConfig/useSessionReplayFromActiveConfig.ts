import { useMemo } from "react";
import { useGetActiveSdkConfig } from "../useSdkConfig";
import { SESSION_REPLAY_FEATURE_NAME } from "../../screens/SamplingConfig/SamplingConfig.constants";
import type { FeatureConfig } from "../../screens/SamplingConfig/SamplingConfig.interface";

export interface UseSessionReplayFromActiveConfigParams {
  enabled?: boolean;
  projectId?: string | null;
}

export function useSessionReplayFromActiveConfig({
  enabled = true,
  projectId,
}: UseSessionReplayFromActiveConfigParams = {}) {
  const hasProject = Boolean(projectId);
  const { data, isLoading, isFetching, error } = useGetActiveSdkConfig({
    enabled: enabled && hasProject,
    projectId,
  });

  const isSessionReplayEnabled = useMemo(() => {
    const features = data?.data?.features;
    if (!features?.length) return false;
    const replay = features.find(
      (f: FeatureConfig) => f.featureName === SESSION_REPLAY_FEATURE_NAME,
    );
    return replay != null && replay.sessionSampleRate === 1;
  }, [data]);

  return {
    isSessionReplayEnabled,
    isLoading: Boolean(enabled && hasProject && isLoading),
    isFetching,
    error,
  };
}
