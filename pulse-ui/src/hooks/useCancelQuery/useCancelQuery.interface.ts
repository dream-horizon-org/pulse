import { ApiResponse } from "../../helpers/makeRequest";

export type CancelQueryRequestType = {
  jobId: string;
};

export type CancelQueryResponseType = {
  success: boolean;
  message?: string;
};

export type CancelQueryOnSettledResponse =
  | ApiResponse<CancelQueryResponseType>
  | undefined;

export type OnSettled = (response: CancelQueryOnSettledResponse) => void;
