import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../makeRequest";
import { InteractionDetailsResponse } from "./getInteractionDetails.interface";

export const getInteractionDetails = async (jobId: string = "") => {
  return makeRequest<InteractionDetailsResponse>({
    url: `${API_BASE_URL}${API_ROUTES.GET_JOB_DETAILS.apiPath}?useCaseId=${jobId}`,
    init: {
      method: API_ROUTES.GET_JOB_DETAILS.method,
    },
  });
};

