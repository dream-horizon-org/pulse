import { useQuery } from "@tanstack/react-query";
import { fetchFunnelDropoff } from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

/**
 * Fetches the ranked drop-off causes for one step of a funnel. Returns the
 * full side-panel payload, including the step name, mode, cohort counts, and
 * sorted causes (lift DESC).
 *
 * Disabled until both {@code funnelId} and {@code stepIndex} are known so the
 * hook can be safely called from a parent that has not yet opened the panel.
 */
export const useFunnelDropoff = (
  funnelId: string | undefined,
  stepIndex: number | undefined,
  runTime?: string
) => {
  const enabled = useProjectQueryEnabled(
    !!funnelId && typeof stepIndex === "number"
  );

  return useQuery({
    queryKey: ["funnelDropoff", funnelId, stepIndex, runTime ?? "latest"],
    queryFn: () =>
      fetchFunnelDropoff(funnelId as string, stepIndex as number, runTime),
    enabled: enabled && !!funnelId && typeof stepIndex === "number",
    refetchOnWindowFocus: false,
  });
};
