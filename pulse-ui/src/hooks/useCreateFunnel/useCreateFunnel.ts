import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  createFunnel,
  type CreateFunnelRequestBody,
} from "../../services/funnels.service";

export const useCreateFunnel = () => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: CreateFunnelRequestBody) => createFunnel(payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["funnelsList"] });
    },
  });
};
