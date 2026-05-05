import { useMutation, useQueryClient } from "@tanstack/react-query";
import { deleteJourney } from "../../services/funnels.service";

/** Cascading delete for a journey. Mirrors `useDeleteFunnel`. */
export const useDeleteJourney = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteJourney(id),
    onSuccess: (_, id) => {
      queryClient.invalidateQueries({ queryKey: ["journeyDetail", id] });
      queryClient.invalidateQueries({ queryKey: ["journeysList"] });
    },
  });
};
