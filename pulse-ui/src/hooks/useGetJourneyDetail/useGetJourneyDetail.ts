import { useQuery } from "@tanstack/react-query";
import { fetchJourneyById } from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

export const useGetJourneyDetail = (journeyId: string | undefined) => {
  const enabled = useProjectQueryEnabled(!!journeyId);

  return useQuery({
    queryKey: ["journeyDetail", journeyId],
    queryFn: () => fetchJourneyById(journeyId as string),
    enabled: enabled && !!journeyId,
    refetchOnWindowFocus: false,
  });
};
