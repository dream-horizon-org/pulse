import { useMutation, useQueryClient } from "@tanstack/react-query";
import { stopJourney } from "../../services/funnels.service";

/**
 * Mutation hook for stopping auto-refresh on an AUTO journey. Mirrors `useStopFunnel`.
 */
export const useStopJourney = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => stopJourney(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ["journeyDetail", id] });
      queryClient.invalidateQueries({ queryKey: ["journeysList"] });
    },
  });
};
