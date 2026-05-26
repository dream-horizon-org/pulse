import { useQuery } from "@tanstack/react-query";
import { fetchFunnelDropoff } from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

/**
 * Fetches ranked drop-off causes for one funnel step (side-panel payload).
 * Disabled until both {@code funnelId} and {@code stepIndex} are set.
 */
export const useFunnelDropoff = (
  funnelId: string | undefined,
  stepIndex: number | undefined,
  runTime?: string,
) => {
  const enabled = useProjectQueryEnabled(
    !!funnelId && stepIndex != null && stepIndex >= 0,
  );

  return useQuery({
    queryKey: ["funnelDropoff", funnelId, stepIndex, runTime ?? "latest"],
    queryFn: () =>
      fetchFunnelDropoff(funnelId as string, stepIndex as number, runTime),
    enabled,
    refetchOnWindowFocus: false,
  });
};
