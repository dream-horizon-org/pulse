import { useMutation } from "@tanstack/react-query";
import { createFunnelJourney } from "../../services/funnels.service";

export const useCreateFunnelJourney = () => {
  return useMutation({
    mutationFn: createFunnelJourney,
  });
};
