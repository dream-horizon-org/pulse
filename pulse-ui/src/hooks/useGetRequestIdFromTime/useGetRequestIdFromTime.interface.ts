import { ApiResponse } from "../../helpers/makeRequest";

export type GetRequestIdFromTimeRequestBody = {
  from_date: string | null;
  to_date: string | null;
  email: string | null;
  pattern: string | null;
  userId: string | null;
};

export type GetRequestIdFromTimeSuccessResponseBody = {
  requestId: string;
};

export type GetRequestIdFromTimeSuccessResponse =
  ApiResponse<GetRequestIdFromTimeSuccessResponseBody>;

export type GetRequestIdFromTimeErrorResponse = {
  data: null;
  error: { code: string; message: string; cause?: string };
  status: number;
};
