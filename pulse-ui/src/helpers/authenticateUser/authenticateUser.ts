import { CredentialResponse } from "@react-oauth/google";
import { jwtDecode } from "jwt-decode";
import { AuthenticateSuccessResponse } from "./authenticateUser.interface";
import { makeRequest } from "../makeRequest";
import { API_ROUTES, API_BASE_URL } from "../../constants";

export type AuthenticateUserOptions = {
  tenantId?: string;
  userEmail?: string;
};

export const authenticateUser = (
  googleCredential: CredentialResponse["credential"],
  options?: AuthenticateUserOptions,
) => {
  const body = {
    responseType: "token",
    grantType: "id_token",
    identifier: googleCredential,
    idProvider: "google",
    resources: [window.location.hostname],
  };

  const headers: Record<string, string> = {};
  if (typeof googleCredential === "string") {
    headers.Authorization = `Bearer ${googleCredential}`;
    let email = options?.userEmail;
    if (email === undefined) {
      try {
        const decoded = jwtDecode<{ email?: string }>(googleCredential);
        email = decoded?.email ?? "";
      } catch {

      }
    }

    if (email) {
      headers["user-email"] = email;
    }
    if (options?.tenantId) {
      headers["tenant-id"] = options.tenantId;
    }
  }

  return makeRequest<AuthenticateSuccessResponse>({
    url: `${API_BASE_URL}${API_ROUTES.AUTHENTICATE.apiPath}`,
    init: {
      method: API_ROUTES.AUTHENTICATE.method,
      body: JSON.stringify(body),
      ...(Object.keys(headers).length > 0 && { headers }),
    },
  });
};
