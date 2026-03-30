import { useMutation } from "@tanstack/react-query";
import { updateFunnelJourney } from "../../services/funnels.service";

export const useUpdateFunnelJourney = () => {
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: any }) =>
      updateFunnelJourney(id, payload),
  });
};
