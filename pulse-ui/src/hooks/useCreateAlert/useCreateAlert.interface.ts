import { ApiResponse } from "../../helpers/makeRequest";
import { AlertFormData } from "../../screens/AlertForm/AlertForm.interface";

export type CreateAlertResponse = {
  alert_id: number;
};

export type CreateAlertOnSettledResponse =
  | ApiResponse<CreateAlertResponse>
  | undefined;

export type OnSettled = (
  data: ApiResponse<CreateAlertResponse> | undefined,
  error: unknown,
  variables: AlertFormData,
  context: unknown,
) => void;

