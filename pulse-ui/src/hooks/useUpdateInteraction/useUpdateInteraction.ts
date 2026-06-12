import { UseMutationResult, useMutation } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  OnSettled,
  UpdateInteractionRequestBody,
  UpdateInteractionResponse,
} from "./useUpdateInteraction.interface";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";

export const useUpdateInteraction = (
  onSettled: OnSettled,
): UseMutationResult<
  ApiResponse<UpdateInteractionResponse>,
  unknown,
  UpdateInteractionRequestBody & { useCaseID: string },
  unknown
> => {
  const updateInteraction = API_ROUTES.UPDATE_JOB;
  return useMutation<
    ApiResponse<UpdateInteractionResponse>,
    unknown,
    UpdateInteractionRequestBody & { useCaseID: string }
  >({
    mutationFn: (requestBody: UpdateInteractionRequestBody & { useCaseID: string }) => {
      const interactionName = requestBody?.useCaseID;

      if (!interactionName) {
        throw new Error("Interaction name is required");
      }

      const url = `${API_BASE_URL}${updateInteraction.apiPath}/${interactionName}`;
      return makeRequest({
        url,
        init: {
          method: updateInteraction.method,
          body: JSON.stringify(requestBody?.jobDetails),
        },
      });
    },
    onSettled: onSettled,
  });
};

