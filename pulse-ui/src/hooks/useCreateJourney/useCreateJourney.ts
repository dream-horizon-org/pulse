import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createJourney } from "../../services/funnels.service";

export const useCreateJourney = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: Record<string, unknown>) => createJourney(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["journeysList"] });
    },
  });
};
