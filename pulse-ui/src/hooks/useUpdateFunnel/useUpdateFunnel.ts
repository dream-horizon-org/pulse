import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  updateFunnel,
  type UpdateFunnelRequestBody,
} from "../../services/funnels.service";

export const useUpdateFunnel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: UpdateFunnelRequestBody }) =>
      updateFunnel(id, payload),
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ["funnelDetail", id] });
      queryClient.invalidateQueries({ queryKey: ["funnelsList"] });
    },
  });
};
