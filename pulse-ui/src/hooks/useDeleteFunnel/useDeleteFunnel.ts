import { useMutation, useQueryClient } from "@tanstack/react-query";
import { deleteFunnel } from "../../services/funnels.service";

/**
 * Cascading delete for a funnel: removes the row, its tag mappings, analytics_jobs
 * entries, and ClickHouse funnel_results rows server-side. Invalidates the listing
 * and the per-id detail cache so consumers repaint.
 */
export const useDeleteFunnel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteFunnel(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ["funnelDetail", id] });
      queryClient.invalidateQueries({ queryKey: ["funnelsList"] });
    },
  });
};
