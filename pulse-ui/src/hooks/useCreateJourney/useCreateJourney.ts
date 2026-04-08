import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createJourney, type CreateJourneyRequestBody } from "../../services/funnels.service";

export const useCreateJourney = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateJourneyRequestBody) => createJourney(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["journeysList"] });
    },
  });
};
