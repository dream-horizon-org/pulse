import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  GetInteractionDetailsParams,
  InteractionDetailsResponse,
} from "./useGetInteractionDetails.interface";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";

export const useGetInteractionDetails = ({
  queryParams = null,
}: GetInteractionDetailsParams) => {
  const interactionDetails = API_ROUTES.GET_JOB_DETAILS;

  return useQuery({
    queryKey: [interactionDetails.key, queryParams?.name],
    queryFn: async () => {
      if (queryParams?.name === null) {
        return {} as ApiResponse<InteractionDetailsResponse>;
      }

      return makeRequest<InteractionDetailsResponse>({
        url: `${API_BASE_URL}/v1/interactions/${queryParams?.name}`,
        init: {
          method: interactionDetails.method,
        },
      });
    },
    refetchOnWindowFocus: true,
  });
};
