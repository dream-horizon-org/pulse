import { CredentialResponse } from "@react-oauth/google";
import { AuthenticateSuccessResponse } from "./authenticateUser.interface";
import { makeRequest } from "../makeRequest";
import { API_ROUTES, API_BASE_URL } from "../../constants";

export const authenticateUser = (
  googleCredential: CredentialResponse["credential"],
) => {
  return makeRequest<AuthenticateSuccessResponse>({
    url: `${API_BASE_URL}${API_ROUTES.AUTHENTICATE.apiPath}`,
    init: {
      method: API_ROUTES.AUTHENTICATE.method,
      body: JSON.stringify({
        responseType: "token",
        grantType: "id_token",
        identifier: googleCredential,
        idProvider: "google",
        resources: [window.location.hostname],
      }),
    },
  });
};
