/**
 * Session Replay Mock Responses
 *
 * Mock data and response generators for Session Replay API endpoints
 */
import type {
  GetSessionsResponse,
  GetSessionDetailResponse,
  GetFilterSchemaResponse,
  GetDateRangeConfigResponse,
  GetQuickFiltersResponse,
  SessionDetailApiResponse,
} from "../../services/sessionReplay/types";
import { applyMockSessionDetailOverrides } from "../../screens/SessionReplayDetail/mock/mockSessionReplayScenarios";
import { SESSION_REPLAY_DETAIL_INTERACTION_ORDER } from "../mockPulseProjectRegistry";

const MOCK_API_ORIGIN = "https://api.example.com";

// Re-export mock data generators from sessionReplay service
// (Keep the existing mock data classes, just reference them here)
export {
  MockSessionReplayData,
  MockConfigurationData,
  MOCK_SESSIONS_DATA,
} from "../../services/sessionReplay/mockData";
/**
 * Generate mock response for GET /api/v1/session-replay/sessions
 */
export function generateSessionsResponse(
  queryParams: Record<string, any> = {},
): GetSessionsResponse {
  const {
    MockSessionReplayData,
    MOCK_SESSIONS_DATA,
  } = require("../../services/sessionReplay/mockData");
  let filteredSessions = [...MOCK_SESSIONS_DATA];
  // Apply filters
  filteredSessions = MockSessionReplayData.filterSessions(filteredSessions, {
    environment: queryParams.environment,
    project: queryParams.project,
    hasErrors: queryParams.filters?.hasErrors,
    rageClicks: queryParams.filters?.rageClicks,
    slowSessions: queryParams.filters?.slowSessions,
    mobile: queryParams.filters?.mobile,
    newUsers: queryParams.filters?.newUsers,
    searchQuery: queryParams.searchQuery,
  });
  // Sort sessions (default: most recent first)
  filteredSessions.sort((a, b) => {
    const dateA = new Date(a.startTime).getTime();
    const dateB = new Date(b.startTime).getTime();
    return dateB - dateA;
  });
  // Paginate
  const page = queryParams.page || 1;
  const pageSize = queryParams.pageSize || 10;
  const paginated = MockSessionReplayData.paginateSessions(
    filteredSessions,
    page,
    pageSize,
  );
  // Calculate metrics on filtered data
  const metrics = MockSessionReplayData.calculateMetrics(filteredSessions);
  return {
    sessions: paginated.sessions,
    pagination: {
      page,
      pageSize,
      total: paginated.total,
      totalPages: paginated.totalPages,
    },
    metrics,
  };
}
/**
 * Generate mock response for GET /api/v1/session-replay/sessions/:id (legacy shape)
 */
export function generateSessionDetailResponse(
  sessionId: string,
): GetSessionDetailResponse {
  const {
    MockSessionReplayData,
  } = require("../../services/sessionReplay/mockData");
  return MockSessionReplayData.generateSessionDetail(sessionId);
}

