import { useMutation, useQueryClient } from "@tanstack/react-query";
import { stopFunnel } from "../../services/funnels.service";

/**
 * Mutation hook for stopping auto-refresh on an AUTO funnel.
 * Server flips funnel_type to ONCE; the funnel renders as COMPLETED in the listing.
 * Invalidates the funnel's detail query and the listing query so both repaint with
 * the new status without a manual refresh.
 */
export const useStopFunnel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => stopFunnel(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ["funnelDetail", id] });
      queryClient.invalidateQueries({ queryKey: ["funnelsList"] });
    },
  });
};
