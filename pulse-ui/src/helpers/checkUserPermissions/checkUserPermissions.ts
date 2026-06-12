import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../makeRequest";
import {
  CheckUserPermissionsRequestBody,
  CheckUserPermissionsResponse,
} from "./checkUserPermissions.interface";

export const checkUserPermissions = async (
  requestBody: CheckUserPermissionsRequestBody,
) => {
  return makeRequest<CheckUserPermissionsResponse>({
    url: `${API_BASE_URL}${API_ROUTES.CHECK_PERMISSIONS.apiPath}`,
    init: {
      method: API_ROUTES.CHECK_PERMISSIONS.method,
      body: JSON.stringify(requestBody),
    },
  });
};
