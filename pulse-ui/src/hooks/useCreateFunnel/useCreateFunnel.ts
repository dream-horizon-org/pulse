import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createFunnel } from "../../services/funnels.service";

export const useCreateFunnel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: Record<string, unknown>) => createFunnel(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["funnelsList"] });
    },
  });
};
