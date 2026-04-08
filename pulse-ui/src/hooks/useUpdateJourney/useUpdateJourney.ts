import { useMutation, useQueryClient } from "@tanstack/react-query";
import { updateJourney, type CreateJourneyRequestBody } from "../../services/funnels.service";

export const useUpdateJourney = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: CreateJourneyRequestBody }) =>
      updateJourney(id, payload),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["journeyDetail", id] });
      queryClient.invalidateQueries({ queryKey: ["journeysList"] });
    },
  });
};
