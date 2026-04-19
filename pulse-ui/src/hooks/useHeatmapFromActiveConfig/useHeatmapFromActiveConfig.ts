import { useMemo } from "react";
import { useGetActiveSdkConfig } from "../useSdkConfig";
import { HEATMAP_FEATURE_NAME } from "../../screens/SamplingConfig/SamplingConfig.constants";

export interface UseHeatmapFromActiveConfigParams {
  enabled?: boolean;
  projectId?: string | null;
}

/**
 * Mirrors session replay gating: heatmap UI is on only when active config lists
 * {@link HEATMAP_FEATURE_NAME} with {@code sessionSampleRate === 1}.
 */
export function useHeatmapFromActiveConfig({
  enabled = true,
  projectId,
}: UseHeatmapFromActiveConfigParams = {}) {
  const hasProject = Boolean(projectId);
  const { data, isLoading, isFetching, error } = useGetActiveSdkConfig({
    enabled: enabled && hasProject,
    projectId,
  });

  const isHeatmapEnabled = useMemo(() => {
    const features = data?.data?.features;
    if (!features?.length) return false;
    const heatmap = features.find(
      (f) => f.featureName === HEATMAP_FEATURE_NAME,
    );
    return heatmap != null && heatmap.sessionSampleRate === 1;
  }, [data?.data?.features]);

  return {
    isHeatmapEnabled,
    isLoading: Boolean(enabled && hasProject && isLoading),
    isFetching,
    error,
  };
}
