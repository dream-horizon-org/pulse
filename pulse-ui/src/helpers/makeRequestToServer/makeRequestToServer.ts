import {
  AI_API_PATHS,
  API_BASE_URL,
  API_METHODS,
  COOKIES_KEY,
} from "../../constants";
import { getCookies } from "../cookies";
import { MakeRequestConfig } from "../makeRequest";
import { MockConfigManager } from "../../mocks/MockConfig";
import type { MockResponse } from "../../mocks/types";
import { resolveIncidentsMock } from "../../mocks/incidentsMockHandler";

// Mock server import - only loaded when needed (direct module avoids barrel/circular issues)
let MockServer: any = null;

// Must match MockConfigManager: dev enables mock unless REACT_APP_USE_MOCK_SERVER=false
const isMockApiEnabled = () => MockConfigManager.getInstance().isEnabled();

/**
 * Inline mock for contact-us so Pricing flow works even if the async mock chunk fails to load.
 * Matches backend + MockResponseGenerator.handleContactUsPost.
 */
function tryInlineMockContactUs(config: MakeRequestConfig): Response | null {
  const method = (config.init?.method || "GET").toUpperCase();
  if (method !== "POST") return null;
  const raw =
    typeof config.url === "string"
      ? config.url
      : config.url instanceof URL
        ? config.url.href
        : String(config.url);
  if (!raw.includes("notifications") || !raw.includes("contact-us")) {
    return null;
  }
  let type: string | null = null;
  try {
    type = new URL(raw, "http://localhost").searchParams.get("type");
  } catch {
    return null;
  }
  if (!type || !["sales", "support"].includes(type.toLowerCase())) {
    return new Response(
      JSON.stringify({
        data: null,
        error: {
          code: "INVALID_TYPE",
          message: "Invalid contact type. Use 'sales' or 'support'",
          cause: "Invalid or missing type query parameter",
        },
      }),
      {
        status: 400,
        headers: { "Content-Type": "application/json" },
      },
    );
  }
  const msg =
    type.toLowerCase() === "sales"
      ? "Contact request submitted successfully"
      : "Support request submitted successfully";
  return new Response(JSON.stringify({ data: msg, error: null }), {
    status: 200,
    headers: { "Content-Type": "application/json" },
  });
}

// Lazy load mock server to avoid bundling when not needed
const getMockServer = async () => {
  if (!MockServer && isMockApiEnabled()) {
    try {
      const mockModule = await import(
        /* webpackChunkName: "pulse-api-dev-stub" */ "../../mocks/MockServer"
      );
      MockServer = mockModule.MockServer;
      if (!MockServer) {
        console.error(
          "[Mock Server] MockServer export missing; contact-us still works via inline mock",
        );
      }
    } catch (e) {
      console.error(
        "[Mock Server] Failed to load mock chunk; contact-us + Support Queries (incidents) use inline mocks. Other APIs may hit the real server.",
        e,
      );
    }
  }
  return MockServer;
};

/**
 * Builds authentication headers for API requests.
 * Used by makeRequestToServer and streamAiRunSse for SSE/streaming calls.
 * Uses the backend-generated access token stored in cookies after successful authentication.
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
  if (isMockApiEnabled()) {
    const contactUs = tryInlineMockContactUs(requestConfig);
    if (contactUs) return contactUs;

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

    // Support Queries: POST/GET /v1/incidents when full mock chunk did not load
    const urlStr =
      typeof requestConfig.url === "string"
        ? requestConfig.url
        : requestConfig.url instanceof URL
          ? requestConfig.url.href
          : String(requestConfig.url);
    const incidentsMock = resolveIncidentsMock(
      requestConfig.init?.method || "GET",
      urlStr,
      requestConfig.init?.body != null
        ? String(requestConfig.init.body)
        : undefined,
    );
    if (incidentsMock) {
      const mr: MockResponse = incidentsMock;
      return new Response(
        JSON.stringify({
          data: mr.data,
          error: mr.error ?? null,
        }),
        {
          status: mr.status,
          headers: { "Content-Type": "application/json" },
        },
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
