import { LoginResponse } from "../login";
import { OnboardingResponse } from "../onboarding";
import { setCookies } from "../cookies";
import { COOKIES_KEY } from "../../constants";

export type SetCookiesAfterAuthOptions = { 
  // DEPRECATED: projectId and projectName now handled by React Context
  // Keep for backward compatibility during migration
  projectId?: string;
  projectName?: string;
};

export const setCookiesAfterAuthentication = (
  loginResponse: LoginResponse | OnboardingResponse,
  options?: SetCookiesAfterAuthOptions,
) => {
  // User info
  setCookies(COOKIES_KEY.USER_ID, loginResponse.userId);
  setCookies(COOKIES_KEY.USER_EMAIL, loginResponse.email);
  setCookies(COOKIES_KEY.USER_NAME, loginResponse.name);
  
  // Tokens
  if (loginResponse.accessToken) {
    setCookies(COOKIES_KEY.ACCESS_TOKEN, loginResponse.accessToken);
  }
  if (loginResponse.refreshToken) {
    setCookies(COOKIES_KEY.REFRESH_TOKEN, loginResponse.refreshToken);
  }
  if (loginResponse.tokenType) {
    setCookies(COOKIES_KEY.TOKEN_TYPE, loginResponse.tokenType);
  }
  if (loginResponse.expiresIn) {
    setCookies(COOKIES_KEY.EXPIRES_IN, `${loginResponse.expiresIn}`);
  }
  
  // Tenant info (for initial hydration only)
  if (loginResponse.tenantId) {
    setCookies(COOKIES_KEY.TENANT_ID, loginResponse.tenantId);
  }
  
  // NOTE: PROJECT_ID and PROJECT_NAME are no longer stored in cookies
  // They are now managed by ProjectContext (React Context API)
};
