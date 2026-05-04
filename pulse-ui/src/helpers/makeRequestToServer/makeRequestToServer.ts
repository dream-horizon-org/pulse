import {
  AI_API_PATHS,
  API_BASE_URL,
  API_METHODS,
  COOKIES_KEY,
} from "../../constants";
import { getCookies } from "../cookies";
import { MakeRequestConfig } from "../makeRequest";

// Mock server import - only loaded when needed
let MockServer: any = null;

// Lazy load mock server to avoid bundling when not needed
const getMockServer = async () => {
  if (!MockServer && process.env.REACT_APP_USE_MOCK_SERVER === "true") {
    const mockModule = await import("../../mocks");
    MockServer = mockModule.MockServer;
  }
  return MockServer;
};

/**
 * Builds authentication headers for API requests.
 * Used by makeRequestToServer and streamAiRunSse for SSE/streaming calls.
 * Uses the backend-generated access token stored in cookies after successful authentication.
 *
 * OpenFGA-protected routes (including all `/v1/ai/*` calls through pulse-server) require both
 * `Authorization` and `X-Project-ID`; the latter comes from `sessionStorage` project context.
 */
function buildAuthHeaders(): Record<string, string> {
  const headers: Record<string, string> = {};

  // Only add Authorization header if access token exists (user is logged in)
  const accessToken = getCookies(COOKIES_KEY.ACCESS_TOKEN);
  if (accessToken && accessToken !== "undefined") {
    const tokenType = getCookies(COOKIES_KEY.TOKEN_TYPE) || "Bearer";
    headers["Authorization"] = `${tokenType} ${accessToken}`;
  }

  // Add user email if available
  const userEmail = getCookies(COOKIES_KEY.USER_EMAIL);
  if (userEmail && userEmail !== "undefined") {
    headers["user-email"] = userEmail;
  }

  // Add project-id header for project-scoped requests
  // Priority: 1) React Context (sessionStorage) - single source of truth
  // NOTE: Never extract projectId from URL to avoid parsing issues
  let projectId: string | undefined;

  // Try sessionStorage (ProjectContext - single source of truth)
  try {
    const stored = sessionStorage.getItem("pulse_project_context");
    if (stored) {
      const data = JSON.parse(stored);
      if (data.projectId && data.projectId !== "undefined") {
        projectId = data.projectId;
      }
    }
  } catch (error) {
    // Silently ignore parsing errors
  }

  // Only set header if we have a valid projectId from context
  if (projectId) {
    headers["X-Project-ID"] = projectId;
  }

  const tenantId = getCookies(COOKIES_KEY.TENANT_ID);
  if (tenantId && tenantId !== "undefined") {
    headers["X-Tenant-ID"] = tenantId;
  }

  return headers;
}

/**
 * POST to the fixed AI run_sse endpoint with the same auth headers as makeRequestToServer.
 * The request URL is not caller-controlled (only {@link API_BASE_URL} + {@link AI_API_PATHS.RUN_SSE}),
 * which avoids SSRF findings on generic `fetch(userUrl)` patterns.
 *
 * @returns Raw {@link Response} for {@link Response.body} streaming (e.g. SSE).
 */
export async function streamAiRunSse(init?: RequestInit): Promise<Response> {
  const base = API_BASE_URL.replace(/\/$/, "");
  const url = `${base}${AI_API_PATHS.RUN_SSE}`;
  const authHeaders = buildAuthHeaders();
  return fetch(url, {
    ...init,
    headers: { ...init?.headers, ...authHeaders },
  });
}

export const makeRequestToServer = async (
  requestConfig: MakeRequestConfig,
): Promise<Response> => {
  // Check if mock server is enabled
  if (process.env.REACT_APP_USE_MOCK_SERVER === "true") {
    try {
      const MockServerClass = await getMockServer();
      if (MockServerClass) {
        const mockServer = MockServerClass.getInstance();
        if (mockServer.isEnabled()) {
          return await mockServer.handleRequest(requestConfig);
        }
      }
    } catch (error) {
      console.warn(
        "[Mock Server] Failed to load mock server, falling back to real API:",
        error,
      );
    }
  }

  // Original implementation - real API call
  const { url, init } = requestConfig;
  const { headers, body, method, ...rest } = init ?? {};
  const authHeaders = buildAuthHeaders();
  const isFormData = body instanceof FormData;

  // `signal`, `credentials`, `cache`, etc. are left in `rest` and forwarded to fetch.
  return await fetch(url, {
    method: method ?? API_METHODS.GET,
    headers: {
      Accept: "application/json",
      ...(!isFormData && { "Content-Type": "application/json" }),
      ...authHeaders,
      ...(headers && { ...headers }),
    },
    ...(body && { body }),
    ...rest,
  });
};
