import { useQuery } from "@tanstack/react-query";
import { fetchFunnelJourneyById } from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

export const useGetFunnelJourneyDetail = (id: string | undefined) => {
  const enabled = useProjectQueryEnabled(!!id);

  return useQuery({
    queryKey: ["funnelJourneyDetail", id],
    queryFn: () => fetchFunnelJourneyById(id as string),
    enabled,
    refetchOnWindowFocus: false,
  });
};
