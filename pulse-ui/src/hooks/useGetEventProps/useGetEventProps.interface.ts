import { ApiResponse } from "../../helpers/makeRequest";

export type GetEventPropsRequestBody = {
  eventName: string;
  eventTimestamp: string;
  userId: string;
};

export type GetEventPropsSuccessResponseBody = {
  eventName: string;
  eventTimestamp: string;
  props: Record<string, any>;
};

export type GetEventPropsSuccessResponse =
  ApiResponse<GetEventPropsSuccessResponseBody>;

export type GetEventPropsErrorResponse = {
  data: null;
  error: { code: string; message: string; cause?: string };
  status: number;
};
