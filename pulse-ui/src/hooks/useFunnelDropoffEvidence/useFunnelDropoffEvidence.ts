import { useQuery } from "@tanstack/react-query";
import { fetchFunnelDropoffEvidence } from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

/**
 * Hydrates the side-panel's "View examples" drill-in. {@code sessionIds} comes from
 * {@code FunnelDropoffCause.exampleSessionIds}; disabled until at least one ID is set.
 */
export const useFunnelDropoffEvidence = (
  funnelId: string | undefined,
  stepIndex: number | undefined,
  sessionIds: string[] | undefined,
  runTime?: string,
) => {
  const hasSessions =
    Array.isArray(sessionIds) && sessionIds.some((id) => id.trim() !== "");
  const enabled = useProjectQueryEnabled(
    !!funnelId && stepIndex != null && stepIndex >= 0 && hasSessions,
  );
  const idsKey = hasSessions
    ? (sessionIds as string[]).slice().sort().join(",")
    : "";

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
        runTime,
      ),
    enabled,
    refetchOnWindowFocus: false,
  });
};
