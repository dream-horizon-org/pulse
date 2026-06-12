import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../makeRequest";
import {
  CreateJobResponse,
  CriticalInteractionFormRequestBodyParams,
} from "./createJob.interface";

export const createJob = async (
  requestBody: CriticalInteractionFormRequestBodyParams,
) => {
  return makeRequest<CreateJobResponse>({
    url: `${API_BASE_URL}${API_ROUTES.CREATE_JOB.apiPath}`,
    init: {
      method: API_ROUTES.CREATE_JOB.method,
      body: JSON.stringify(requestBody),
    },
  });
};
