import { ApiResponse } from "../../helpers/makeRequest";

export type QueryValidationRequestBody = { query: string };
export type QueryValidationSuccessResponseBody = {
  success: boolean;
  errorMessage?: string;
};
export type QueryValidationSuccessResponse =
  ApiResponse<QueryValidationSuccessResponseBody>;
export type QueryValidationErrorResponse = {
  data: null;
  error: { code: string; message: string; cause?: string };
  status: number;
};
