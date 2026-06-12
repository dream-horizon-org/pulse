import { CriticalInteractionFormRequestBodyParams } from "../../helpers/createJob";
import { ApiResponse } from "../../helpers/makeRequest";

export type UpdateInteractionRequestBody = {
  jobDetails?: CriticalInteractionFormRequestBodyParams;
  user: string;
};

export type UpdateInteractionResponse = {
  jobId: number;
  status: number;
};

export type UpdateInteractionOnSettledResponse =
  | ApiResponse<UpdateInteractionResponse>
  | undefined;

export type OnSettled = (data: UpdateInteractionOnSettledResponse) => void;

