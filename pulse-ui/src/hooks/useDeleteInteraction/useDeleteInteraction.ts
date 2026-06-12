import { UseMutationResult, useMutation } from "@tanstack/react-query";
import { API_BASE_URL } from "../../constants";
import {
  OnSettled,
  DeleteInteractionRequestBody,
  DeleteInteractionResponse,
} from "./useDeleteInteraction.interface";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";

export const useDeleteInteraction = (
  onSettled: OnSettled,
): UseMutationResult<
  ApiResponse<DeleteInteractionResponse>,
  unknown,
  DeleteInteractionRequestBody,
  unknown
> => {
  return useMutation<
    ApiResponse<DeleteInteractionResponse>,
    unknown,
    DeleteInteractionRequestBody
  >({
    mutationFn: (requestBody: DeleteInteractionRequestBody) => {
      const interactionName = requestBody?.useCaseId;

      if (!interactionName) {
        throw new Error("Interaction name is required");
      }

      // Based on backend: DELETE /v1/interactions/{name}
      const url = `${API_BASE_URL}/v1/interactions/${interactionName}`;
      return makeRequest<DeleteInteractionResponse>({
        url,
        init: {
          method: "DELETE",
          headers: {
            "user-email": requestBody.user,
          },
        },
      });
    },
    onSettled: onSettled,
  });
};

