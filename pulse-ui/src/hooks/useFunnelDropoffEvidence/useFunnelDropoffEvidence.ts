import { useQuery } from "@tanstack/react-query";
import { fetchFunnelDropoffEvidence } from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

/**
 * Hydrates the side-panel's "View examples" drill-in. {@code sessionIds} is
 * expected to come from a {@code FunnelDropoffCause.exampleSessionIds} array;
 * the hook stays disabled until at least one ID is supplied.
 */
export const useFunnelDropoffEvidence = (
  funnelId: string | undefined,
  stepIndex: number | undefined,
  sessionIds: string[] | undefined,
  runTime?: string
) => {
  const hasIds = !!sessionIds && sessionIds.length > 0;
  const enabled = useProjectQueryEnabled(
    !!funnelId && typeof stepIndex === "number" && hasIds
  );
  const idsKey = hasIds ? (sessionIds as string[]).slice().sort().join(",") : "";

  return useQuery({
    queryKey: [
      "funnelDropoffEvidence",
      funnelId,
      stepIndex,
      runTime ?? "latest",
      idsKey,
    ],
    queryFn: () =>
      fetchFunnelDropoffEvidence(
        funnelId as string,
        stepIndex as number,
        sessionIds as string[],
        runTime
      ),
    enabled:
      enabled && !!funnelId && typeof stepIndex === "number" && hasIds,
    refetchOnWindowFocus: false,
  });
};
