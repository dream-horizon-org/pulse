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
export function generateSessionDetailApiResponse(
  sessionId: string,
): SessionDetailApiResponse {
  const now = new Date();
  const durationMs = 154000;
  const startTime = new Date(now.getTime() - durationMs);
  const endTime = new Date(now.getTime());
  const events = [
    {
      traceId: `trace_${sessionId}_1`,
      spanId: `span_${sessionId}_1`,
      timestamp: startTime.toISOString(),
      eventType: "navigation" as const,
      description: "Screen /dream11-home",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_2`,
      spanId: `span_${sessionId}_2`,
      timestamp: new Date(startTime.getTime() + 5330).toISOString(),
      eventType: "click" as const,
      description: "Tap on Search button",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_3`,
      spanId: `span_${sessionId}_3`,
      timestamp: new Date(startTime.getTime() + 8200).toISOString(),
      eventType: "navigation" as const,
      description: "Screen /product-list",
      durationNs: 0,
    },
    {
      traceId: `trace_${sessionId}_4`,
      spanId: `span_${sessionId}_4`,
      timestamp: new Date(startTime.getTime() + 15300).toISOString(),
      eventType: "api_call" as const,
      description: "API Call - GET /api/products/recs",
      durationNs: 245000000,
    },
    {
      traceId: `trace_${sessionId}_5`,
      spanId: `span_${sessionId}_5`,
      timestamp: new Date(startTime.getTime() + 22100).toISOString(),
      eventType: "click" as const,
      description: "Tap on Product card",
      durationNs: 0,
    },
    // {
    //   traceId: `trace_${sessionId}_6`,
    //   spanId: `span_${sessionId}_6`,
    //   timestamp: 24500,
    //   type: "navigation" as const,
    //   description: "Screen Load - /product-detail",
    // },
    // {
    //   traceId: `trace_${sessionId}_7`,
    //   spanId: `span_${sessionId}_7`,
    //   timestamp: 31200,
    //   type: "click" as const,
    //   description: "Tap - Add to cart",
    // },
    // {
    //   traceId: `trace_${sessionId}_8`,
    //   spanId: `span_${sessionId}_8`,
    //   timestamp: 38100,
    //   type: "api_call" as const,
    //   description: "API Call - POST /api/cart/add",
    // },
    // {
    //   traceId: `trace_${sessionId}_9`,
    //   spanId: `span_${sessionId}_9`,
    //   timestamp: 41800,
    //   type: "navigation" as const,
    //   description: "Screen Load - /cart",
    // },
    // {
    //   traceId: `trace_${sessionId}_10`,
    //   spanId: `span_${sessionId}_10`,
    //   timestamp: 45200,
    //   type: "error" as const,
    //   description: "Payment gateway timeout",
    // },
    // {
    //   traceId: `trace_${sessionId}_11`,
    //   spanId: `span_${sessionId}_11`,
    //   timestamp: 58900,
    //   type: "click" as const,
    //   description: "Tap - Retry payment",
    // },
    // {
    //   traceId: `trace_${sessionId}_12`,
    //   spanId: `span_${sessionId}_12`,
    //   timestamp: 67200,
    //   type: "api_call" as const,
    //   description: "API Call - GET /api/user/profile",
    // },
    // {
    //   traceId: `trace_${sessionId}_13`,
    //   spanId: `span_${sessionId}_13`,
    //   timestamp: 89100,
    //   type: "navigation" as const,
    //   description: "Screen Load - /checkout-success",
    // },
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
    "POST https://api.example.com/api/payment HTTP/2",
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
  const cartValidationStack = [
    "java.lang.IllegalStateException: Cart line item missing price snapshot",
    "\tat com.dream11.cart.CartRepository.validateLineItems(CartRepository.kt:214)",
    "\tat com.dream11.cart.CartRepository.syncCart$lambda$3(CartRepository.kt:98)",
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
      title: "CartValidationError",
      exceptionStackTrace: cartValidationStack,
    },
    {
      traceId: `trace_${sessionId}_4`,
      spanId: `span_${sessionId}_4`,
      pulseType: "non_fatal",
      timestamp: 15300,
      title: "NetworkSlowWarning",
      exceptionStackTrace: [
        "com.dream11.network.SlowResponseWarning: GET /api/products/recs exceeded p95 (245ms > 200ms)",
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
    duration: durationMs,
    platform: "Android",
    device: "Pixel 6",
    osVersion: "14",
    appVersion: "2.3.1",
    geography: "United States, San Francisco",
    quality: 6.5,
    journey: ["com.fc.home", "com.fc.home.Product", "com.fc.home.Cart"],
    interactions: [
      {
        interactionName: "FCInteractionTesting",
        status: "success",
        successCount: 1,
        failureCount: 0,
        durationMs: 76,
        apdexScore: 0.29,
      },
      {
        interactionName: "SubmitPayment",
        status: "failed",
        successCount: 0,
        failureCount: 1,
        durationMs: 30100,
        apdexScore: 0,
      },
      {
        interactionName: "AddToCart",
        status: "success",
        successCount: 1,
        failureCount: 0,
        durationMs: 420,
        apdexScore: 0.85,
      },
    ],
    networkRequests: [
      {
        timestamp: 15300,
        durationNs: 245000000,
        method: "GET",
        url: "https://api.example.com/api/products/recs",
        status: "200",
        target: "",
        traceId: `trace_${sessionId}_4`,
        spanId: `span_${sessionId}_4`,
      },
      {
        timestamp: 22100,
        durationNs: 89000000,
        method: "GET",
        url: "https://api.example.com/api/products/123",
        status: "200",
        target: "",
        traceId: "",
        spanId: "",
      },
      {
        timestamp: 38100,
        durationNs: 125000000,
        method: "GET",
        url: "https://api.example.com/api/products/recs",
        status: "200",
        target: "",
        traceId: "",
        spanId: "",
      },
      {
        timestamp: 38100,
        durationNs: 312000000,
        method: "POST",
        url: "https://api.example.com/api/cart/add",
        status: "200",
        target: "",
        traceId: "",
        spanId: "",
      },
      {
        timestamp: 41800,
        durationNs: 156000000,
        method: "GET",
        url: "https://api.example.com/api/cart",
        status: "200",
        target: "",
        traceId: "",
        spanId: "",
      },
      {
        timestamp: 45200,
        durationNs: 30100000000,
        method: "POST",
        url: "https://api.example.com/api/payment",
        status: "504",
        target: "",
        traceId: `trace_${sessionId}_10`,
        spanId: `span_${sessionId}_10`,
      },
      {
        timestamp: 67200,
        durationNs: 198000000,
        method: "GET",
        url: "https://api.example.com/api/user/profile",
        status: "200",
        target: "",
        traceId: "",
        spanId: "",
      },
    ],
    events,
    exceptions,
  };
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
