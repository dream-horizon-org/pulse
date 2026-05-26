import { useQuery } from "@tanstack/react-query";
import { fetchFunnelDropoffEvidence } from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

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

  return useQuery({
    queryKey: [
      "funnelDropoffEvidence",
      funnelId,
      stepIndex,
      sessionIds,
      runTime,
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
