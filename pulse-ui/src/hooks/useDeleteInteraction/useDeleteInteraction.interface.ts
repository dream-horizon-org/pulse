import { ApiResponse } from "../../helpers/makeRequest";

export type DeleteInteractionRequestBody = {
  useCaseId: string;
  user: string;
  createdBy: string;
};

export type DeleteInteractionResponse = {
  status: number;
};

export type DeleteInteractionOnSettledResponse =
  | ApiResponse<DeleteInteractionResponse>
  | undefined;

export type OnSettled = (data: DeleteInteractionOnSettledResponse) => void;

