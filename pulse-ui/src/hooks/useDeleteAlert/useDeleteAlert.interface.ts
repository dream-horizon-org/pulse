import { ApiResponse } from "../../helpers/makeRequest";

export type DeleteAlertResponse = Boolean;

export type DeleteAlertOnSettledResponse =
  | ApiResponse<DeleteAlertResponse>
  | undefined;

export type OnSettled = (response: DeleteAlertOnSettledResponse) => void;
