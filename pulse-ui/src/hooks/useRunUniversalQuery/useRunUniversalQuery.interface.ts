import { ApiResponse } from "../../helpers/makeRequest";

export type GetQueryIdRequestBody = { query: string; emailId: string };

export type GetQueryIdSuccessResponseBody = {
  requestId: string;
};

export type GetQueryIdSuccessResponse =
  ApiResponse<GetQueryIdSuccessResponseBody>;

export type GetQueryIdErrorResponse = {
  data: null;
  error: { code: string; message: string; cause?: string };
  status: number;
};
