import { ApiResponse } from "../helpers/makeRequest";

export const hasResponseError = (response: ApiResponse<unknown>) =>
  response.status !== 200 || !!response.error;
