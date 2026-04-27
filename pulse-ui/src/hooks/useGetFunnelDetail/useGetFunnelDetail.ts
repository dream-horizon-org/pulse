import { useQuery } from "@tanstack/react-query";
import { fetchFunnelById } from "../../services/funnels.service";
import { useProjectQueryEnabled } from "../useProjectQueryEnabled";

export const useGetFunnelDetail = (funnelId: string | undefined) => {
  const enabled = useProjectQueryEnabled(!!funnelId);

  return useQuery({
    queryKey: ["funnelDetail", funnelId],
    queryFn: () => fetchFunnelById(funnelId as string),
    enabled: enabled && !!funnelId,
    refetchOnWindowFocus: false,
  });
};
