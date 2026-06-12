import { LoginResponse } from "../login";
import { OnboardingResponse } from "../onboarding";
import { removeCookie, setCookies } from "../cookies";
import { COOKIES_KEY, LOGIN_RESPONSE_KEYS } from "../../constants";
import { syncPulseUserIdentity } from "../../pulse-web-rum/pulseRum";

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
  // USER_ID: Used for frontend UI logic only (not for authentication)
  // - Shows "(You)" label next to current user in member lists
  // - Prevents users from removing themselves or changing their own role
  // - Backend gets userId from JWT token in Authorization header
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
  if (loginResponse.tenantName) {
    setCookies(COOKIES_KEY.TENANT_NAME, loginResponse.tenantName);
  }
  if (loginResponse.tenantRole) {
    setCookies(COOKIES_KEY.TENANT_ROLE, loginResponse.tenantRole);
  }
  if (loginResponse.tier) {
    setCookies(COOKIES_KEY.TIER, loginResponse.tier);
  }

  if (
    LOGIN_RESPONSE_KEYS.SYSTEM_ROLE in loginResponse &&
    loginResponse.systemRole
  ) {
    setCookies(COOKIES_KEY.SYSTEM_ROLE, loginResponse.systemRole);
  } else {
    removeCookie(COOKIES_KEY.SYSTEM_ROLE);
  }

  syncPulseUserIdentity({
    userId: loginResponse.userId,
    email: loginResponse.email,
    name: loginResponse.name,
    tenantId: loginResponse.tenantId,
    tenantRole: loginResponse.tenantRole,
    systemRole:
      LOGIN_RESPONSE_KEYS.SYSTEM_ROLE in loginResponse
        ? loginResponse.systemRole
        : undefined,
  });
};
