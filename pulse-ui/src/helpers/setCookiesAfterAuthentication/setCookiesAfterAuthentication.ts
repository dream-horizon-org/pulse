import { jwtDecode } from "jwt-decode";
import {
  AuthenticateSuccessResponse,
  GuardianDecodedToken,
  FirebaseDecodedToken,
} from "../authenticateUser";
import { setCookies } from "../cookies";
import { COOKIES_KEY } from "../../constants";

function parseIdTokenClaims(idToken: string): {
  email: string;
  userName: string;
  profilePicture: string;
  tenantId?: string;
} {
  const decoded = jwtDecode<GuardianDecodedToken & FirebaseDecodedToken>(idToken);
  const email = decoded.email ?? "";
  const profilePicture =
    decoded.profilePicture ?? decoded.picture ?? "";
  const userName =
    decoded.firstName !== undefined && decoded.lastName !== undefined
      ? `${decoded.firstName} ${decoded.lastName}`
      : (decoded.name ??
          ([decoded.given_name, decoded.family_name].filter(Boolean).join(" ") ||
            ""));
  const tenantId = decoded["firebase.tenant"];
  return { email, userName, profilePicture, tenantId };
}

export type SetCookiesAfterAuthOptions = { tenantId?: string; tenantName?: string };

export const setCookiesAfterAuthentication = (
  authenticationSuccessResponse: AuthenticateSuccessResponse,
  options?: SetCookiesAfterAuthOptions,
) => {
  const { accessToken, refreshToken, idToken, tokenType, expiresIn } =
    authenticationSuccessResponse;

  const { email, userName, profilePicture, tenantId: tokenTenantId } =
    parseIdTokenClaims(idToken);
  const tenantId = options?.tenantId ?? tokenTenantId;

  setCookies(COOKIES_KEY.USER_EMAIL, email);
  setCookies(COOKIES_KEY.USER_NAME, userName);
  setCookies(COOKIES_KEY.USER_PICTURE, profilePicture);
  setCookies(COOKIES_KEY.ACCESS_TOKEN, accessToken);
  setCookies(COOKIES_KEY.REFRESH_TOKEN, refreshToken);
  setCookies(COOKIES_KEY.TOKEN_TYPE, tokenType);
  setCookies(COOKIES_KEY.EXPIRES_IN, `${expiresIn}`);
  if (tenantId) {
    setCookies(COOKIES_KEY.TENANT_ID, tenantId);
  }
  if (options?.tenantName) {
    setCookies(COOKIES_KEY.TENANT_NAME, options.tenantName);
  }
};
