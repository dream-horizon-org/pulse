import { useMutation, useQueryClient } from "@tanstack/react-query";
import { updateFunnel } from "../../services/funnels.service";

export const useUpdateFunnel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: unknown }) =>
      updateFunnel(id, payload),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["funnelDetail", id] });
      queryClient.invalidateQueries({ queryKey: ["funnelsList"] });
    },
  });
};
