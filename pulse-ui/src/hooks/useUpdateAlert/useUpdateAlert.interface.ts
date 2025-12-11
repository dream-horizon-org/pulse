import { ApiResponse } from "../../helpers/makeRequest";
import { AlertFormData } from "../../screens/AlertForm/AlertForm.interface";
import { CreateAlertResponse } from "../useCreateAlert/useCreateAlert.interface";

export type UpdateAlertOnSettled = (
  data: ApiResponse<CreateAlertResponse> | undefined,
  error: unknown,
  variables: AlertFormData,
  context: unknown,
) => void;