function buildGenericSessionDetailApiResponse(
  sessionId: string,
): SessionDetailApiResponse {
  const now = new Date();
  const durationMs = 92000;
  const startTime = new Date(now.getTime() - durationMs);
  const endTime = new Date(now.getTime());
  const t = (ms: number) => new Date(startTime.getTime() + ms).toISOString();
  const events = [
    {
      traceId: `trace_${sessionId}_0`,
      spanId: `span_${sessionId}_0`,
      timestamp: t(0),
      eventType: "navigation" as const,
      description: "Navigate to /dream11-home",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_0a`,
      spanId: `span_${sessionId}_0a`,
      timestamp: t(650),
      eventType: "navigation" as const,
      description: "Navigate to MainActivity resumed",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_boot`,
      spanId: `span_${sessionId}_boot`,
      timestamp: t(900),
      eventType: "api_call" as const,
      description: `API GET ${MOCK_API_ORIGIN}/api/v1/bootstrap`,
      durationNs: 42000000,
    },
    {
      traceId: `trace_${sessionId}_me`,
      spanId: `span_${sessionId}_me`,
      timestamp: t(2200),
      eventType: "api_call" as const,
      description: `API GET ${MOCK_API_ORIGIN}/api/v1/user/me`,
      durationNs: 78000000,
    },
    {
      traceId: `trace_${sessionId}_1`,
      spanId: `span_${sessionId}_1`,
      timestamp: t(3800),
      eventType: "click" as const,
      description: "Tap on search field",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_2`,
      spanId: `span_${sessionId}_2`,
      timestamp: t(5330),
      eventType: "click" as const,
      description: "Tap on Search button",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_3`,
      spanId: `span_${sessionId}_3`,
      timestamp: t(8200),
      eventType: "navigation" as const,
      description: "Navigate to /contest-list",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_sort`,
      spanId: `span_${sessionId}_sort`,
      timestamp: t(10200),
      eventType: "click" as const,
      description: "Tap sort — contest entry fee",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_4`,
      spanId: `span_${sessionId}_4`,
      timestamp: t(15300),
      eventType: "api_call" as const,
      description: `API GET ${MOCK_API_ORIGIN}/api/v1/contests/recommended`,
      durationNs: 245000000,
    },
    {
      traceId: `trace_${sessionId}_flt`,
      spanId: `span_${sessionId}_flt`,
      timestamp: t(17800),
      eventType: "click" as const,
      description: "Tap filter chip — contest type",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_5`,
      spanId: `span_${sessionId}_5`,
      timestamp: t(20500),
      eventType: "click" as const,
      description: "Tap IPL mega contest card",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_7`,
      spanId: `span_${sessionId}_7`,
      timestamp: t(22100),
      eventType: "api_call" as const,
      description: `API GET ${MOCK_API_ORIGIN}/api/v1/contests/ipl-mega-2026/detail`,
      durationNs: 89000000,
    },
    {
      traceId: `trace_${sessionId}_6`,
      spanId: `span_${sessionId}_6`,
      timestamp: t(24500),
      eventType: "navigation" as const,
      description: "Navigate to /contest-detail",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_6b`,
      spanId: `span_${sessionId}_6b`,
      timestamp: t(26800),
      eventType: "click" as const,
      description: "Scroll contest detail — prize pool",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_int`,
      spanId: `span_${sessionId}_int`,
      timestamp: t(29500),
      eventType: "interaction" as const,
      description: "Critical interaction JoinContestButtonClick acknowledged",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_atc`,
      spanId: `span_${sessionId}_atc`,
      timestamp: t(31200),
      eventType: "click" as const,
      description: "Tap Join contest (JoinContestButtonClick)",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_8`,
      spanId: `span_${sessionId}_8`,
      timestamp: t(32800),
      eventType: "api_call" as const,
      description: `API POST ${MOCK_API_ORIGIN}/api/v1/contests/join`,
      durationNs: 312000000,
    },
    {
      traceId: `trace_${sessionId}_9`,
      spanId: `span_${sessionId}_9`,
      timestamp: t(36500),
      eventType: "navigation" as const,
      description: "Navigate to /team-selection",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_8b`,
      spanId: `span_${sessionId}_8b`,
      timestamp: t(38100),
      eventType: "api_call" as const,
      description: `API GET ${MOCK_API_ORIGIN}/api/v1/contests/recommended`,
      durationNs: 125000000,
    },
    {
      traceId: `trace_${sessionId}_cart`,
      spanId: `span_${sessionId}_cart`,
      timestamp: t(41800),
      eventType: "api_call" as const,
      description: `API GET ${MOCK_API_ORIGIN}/api/v1/wallet/balance`,
      durationNs: 156000000,
    },
    {
      traceId: `trace_${sessionId}_chk`,
      spanId: `span_${sessionId}_chk`,
      timestamp: t(43200),
      eventType: "click" as const,
      description: "Tap Pay entry fee",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_10`,
      spanId: `span_${sessionId}_10`,
      timestamp: t(45200),
      eventType: "api_call" as const,
      description: `API POST ${MOCK_API_ORIGIN}/api/v1/payments/contest-entry`,
      durationNs: 30100000000,
    },
    {
      traceId: `trace_${sessionId}_err`,
      spanId: `span_${sessionId}_err`,
      timestamp: t(45800),
      eventType: "error" as const,
      description: "Payment gateway timeout",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_11`,
      spanId: `span_${sessionId}_11`,
      timestamp: t(52000),
      eventType: "click" as const,
      description: "Tap Retry payment",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_12`,
      spanId: `span_${sessionId}_12`,
      timestamp: t(67200),
      eventType: "api_call" as const,
      description: `API GET ${MOCK_API_ORIGIN}/api/user/profile`,
      durationNs: 198000000,
    },
    {
      traceId: `trace_${sessionId}_13`,
      spanId: `span_${sessionId}_13`,
      timestamp: t(71500),
      eventType: "navigation" as const,
      description: "Navigate to /wallet",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_14`,
      spanId: `span_${sessionId}_14`,
      timestamp: t(78000),
      eventType: "click" as const,
      description: "Tap dismiss error banner",
      durationNs: 0,
    },
  ];
  const paymentGatewayStack = [
    "com.dream11.payment.PaymentGatewayTimeout: 504 Gateway Timeout",
    "Caused by: retrofit2.HttpException: HTTP 504 ",
    "\tat retrofit2.KotlinExtensions$await$2$2.onResponse(KotlinExtensions.kt:53)",
    "\tat retrofit2.OkHttpCall$1.onResponse(OkHttpCall.java:161)",
    "\tat okhttp3.internal.connection.RealCall$AsyncCall.run(RealCall.kt:519)",
    "\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1156)",
    "\tat java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:651)",
    "\tat java.lang.Thread.run(Thread.java:1119)",
    "Caused by: okhttp3.internal.http2.StreamResetException: stream was reset: NO_ERROR",
    "\tat okhttp3.internal.http2.Http2Stream.takeHeaders(Http2Stream.kt:148)",
    "\tat okhttp3.internal.http2.Http2ExchangeCodec.readResponseHeaders(Http2ExchangeCodec.kt:97)",
    "\tat okhttp3.internal.connection.Exchange.readResponseHeaders(Exchange.kt:110)",
    "\tat okhttp3.internal.http.CallServerInterceptor.intercept(CallServerInterceptor.kt:93)",
    "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)",
    "\tat com.dream11.network.LoggingInterceptor.intercept(LoggingInterceptor.kt:42)",
    "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)",
    "\tat com.dream11.network.AuthHeaderInterceptor.intercept(AuthHeaderInterceptor.kt:28)",
    "--- Request ---",
    "POST https://api.example.com/api/v1/payments/contest-entry HTTP/2",
    "Content-Type: application/json",
    "X-Request-Id: req_pay_8f2a9c1d",
    "User-Agent: Dream11/2.3.1 (Android 14; Pixel 6)",
    "",
    "--- Response ---",
    "HTTP/2 504",
    "server: envoy",
    "x-envoy-upstream-service-time: 30001",
    "date: Wed, 18 Mar 2026 07:12:46 GMT",
    "",
    "Message: Upstream payment service did not respond within 30s (Razorpay adapter).",
  ].join("\n");
  const contestEntryValidationStack = [
    "java.lang.IllegalStateException: Contest entry fee not locked for selected XI",
    "\tat com.dream11.contest.ContestEntryRepository.validateEntry(ContestEntryRepository.kt:198)",
    "\tat com.dream11.contest.ContestEntryRepository.reserveEntry$lambda$3(ContestEntryRepository.kt:91)",
    "\tat kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:108)",
    "\tat android.os.Handler.handleCallback(Handler.java:959)",
    "\tat android.os.Handler.dispatchMessage(Handler.java:100)",
    "\tat android.os.Looper.loopOnce(Looper.java:232)",
    "\tat android.os.Looper.loop(Looper.java:317)",
    "\tat android.app.ActivityThread.main(ActivityThread.java:8705)",
    "\tat java.lang.reflect.Method.invoke(Native Method)",
    "\tat com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:580)",
    "\tat com.android.internal.os.ZygoteInit.main(ZygoteInit.java:886)",
    "Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [StandaloneCoroutine{Cancelling}@a1b2c3d, Dispatchers.Main.immediate]",
  ].join("\n");
  const exceptions = [
    {
      traceId: `trace_${sessionId}_10`,
      spanId: `span_${sessionId}_10`,
      pulseType: "error",
      timestamp: 45200,
      title: "PaymentGatewayTimeout",
      exceptionStackTrace: paymentGatewayStack,
    },
    {
      traceId: `trace_${sessionId}_7`,
      spanId: `span_${sessionId}_7`,
      pulseType: "non_fatal",
      timestamp: 22100,
      title: "ContestEntryValidationError",
      exceptionStackTrace: contestEntryValidationStack,
    },
    {
      traceId: `trace_${sessionId}_4`,
      spanId: `span_${sessionId}_4`,
      pulseType: "non_fatal",
      timestamp: 15300,
      title: "NetworkSlowWarning",
      exceptionStackTrace: [
        "com.dream11.network.SlowResponseWarning: GET /api/v1/contests/recommended exceeded p95 (245ms > 200ms)",
        "\tat com.dream11.network.TelemetryInterceptor.intercept(TelemetryInterceptor.kt:71)",
        "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)",
        "\tat okhttp3.internal.connection.ConnectInterceptor.intercept(ConnectInterceptor.kt:34)",
        "\tat okhttp3.internal.http.RealInterceptorChain.proceed(RealInterceptorChain.kt:109)",
        "Thread: OkHttp https://api.example.com/...",
        "DNS: 12ms, TCP connect: 28ms, TLS: 41ms, TTFB: 164ms",
      ].join("\n"),
    },
  ];
  return {
    sessionId,
    userId: "user_3456",
    isAnonymous: false,
    startTime: startTime.toISOString(),
    endTime: endTime.toISOString(),
    duration: 92000,
    platform: "Android",
    device: "Pixel 6",
    osVersion: "14",
    appVersion: "2.3.1",
    geography: "India, Mumbai",
    quality: 0.65,
    journey: [
      "HomeScreen",
      "ProductDetailScreen",
      "CartScreen",
    ],
    interactions: [
      {
        interactionName: SESSION_REPLAY_DETAIL_INTERACTION_ORDER[0],
        status: "success",
        successCount: 1,
        failureCount: 0,
        durationMs: 420,
        apdexScore: 0.85,
      },
      {
        interactionName: SESSION_REPLAY_DETAIL_INTERACTION_ORDER[1],
        status: "success",
        successCount: 1,
        failureCount: 0,
        durationMs: 320,
        apdexScore: 0.82,
      },
      {
        interactionName: SESSION_REPLAY_DETAIL_INTERACTION_ORDER[2],
        status: "failed",
        successCount: 0,
        failureCount: 1,
        durationMs: 30100,
        apdexScore: 0,
      },
    ],
    networkRequests: [
      {
        timestamp: 900,
        durationNs: 42000000,
        method: "GET",
        url: `${MOCK_API_ORIGIN}/api/v1/bootstrap`,
        status: "200",
        target: "/api/v1/bootstrap",
        traceId: `trace_${sessionId}_boot`,
        spanId: `span_${sessionId}_boot`,
      },
      {
        timestamp: 2200,
        durationNs: 78000000,
        method: "GET",
        url: `${MOCK_API_ORIGIN}/api/v1/user/me`,
        status: "200",
        target: "/api/v1/user/me",
        traceId: `trace_${sessionId}_me`,
        spanId: `span_${sessionId}_me`,
      },
      {
        timestamp: 15300,
        durationNs: 245000000,
        method: "GET",
        url: `${MOCK_API_ORIGIN}/api/v1/contests/recommended`,
        status: "200",
        target: "/api/v1/contests/recommended",
        traceId: `trace_${sessionId}_4`,
        spanId: `span_${sessionId}_4`,
      },
      {
        timestamp: 22100,
        durationNs: 89000000,
        method: "GET",
        url: `${MOCK_API_ORIGIN}/api/v1/contests/ipl-mega-2026/detail`,
        status: "200",
        target: "/api/v1/contests/ipl-mega-2026/detail",
        traceId: `trace_${sessionId}_7`,
        spanId: `span_${sessionId}_7`,
      },
      {
        timestamp: 32800,
        durationNs: 312000000,
        method: "POST",
        url: `${MOCK_API_ORIGIN}/api/v1/contests/join`,
        status: "200",
        target: "/api/v1/contests/join",
        traceId: `trace_${sessionId}_8`,
        spanId: `span_${sessionId}_8`,
      },
      {
        timestamp: 38100,
        durationNs: 125000000,
        method: "GET",
        url: `${MOCK_API_ORIGIN}/api/v1/contests/recommended`,
        status: "200",
        target: "/api/v1/contests/recommended",
        traceId: `trace_${sessionId}_8b`,
        spanId: `span_${sessionId}_8b`,
      },
      {
        timestamp: 41800,
        durationNs: 156000000,
        method: "GET",
        url: `${MOCK_API_ORIGIN}/api/v1/wallet/balance`,
        status: "200",
        target: "/api/v1/wallet/balance",
        traceId: `trace_${sessionId}_cart`,
        spanId: `span_${sessionId}_cart`,
      },
      {
        timestamp: 45200,
        durationNs: 30100000000,
        method: "POST",
        url: `${MOCK_API_ORIGIN}/api/v1/payments/contest-entry`,
        status: "504",
        target: "/api/v1/payments/contest-entry",
        traceId: `trace_${sessionId}_10`,
        spanId: `span_${sessionId}_10`,
      },
      {
        timestamp: 67200,
        durationNs: 198000000,
        method: "GET",
        url: `${MOCK_API_ORIGIN}/api/user/profile`,
        status: "200",
        target: "/api/user/profile",
        traceId: `trace_${sessionId}_12`,
        spanId: `span_${sessionId}_12`,
      },
    ],
    events,
    exceptions,
  };
}

/** Session detail for mocks: generic journey + optional `sess_mock_*` overrides from evidence scenarios. */
export function generateSessionDetailApiResponse(
  sessionId: string,
): SessionDetailApiResponse {
  return applyMockSessionDetailOverrides(
    sessionId,
    buildGenericSessionDetailApiResponse(sessionId),
  );
}

/**
 * Generate mock response for GET /api/v1/session-replay/filters/schema
 */
export function generateFilterSchemaResponse(
  queryParams: Record<string, any> = {},
): GetFilterSchemaResponse {
  const {
    MockConfigurationData,
  } = require("../../services/sessionReplay/mockData");
  // For now, default to iOS. In real app, would use projectId to determine platform
  const platform = "ios"; // Could be derived from queryParams.projectId
  return MockConfigurationData.getFilterSchema(
    platform as "web" | "ios" | "android",
  );
}
/**
 * Generate mock response for GET /api/v1/session-replay/config/date-ranges
 */
export function generateDateRangeConfigResponse(): GetDateRangeConfigResponse {
  const {
    MockConfigurationData,
  } = require("../../services/sessionReplay/mockData");
  return MockConfigurationData.getDateRangeConfig();
}
/**
 * Generate mock response for GET /api/v1/session-replay/config/quick-filters
 */
export function generateQuickFiltersResponse(): GetQuickFiltersResponse {
  const {
    MockConfigurationData,
  } = require("../../services/sessionReplay/mockData");
  return MockConfigurationData.getQuickFilters();
}
/**
 * Generate mock response for POST /api/v1/session-replay/sessions/bulk-tag
 */
export function generateBulkTagResponse(): { success: boolean } {
  return { success: true };
}
/**
 * Generate mock response for DELETE /api/v1/session-replay/sessions/bulk-delete
 */
export function generateBulkDeleteResponse(): { success: boolean } {
  return { success: true };
}
/**
 * Generate mock response for POST /api/v1/session-replay/sessions/export
 */
export function generateExportResponse(): {
  downloadUrl: string;
  expiresAt: string;
} {
  return {
    downloadUrl: "https://example.com/downloads/sessions.csv",
    expiresAt: new Date(Date.now() + 3600000).toISOString(),
  };
}
